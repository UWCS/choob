import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobError;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.ObjectDBError;
import uk.co.uwcs.choob.support.ObjectDBTransaction;
import uk.co.uwcs.choob.support.events.ChannelAction;
import uk.co.uwcs.choob.support.events.ChannelEvent;
import uk.co.uwcs.choob.support.events.ChannelJoin;
import uk.co.uwcs.choob.support.events.ChannelMessage;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.NickChange;

class QuoteObject
{
	public int id;
	public String quoter;
	public String hostmask;
	public int lines;
	public int score;
	public int up;
	public int down;
	public long time;
}

class QuoteLine
{
	public int id;
	public int quoteID;
	public int lineNumber;
	public String nick;
	public String message;
	public boolean isAction;
}

class RecentQuote
{
	public QuoteObject quote;
	// No sense in caching the lines here.
	public long time;
	/*
	 * 0 = displayed
	 * 1 = quoted
	 */
	public int type;
}

class QuoteEnumerator
{
	public QuoteEnumerator()
	{
		// Unhide
	}

	public QuoteEnumerator(final String enumSource, final int[] idList)
	{
		this.enumSource = enumSource;
		this.idList = "";
		for (int i = 0; i < idList.length; i++) {
			if (i > 0)
				this.idList += ",";
			this.idList += idList[i];
		}
		this.index = (int)Math.floor(Math.random() * idList.length);
		this.lastUsed = System.currentTimeMillis();
	}

	public int getNext()
	{
		if (intIdList == null)
			setupIDListInt();

		index++;
		if (index >= intIdList.length) {
			index = 0;
		}
		lastUsed = System.currentTimeMillis();
		return intIdList[index];
	}

	private void setupIDListInt()
	{
		final String[] list = this.idList.split("\\s*,\\s*");
		this.intIdList = new int[list.length];
		for (int i = 0; i < list.length; i++) {
			this.intIdList[i] = Integer.parseInt(list[i]);
		}
	}

	public int getSize()
	{
		if (intIdList == null)
			setupIDListInt();
		return intIdList.length;
	}

	public int id;
	public String enumSource;
	public String idList;
	private int[] intIdList = null;
	public int index;
	public long lastUsed;
}

public class Quote
{
	private static int MINLENGTH = 7; // Minimum length of a line to be quotable using simple syntax.
	private static int MINWORDS = 2; // Minimum words in a line to be quotable using simple syntax.
	private static int HISTORY = 100; // Lines of history to search.
	private static int EXCERPT = 40; // Maximum length of excerpt text in replies to create.
	private static int MAXLINES = 10; // Maximum allowed lines in a quote.
	private static int MAXCLAUSES = 20; // Maximum allowed "clauses" - components of the query, e.g score.
	private static int MAXJOINS = 6; // Maximum allowed "joins" - one needed per regexp or nick clause.
	private static int RECENTLENGTH = 20; // Maximum length of "recent quotes" list for a context.
	private static String IGNORE = "quoteme|quote|quoten|quote.create"; // Ignore these when searching for regex quotes.
	private static int THRESHOLD = -3; // Lowest karma of displayed quote.
	private static long ENUM_TIMEOUT = 5 * 60 * 1000; // 5 minutes

	private final HashMap<String,List<RecentQuote>> recentQuotes;

	private final Modules mods;
	private final IRCInterface irc;
	private Pattern ignorePattern;

	public Quote( final Modules mods, final IRCInterface irc )
	{
		this.mods = mods;
		this.irc = irc;
		recentQuotes = new HashMap<String,List<RecentQuote>>();
		updatePatterns();
		mods.interval.callBack("clean-enums", 60000, 1);
	}

	public String[] info()
	{
		return new String[] {
			"Plugin to allow users to create a database of infamous quotes.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	void updatePatterns()
	{
		this.ignorePattern = Pattern.compile(
				"^(?:" + irc.getTriggerRegex() + ")" +
				"(?:" + IGNORE + ")", Pattern.CASE_INSENSITIVE);
	}

	public String[] helpTopics = { "UsingCreate", "CreateExamples", "UsingGet" };

	public String[] helpUsingCreate = {
		  "There's 4 ways of calling Quote.Create.",
		  "If you pass no parameters (or action: or privmsg:), the most recent line (or action) that's long enough will be quoted.",
		  "With just a nickname (or privmsg:<Nick>), the most recent line from that nick will be quoted.",
		  "With action:<Nick>, the most recent action from that nick will be quoted.",
		  "Finally, you can specify one or 2 regular expression searches. If"
		+ " you specify just one, the most recent matching line will be quoted."
		+ " With 2, the first one matches the start of the quote, and the"
		+ " second matches the end. Previous quote commands are skipped when doing"
		+ " any regex matching.",
		  "'Long enough' in this context means at least " + MINLENGTH
		+ " characters, and at least " + MINWORDS + " words.",
		  "Note that nicknames are always made into their short form: 'privmsg:fred|bed' will quote people called 'fred', 'fred|busy', etc.",
		  "See CreateExamples for some examples."
	};

	// A class to track the scores of a person.
	class ScoreTracker implements Comparable<ScoreTracker>
	{
		public String name;
		public int count;

		// Create a new ScoreTracker, given the name of the person.
		public ScoreTracker(final String tname)
		{
			name=tname;
		}

		public void addQuote()
		{
			count++;
		}

		// Compare to another ScoreTracker.
		public int compareTo(final ScoreTracker o)
		{
			// Ignore the "null" case.
			if (o == null)
				return 1;

			return o.count - count;
		}
	}


	public String[] helpCreateExamples = {
		  "'Quote.Create action:Fred' will quote the most recent action from Fred, regardless of length.",
		  "'Quote.Create privmsg:' will quote the most recent line that's long enough.",
		  "'Quote.Create fred:/blizzard/ /suck/' will quote the most recent possible quote starting with fred saying 'Blizzard', ending with the first line containing 'suck'."
	};

	public String[] helpUsingGet = {
		"There are several clauses you can use to select quotes.",
		"You can specify a number, which will be used as a quote ID.",
		"A clause '<Selector>:<Relation><Number>', where <Selector> is one of 'score' or 'length', <Relation> is '>', '<' or '=' and <Number> is some number.",
		"You can use 'quoter:<Nick>' to get quotes made by <Nick>.",
		"Finally, you can use '<Nick>', '/<Regex>/' or '<Nick>:/<Regex>/' to match only quotes from <Nick>, quotes matching <Regex> or quotes where <Nick> said something matching <Regex>."
	};

	public String[] helpCommandCreate = {
		"Create a new quote. See Quote.UsingCreate for more info.",
		"[ [privmsg:][<Nick>] | action:[<Nick>] | [<Nick>:]/<Regex>/ [[<Nick>:]/<Regex>/] ]",
		"<Nick> is a nickname to quote",
		"<Regex> is a regular expression to use to match lines"
	};
	public void commandCreate( final Message mes )
	{
		if (!(mes instanceof ChannelEvent))
		{
			irc.sendContextReply( mes, "Sorry, this command can only be used in a channel" );
			return;
		}
		final List<Message> history = mods.history.getLastMessages( mes, HISTORY );

		String param = mods.util.getParamString(mes).trim();

		// Default is privmsg
		if ( param.equals("") || (param.charAt(0) < '0' || param.charAt(0) > '9') && param.charAt(0) != '/' && param.indexOf(':') == -1 && param.indexOf(' ') == -1 )
			param = "privmsg:" + param;

		final List<Message> lines = new ArrayList<Message>();
		if (param.charAt(0) >= '0' && param.charAt(0) <= '9')
		{
			// First digit is a number. That means the rest are, too! (Or at least, we assume so.)
			final String bits[] = param.split(" +");
			int offset = 0;
			int size;
			if (bits.length > 2)
			{
				irc.sendContextReply(mes, "When quoting by offset, you must supply only 1 or 2 parameters.");
				return;
			}
			else if (bits.length == 2)
			{
				try
				{
					offset = Integer.parseInt(bits[1]);
				}
				catch (final NumberFormatException e)
				{
					irc.sendContextReply(mes, "Numeric offset " + bits[1] + " was not a valid integer...");
					return;
				}
			}
			try
			{
				size = Integer.parseInt(bits[0]);
			}
			catch (final NumberFormatException e)
			{
				irc.sendContextReply(mes, "Numeric size " + bits[0] + " was not a valid integer...");
				return;
			}
			if (offset < 0)
			{
				irc.sendContextReply(mes, "Can't quote things that haven't happened yet!");
				return;
			}
			else if (size < 1)
			{
				irc.sendContextReply(mes, "Can't quote an empty quote!");
				return;
			}
			else if (offset + size > history.size())
			{
				irc.sendContextReply(mes, "Can't quote -- memory like a seive!");
				return;
			}
			// Must do this backwards
			for(int i=offset + size - 1; i>=offset; i--)
				lines.add(history.get(i));
		}
		else if (param.charAt(0) != '/' && param.indexOf(':') == -1 && param.indexOf(' ') == -1)
		{
			// It's a nickname.
			final String bits[] = param.split("\\s+");
			if (bits.length > 2)
			{
				irc.sendContextReply(mes, "When quoting by nickname, you must supply only 1 parameter.");
				return;
			}
			final String findNick = mods.nick.getBestPrimaryNick( bits[0] ).toLowerCase();
			for(int i=0; i<history.size(); i++)
			{
				final Message line = history.get(i);
				final String text = line.getMessage();
				if (text.length() < MINLENGTH || text.split(" +").length < MINWORDS)
					continue;
				final String guessNick = mods.nick.getBestPrimaryNick( line.getNick() );
				if ( guessNick.toLowerCase().equals(findNick) )
				{
					lines.add(line);
					break;
				}
			}
			if (lines.size() == 0)
			{
				irc.sendContextReply(mes, "No quote found for nickname " + findNick + ".");
				return;
			}
		}
		else if (param.toLowerCase().startsWith("action:") || param.toLowerCase().startsWith("privmsg:"))
		{
			final Class<? extends ChannelEvent> thing;
			if (param.toLowerCase().startsWith("action:"))
				thing = ChannelAction.class;
			else
				thing = ChannelMessage.class;

			// It's an action from a nickname
			String bits[] = param.split("\\s+");
			if (bits.length > 2)
			{
				irc.sendContextReply(mes, "When quoting by type, you must supply only 1 parameter.");
				return;
			}
			bits = bits[0].split(":");
			String findNick;
			if (bits.length == 2)
				findNick = mods.nick.getBestPrimaryNick( bits[1] ).toLowerCase();
			else
				findNick = null;
			for(int i=0; i<history.size(); i++)
			{
				final Message line = history.get(i);
				final String text = line.getMessage();
				if (!thing.isInstance(line))
					continue;

				if (findNick != null)
				{
					final String guessNick = mods.nick.getBestPrimaryNick( line.getNick() );
					if ( guessNick.toLowerCase().equals(findNick) )
					{
						lines.add(line);
						break;
					}
				}
				else
				{
					// Check length etc.
					if (text.length() < MINLENGTH || text.split(" +").length < MINWORDS)
						continue;
					lines.add(line);
					break;
				}
			}
			if (lines.size() == 0)
			{
				if (findNick != null)
					irc.sendContextReply(mes, "No quotes found for nickname " + findNick + ".");
				else
					irc.sendContextReply(mes, "No recent quotes of that type!");
				return;
			}
		}
		else
		{
			// Final case: Regex quoting.
			// Matches anything of the form [NICK{:| }]/REGEX/ [[NICK{:| }]/REGEX/]

			// What's allowed in the //s:
			final String slashslashcontent="[^/]";

			// The '[NICK{:| }]/REGEX/' bit:
			final String token="(?:([^\\s:/]+)[:\\s])?/(" + slashslashcontent + "+)/";

			// The '[NICK{:| }]/REGEX/ [[NICK{:| }]/REGEX/]' bit:
			final Matcher ma = Pattern.compile(token + "(?:\\s+" + token + ")?", Pattern.CASE_INSENSITIVE).matcher(param);

			if (!ma.matches())
			{
				irc.sendContextReply(mes, "Sorry, your string looked like a regex quote but I couldn't decipher it.");
				return;
			}

			String startNick, startRegex, endNick, endRegex;
			if (ma.group(4) != null)
			{
				// The second parameter exists ==> multiline quote
				startNick = ma.group(1);
				startRegex = "(?i).*" + ma.group(2) + ".*";
				endNick = ma.group(3);
				endRegex = "(?i).*" + ma.group(4) + ".*";
			}
			else
			{
				startNick = null;
				startRegex = null;
				endNick = ma.group(1);
				endRegex = "(?i).*" + ma.group(2) + ".*";
			}

			if (startNick != null)
				startNick = mods.nick.getBestPrimaryNick( startNick ).toLowerCase();
			if (endNick != null)
				endNick = mods.nick.getBestPrimaryNick( endNick ).toLowerCase();

			// OK, do the match!
			int endIndex = -1, startIndex = -1;
			for(int i=0; i<history.size(); i++)
			{
				final Message line = history.get(i);

				// Completely disregard lines that are quotey.
				if (ignorePattern.matcher(line.getMessage()).find())
					continue;

				System.out.println("<" + line.getNick() + "> " + line.getMessage());

				final String nick = mods.nick.getBestPrimaryNick( line.getNick() ).toLowerCase();
				if ( endRegex != null )
				{
					// Not matched the end yet (the regex being null is an indicator for us having matched it, obviously. But only the end regex).

					if ((endNick == null || endNick.equals(nick))
							&& line.getMessage().matches(endRegex))
					{
						// But have matched now...
						endRegex = null;
						endIndex = i;

						// If we weren't doing a multiline regex quote, this is actually the start index.
						if ( startRegex == null )
						{
							startIndex = i;

							// And hence we're done.
							break;
						}
					}
				}
				else // ..the end has been matched, and we're doing a multiline, so we're looking for the start:
				{
					if ((startNick == null || startNick.equals(nick)) && line.getMessage().matches(startRegex))
					{
						// It matches, huzzah, we're done:
						startIndex = i;
						break;
					}
				}
			}

			if  (endIndex == -1) // We failed to match the 'first' line:
			{
				if (startRegex == null) // We wern't going for a multi-line match:
					irc.sendContextReply(mes, "Sorry, couldn't find the line you were after.");
				else
					irc.sendContextReply(mes, "Sorry, the second regex (for the ending line) didn't match anything, not checking for the start line.");

				return;
			}

			if (startIndex == -1)
			{
				final Message endLine=history.get(endIndex);
				irc.sendContextReply(mes, "Sorry, the first regex (for the start line) couldn't be matched before the end-line I chose: " + formatPreviewLine(endLine.getNick(), endLine.getMessage(), endLine instanceof ChannelAction));
				return;
			}

			for(int i=startIndex; i>=endIndex; i--)
				lines.add(history.get(i));
		}

		// Have some lines.

		// Is it a sensible size?
		if (lines.size() > MAXLINES)
		{
			irc.sendContextReply(mes, "Sorry, this quote is longer than the maximum size of " + MAXLINES + " lines.");
			return;
		}

		// Check for people suspiciously quoting themselves.
		// For now, that's just if they are the final line in the quote.
		final Message last = lines.get(lines.size() - 1);
		//*/ Remove the first slash to comment me out.
		if (last.getLogin().compareToIgnoreCase(mes.getLogin()) == 0
				&& last.getHostname().compareToIgnoreCase(mes.getHostname()) == 0)
		{
			// Suspicious!
			irc.sendContextReply(mes, "Sorry, no quoting yourself!");
			return;
		} //*/

		// OK, build a QuoteObject...
		final QuoteObject quote = new QuoteObject();
		quote.quoter = mods.nick.getBestPrimaryNick(mes.getNick());
		quote.hostmask = (mes.getLogin() + "@" + mes.getHostname()).toLowerCase();
		quote.lines = lines.size();
		quote.score = 0;
		quote.up = 0;
		quote.down = 0;
		quote.time = System.currentTimeMillis();

		// QuoteLine object; quoteID will be filled in later.

		final List<QuoteLine> quoteLines = new ArrayList<QuoteLine>(lines.size());

		mods.odb.runTransaction( new ObjectDBTransaction() {
			@Override
			public void run()
		{
			quote.id = 0;
			save(quote);

			// Now have a quote ID!
			quoteLines.clear();
			for(int i=0; i<lines.size(); i++)
			{
				final QuoteLine quoteLine = new QuoteLine();
				quoteLine.quoteID = quote.id;
				quoteLine.id = 0;
				quoteLine.lineNumber = i;
				quoteLine.nick = mods.nick.getBestPrimaryNick(lines.get(i).getNick());
				quoteLine.message = lines.get(i).getMessage();
				quoteLine.isAction = lines.get(i) instanceof ChannelAction;
				save(quoteLine);
				quoteLines.add(quoteLine);
			}
		}});

		// Remember this quote for later...
		addLastQuote(mes.getContext(), quote, 1);

		irc.sendContextReply( mes, "OK, added " + quote.lines + " line quote #" + quote.id + ": (" +
			new SimpleDateFormat("h:mma").format(new Date(lines.get(0).getMillis())).toLowerCase() +
			") " + formatPreview(quoteLines)
		);
	}

	public String[] helpCommandAdd = {
		"Add a quote to the database.",
		"<Nick> <Text> [ ||| <Nick> <Text> ... ]",
		"the nickname of the person who said the text (of the form '<nick>' or '* nick' or just simply 'nick')",
		"the text that was actually said"
	};
	public java.security.Permission permissionCommandAdd = new ChoobPermission("plugins.quote.add");
	public void commandAdd(final Message mes)
	{
		mods.security.checkNickPerm(permissionCommandAdd, mes);

		final String params = mods.util.getParamString( mes );

		final String[] lines = params.split("\\s+\\|\\|\\|\\s+");

		final QuoteLine[] content = new QuoteLine[lines.length];

		for(int i=0; i<lines.length; i++)
		{
			final String line = lines[i];
			String nick, text;
			boolean action = false;
			if (line.charAt(0) == '*')
			{
				final int spacePos1 = line.indexOf(' ');
				if (spacePos1 == -1)
				{
					irc.sendContextReply(mes, "Line " + i + " was invalid!");
					return;
				}
				final int spacePos2 = line.indexOf(' ', spacePos1 + 1);
				if (spacePos2 == -1)
				{
					irc.sendContextReply(mes, "Line " + i + " was invalid!");
					return;
				}
				nick = line.substring(spacePos1 + 1, spacePos2);
				text = line.substring(spacePos2 + 1);
				action = true;
			}
			else if (line.charAt(0) == '<')
			{
				final int spacePos = line.indexOf(' ');
				if (spacePos == -1)
				{
					irc.sendContextReply(mes, "Line " + i + " was invalid!");
					return;
				}
				else if (line.charAt(spacePos - 1) != '>')
				{
					irc.sendContextReply(mes, "Line " + i + " was invalid!");
					return;
				}
				nick = line.substring(1, spacePos - 1);
				text = line.substring(spacePos + 1);
			}
			else
			{
				final int spacePos = line.indexOf(' ');
				if (spacePos == -1)
				{
					irc.sendContextReply(mes, "Line " + i + " was invalid!");
					return;
				}
				nick = line.substring(0, spacePos);
				text = line.substring(spacePos + 1);
			}
			final QuoteLine quoteLine = new QuoteLine();
			quoteLine.lineNumber = i;
			quoteLine.nick = mods.nick.getBestPrimaryNick(nick);
			quoteLine.message = text;
			quoteLine.isAction = action;
			content[i] = quoteLine;
		}

		final QuoteObject quote = new QuoteObject();
		quote.quoter = mods.nick.getBestPrimaryNick(mes.getNick());
		quote.hostmask = (mes.getLogin() + "@" + mes.getHostname()).toLowerCase();
		quote.lines = lines.length;
		quote.score = 0;
		quote.up = 0;
		quote.down = 0;
		quote.time = System.currentTimeMillis();

		// QuoteLine object; quoteID will be filled in later.

		final List<QuoteLine> quoteLines = new ArrayList<QuoteLine>(lines.length);

		mods.odb.runTransaction( new ObjectDBTransaction() {
			@Override
			public void run()
		{
			// Have to set ID etc. here in case transaction blows up.
			quote.id = 0;
			save(quote);

			// Now have a quote ID!
			quoteLines.clear();
			for (final QuoteLine quoteLine : content)
			{
				quoteLine.quoteID = quote.id;
				quoteLine.id = 0;
				save(quoteLine);
				quoteLines.add(quoteLine);
			}
		}});

		addLastQuote(mes.getContext(), quote, 1);

		irc.sendContextReply( mes, "OK, added quote " + quote.id + ": " + formatPreview(quoteLines) );
	}

	private final String formatPreviewLine(final String nick, final String message, final boolean isAction)
	{
		String text, prefix;

		if (message.length() > EXCERPT)
			text = message.substring(0, 27) + "...";
		else
			text = message;

		if (isAction)
			prefix = "* " + nick;
		else
			prefix = "<" + nick + ">";

		return prefix + " " + text;

	}

	private String formatPreview(final List<QuoteLine> lines)
	{
		if (lines.size() == 1)
		{
			final QuoteLine line = lines.get(0);
			return formatPreviewLine(line.nick, line.message, line.isAction);
		}

		// last is initalised above
		final QuoteLine first = lines.get(0);

		String firstText;
		if (first.isAction)
			firstText = "* " + first.nick;
		else
			firstText = "<" + first.nick + ">";
		if (first.message.length() > EXCERPT)
			firstText += " " + first.message.substring(0, 27) + "...";
		else
			firstText += " " + first.message;

		final QuoteLine last = lines.get(lines.size() - 1);
		String lastText;
		if (last.isAction)
			lastText = "* " + last.nick;
		else
			lastText = "<" + last.nick + ">";
		if (last.message.length() > EXCERPT)
			lastText += " " + last.message.substring(0, 27) + "...";
		else
			lastText += " " + last.message;

		return firstText + " -> " + lastText;
	}

	// Interval
	public void interval(final Object param)
	{
		if ("clean-enums".equals(param)) {
			// Clean up dead enumerators.
			final long lastUsedCutoff = System.currentTimeMillis() - ENUM_TIMEOUT;
			final List<QuoteEnumerator> deadEnums = mods.odb.retrieve(QuoteEnumerator.class, "WHERE lastUsed < " + lastUsedCutoff);
			for (int i = 0; i < deadEnums.size(); i++) {
				mods.odb.delete(deadEnums.get(i));
			}
			mods.interval.callBack(param, 60000, 1);
		}
	}

	private QuoteObject pickRandomQuote(final List<QuoteObject> quotes, String enumSource)
	{
		int quoteId = -1;
		enumSource = enumSource.toLowerCase();
		final List<QuoteEnumerator> enums = mods.odb.retrieve(QuoteEnumerator.class, "WHERE enumSource = '" + mods.odb.escapeString(enumSource) + "'");
		QuoteEnumerator qEnum = null;
		if (enums.size() >= 1) {
			qEnum = enums.get(0);
			if (qEnum.getSize() != quotes.size()) {
				// Count has changed: invalidated!
				mods.odb.delete(qEnum);
				qEnum = null;
			} else {
				// Alright, step to the next one.
				quoteId = qEnum.getNext();
				mods.odb.update(qEnum);
			}
		}
		if (qEnum == null) {
			// No enumerator, create one.
			final int[] idList = new int[quotes.size()];
			for (int i = 0; i < quotes.size(); i++)
				idList[i] = quotes.get(i).id;

			qEnum = new QuoteEnumerator(enumSource, idList);
			quoteId = qEnum.getNext();
			mods.odb.save(qEnum);
		}

		QuoteObject rvQuote = null;
		for (int i = 0; i < quotes.size(); i++) {
			final QuoteObject quote = quotes.get(i);
			if (quote.id == quoteId) {
				rvQuote = quote;
				break;
			}
		}
		return rvQuote;
	}

	public String[] helpCommandGet = {
		"Get a random quote from the database.",
		"[ <Clause> [ <Clause> ... ]]",
		"<Clause> is a clause to select quotes with (see Quote.UsingGet)"
	};
	public void commandGet(final Message mes)
	{
		final String whereClause = getClause(mods.util.getParamString(mes));
		List<QuoteObject> quotes;
		try
		{
			quotes = mods.odb.retrieve(QuoteObject.class, whereClause);
		}
		catch (final ObjectDBError e)
		{
			if (e.getCause() instanceof java.sql.SQLException)
			{
				irc.sendContextReply(mes, "Could not retrieve: " + e.getCause());
				return;
			}
			throw e;
		}

		if (quotes.size() == 0)
		{
			irc.sendContextReply(mes, "No quotes found!");
			return;
		}

		final QuoteObject quote = pickRandomQuote(quotes, mes.getContext() + ":" + whereClause);

		final List<QuoteLine> lines = mods.odb.retrieve(QuoteLine.class, "WHERE quoteID = " + quote.id + " ORDER BY lineNumber");
		final Iterator<QuoteLine> l = lines.iterator();
		if (!l.hasNext())
		{
			irc.sendContextReply(mes, "Found quote " + quote.id + " but it was empty!");
			return;
		}
		while(l.hasNext())
		{
			final QuoteLine line = l.next();
			if (line.isAction)
				irc.sendContextMessage( mes, "* " + line.nick + " " + line.message );
			else
				irc.sendContextMessage( mes, "<" + line.nick + "> " + line.message );
		}

		// Remember this quote for later...
		addLastQuote(mes.getContext(), quote, 0);
	}

	public String[] helpApiSingleLineQuote = {
		"Get a single line quote from the specified nickname, optionally adding it to the recent quotes list for the passed context.",
		"Either the single line quote, or null if there was none.",
		"<nick> [<context>]",
		"the nickname to get a quote from",
		"the optional context in which the quote will be displayed"
	};
	public String apiSingleLineQuote(final String nick)
	{
		return apiSingleLineQuote(nick, null);
	}

	public String apiSingleLineQuote(final String nick, final String context)
	{
		return apiSingleLineQuote(nick, context, "");
	}

	public String apiSingleLineQuote(final String nick, final String context, final String querysuffix)
	{
		final String whereClause = getClause(nick + " length:=1" + " " + querysuffix);
		final List<QuoteObject> quotes = mods.odb.retrieve( QuoteObject.class, "SORT BY RANDOM LIMIT (1) " + whereClause);
		if (quotes.size() == 0)
			return null;

		final QuoteObject quote = quotes.get(0);
		final List <QuoteLine>lines = mods.odb.retrieve( QuoteLine.class, "WHERE quoteID = " + quote.id );

		if (lines.size() == 0)
		{
			System.err.println("Found quote " + quote.id + " but it was empty!" );
			return null;
		}

		if (context != null)
			addLastQuote(context, quote, 0);

		final QuoteLine line = lines.get(0);

		if (line.isAction)
			return "/me " + line.message;
		return line.message;
	}

	private void addLastQuote(final String context, final QuoteObject quote, final int type)
	{
		synchronized(recentQuotes)
		{
			List<RecentQuote> recent = recentQuotes.get(context);
			if (recent == null)
			{
				recent = new LinkedList<RecentQuote>();
				recentQuotes.put( context, recent );
			}

			final RecentQuote info = new RecentQuote();
			info.quote = quote;
			info.time = System.currentTimeMillis();
			info.type = type;

			recent.add(0, info);

			while (recent.size() > RECENTLENGTH)
				recent.remove( RECENTLENGTH );
		}
	}

	public String[] helpCommandCount = {
		"Get the number of matching quotes.",
		"[ <Clause> [ <Clause> ... ]]",
		"<Clause> is a clause to select quotes with (see Quote.UsingGet)"
	};
	public void commandCount( final Message mes )
	{
		final String whereClause = getClause( mods.util.getParamString( mes ) );
		final List<QuoteObject> quoteCounts = mods.odb.retrieve( QuoteObject.class, whereClause );
		final Set<Integer> ids = new HashSet<Integer>();

		for (final QuoteObject q : quoteCounts)
			ids.add(Integer.valueOf(q.id));

		final int count = ids.size();

		if (count == 0)
			irc.sendContextReply( mes, "Sorry, no quotes match!" );
		else if (count == 1)
			irc.sendContextReply( mes, "There's just the one quote..." );
		else
			irc.sendContextReply( mes, "There are " + count + " quotes!" );
	}

	/*
	public String[] helpCommandScores = {
		"Returns the most quoted people.",
		"[ <Clause> [ <Clause> ... ]]",
		"<Clause> is a clause to select quotes with (see Quote.UsingGet)"
	};

	public void commandScores( Message mes ) throws ChoobException
	{
		final String whereClause = getClause( mods.util.getParamString( mes ) );
		//final List<QuoteObject> quoteIds = ;

		HashMap <String, ScoreTracker> scores = new HashMap<String, ScoreTracker>();
		final List<QuoteObject> quoteCounts = mods.odb.retrieve( QuoteObject.class, whereClause );
		final Set<Integer> ids = new HashSet<Integer>();

		for (QuoteObject q : quoteCounts)
			ids.add(q.id);


		ScoreTracker st;

		for (Integer qid : ids)
		{
			Set<String> credititedThisQuote = new HashSet<String>();
			for (QuoteLine line : (List<QuoteLine>)mods.odb.retrieve( QuoteLine.class, "WHERE quoteID = " + qid))
			{
				//System.out.println("Crediting " + line.nick + " for their work in " + qid);
				if (!credititedThisQuote.contains(line.nick))
				{
					if ((st = scores.get(line.nick)) == null)
						scores.put(line.nick, st = new ScoreTracker(line.nick));

					st.addQuote();
					credititedThisQuote.add(line.nick);
				}
			}
		}

		ArrayList<ScoreTracker> l = new ArrayList(scores.values());
		Collections.sort(l);

		StringBuilder b = new StringBuilder("Top scorers: ");
		int i=1;

		for (ListIterator<ScoreTracker> it = l.listIterator(); it.hasNext() && it.nextIndex() < 5; i++)
		{
			ScoreTracker p = it.next();
			b.append(i).append(") ").append(p.name).append(" with ").append(p.count).append(", ");
		}
		irc.sendContextReply(mes, b.toString());
	}
	*/


	// quotekarma, quotesummary
	public String[] helpCommandSummary = {
		"Get a summary of matching quotes.",
		"[ <Clause> [ <Clause> ... ]]",
		"<Clause> is a clause to select quotes with (see Quote.UsingGet)"
	};
	@SuppressWarnings("boxing")
	public void commandSummary( final Message mes )
	{
		final String whereClause = getClause( mods.util.getParamString(mes) );
		final List<QuoteObject> quoteKarmasSpam = mods.odb.retrieve( QuoteObject.class, whereClause );

		final Map<Integer, QuoteObject> quoteKarmaIds = new HashMap<Integer, QuoteObject>();
		for (final QuoteObject q : quoteKarmasSpam)
			quoteKarmaIds.put(q.id, q);

		final List<Integer> quoteKarmas = new ArrayList<Integer>();
		for (final QuoteObject q : quoteKarmaIds.values())
			quoteKarmas.add(q.score);

		final int count = quoteKarmas.size();
		int nonZeroCount = 0;
		int total = 0;
		int max = 0, min = 0;
		for(final Integer i: quoteKarmas)
		{
			total += i;
			if (i != 0)
			{
				nonZeroCount++;
				if (i < min)
					min = i;
				else if (i > max)
					max = i;
			}
		}

		final DecimalFormat format = new DecimalFormat("##0.00");

		final String avKarma = format.format((double) total / (double) count);
		final String avNonZeroKarma = format.format((double) total / (double) nonZeroCount);

		if (count == 0)
			irc.sendContextReply( mes, "Sorry, no quotes found!" );
		else if (count == 1)
			irc.sendContextReply( mes, "I only found one quote; karma is " + total + "." );
		else
			irc.sendContextReply( mes, "Found " + count + " quotes. The total karma is " + total + ", average " + avKarma + ". " + nonZeroCount + " quotes had a karma; average for these is " + avNonZeroKarma + ". Min karma is " + min + " and max is " + max + "." );
	}

	public String[] helpCommandInfo = {
		"Get info about a particular quote.",
		"[ <QuoteID> ]",
		"<QuoteID> is the ID of a quote"
	};
	public void commandInfo( final Message mes )
	{
		int quoteID = -1;
		final List<String> params =  mods.util.getParams(mes);
		if ( params.size() > 2 )
		{
			irc.sendContextReply( mes, "Syntax: 'Quote.Info " + helpCommandInfo[1] + "'." );
			return;
		}

		// Check input
		try
		{
			if ( params.size() == 2 )
				quoteID = Integer.parseInt( params.get(1) );
		}
		catch ( final NumberFormatException e )
		{
			irc.sendContextReply( mes, "Syntax: 'Quote.Info " + helpCommandInfo[1] + "'." );
			return;
		}

		if (quoteID == -1)
		{
			synchronized(recentQuotes)
			{
				final String context = mes.getContext();
				final List<RecentQuote> recent = recentQuotes.get(context);
				if (recent == null || recent.size() == 0)
				{
					irc.sendContextReply( mes, "Sorry, no quotes seen from here!" );
					return;
				}

				quoteID = recent.get(0).quote.id;
			}
		}

		final List<QuoteObject> quotes = mods.odb.retrieve( QuoteObject.class, "WHERE id = " + quoteID );
		if (quotes.size() == 0)
		{
			irc.sendContextReply( mes, "Quote " + quoteID + " does not exist!" );
			return;
		}

		final QuoteObject quote = quotes.get(0);

		if (quote.time == 0)
			irc.sendContextReply(mes, "Quote #" + quote.id + ": Quoted by " + quote.quoter + " at Bob knows when. This is a " + quote.lines + " line quote with a karma of " + quote.score + " (" + quote.up + " up, " + quote.down + " down)." );
		else
			irc.sendContextReply(mes, "Quote #" + quote.id + ": Quoted by " + quote.quoter + " on " + new Date(quote.time) + ". This is a " + quote.lines + " line quote with a karma of " + quote.score + " (" + quote.up + " up, " + quote.down + " down)." );
	}

	public String[] helpCommandRemove = {
		"Remove your most recently added quote.",
	};
	public void commandRemove( final Message mes )
	{
		// Quotes are stored by context...
		synchronized(recentQuotes)
		{
			final String context = mes.getContext();
			final List<RecentQuote> recent = recentQuotes.get(context);
			if (recent == null || recent.size() == 0)
			{
				irc.sendContextReply( mes, "Sorry, no quotes seen from here!" );
				return;
			}

			final String nick = mods.nick.getBestPrimaryNick(mes.getNick()).toLowerCase();
			final String hostmask = (mes.getLogin() + "@" + mes.getHostname()).toLowerCase();

			final Iterator<RecentQuote> iter = recent.iterator();
			QuoteObject quote = null;
			RecentQuote info = null;
			while(iter.hasNext())
			{
				info = iter.next();
				if (info.type == 1 && info.quote.quoter.toLowerCase().equals(nick)
						&& info.quote.hostmask.equals(hostmask))
				{
					quote = info.quote;
					break;
				}
			}

			if (quote == null)
			{
				irc.sendContextReply( mes, "Sorry, you haven't quoted anything recently here!" );
				return;
			}

			final QuoteObject theQuote = quote;
			final List<QuoteLine> quoteLines = mods.odb.retrieve( QuoteLine.class, "WHERE quoteID = " + quote.id );
			mods.odb.runTransaction( new ObjectDBTransaction() {
				@Override
				public void run()
			{
				delete(theQuote);

				// Now have a quote ID!
				for(final QuoteLine line: quoteLines)
				{
					delete(line);
				}
			}});

			recent.remove(info); // So the next unquote doesn't hit it

			irc.sendContextReply( mes, "OK, unquoted quote " + quote.id + " (" + formatPreview(quoteLines) + ")." );
		}
	}

	public String[] helpCommandLast = {
		"Get a list of recent quote IDs.",
		"[<Count>]",
		"<Count> is the maximum number to return (default is 1)"
	};
	public void commandLast( final Message mes )
	{
		// Get a count...
		int count = 1;
		final String param =  mods.util.getParamString(mes);
		try
		{
			if ( param.length() > 0 )
				count = Integer.parseInt( param );
		}
		catch ( final NumberFormatException e )
		{
			irc.sendContextReply( mes, "'" + param + "' is not a valid integer!" );
			return;
		}
		synchronized(recentQuotes)
		{
			final String context = mes.getContext();
			final List<RecentQuote> recent = recentQuotes.get(context);
			if (recent == null || recent.size() == 0)
			{
				irc.sendContextReply( mes, "Sorry, no quotes seen from here!" );
				return;
			}

			// Ugly hack to avoid lack of last()...
			final ListIterator<RecentQuote> iter = recent.listIterator();

			RecentQuote info = null;
			boolean first = true;
			String output = null;
			int remain = count;
			while(iter.hasNext() && remain-- > 0)
			{
				info = iter.next();
				if (!first)
					output = output + ", " + info.quote.id;
				else
					output = "" + info.quote.id;
				first = false;
			}

			if (count == 1)
				irc.sendContextReply( mes, "Most recent quote ID: " + output + "." );
			else
				irc.sendContextReply( mes, "Most recent quote IDs (most recent first): " + output + "." );
		}
	}

	public String[] helpCommandKarmaMod = {
		"Increase or decrease the karma of a quote.",
		"<Direction> [<ID>]",
		"<Direction> is 'up' or 'down'",
		"<ID> is an optional quote ID (default is to use the most recent)"
	};
	public synchronized void commandKarmaMod( final Message mes )
	{
		int quoteID = -1;
		boolean up = true;
		final List<String> params =  mods.util.getParams(mes);
		if (params.size() == 1 || params.size() > 3)
		{
			irc.sendContextReply( mes, "Syntax: quote.KarmaMod {up|down} [number]" );
			return;
		}

		// Check input
		try
		{
			if ( params.size() == 3 )
				quoteID = Integer.parseInt( params.get(2) );
		}
		catch ( final NumberFormatException e )
		{
			// History dictates that this be ignored.
		}

		if ( params.get(1).toLowerCase().equals("down") )
			up = false;
		else if ( params.get(1).toLowerCase().equals("up") )
			up = true;
		else
		{
			irc.sendContextReply( mes, "Syntax: quote.KarmaMod {up|down} [number]" );
			return;
		}

		String leet;
		if (up)
			leet = "l33t";
		else
			leet = "lame";

		if (quoteID == -1)
		{
			synchronized(recentQuotes)
			{
				final String context = mes.getContext();
				final List<RecentQuote> recent = recentQuotes.get(context);
				if (recent == null || recent.size() == 0)
				{
					irc.sendContextReply( mes, "Sorry, no quotes seen from here!" );
					return;
				}

				quoteID = recent.get(0).quote.id;
			}
		}

		final List<QuoteObject> quotes = mods.odb.retrieve( QuoteObject.class, "WHERE id = " + quoteID );
		if (quotes.size() == 0)
		{
			irc.sendContextReply( mes, "No such quote to " + leet + "!" );
			return;
		}

		final QuoteObject quote = quotes.get(0);
		if (up)
		{
			if (quote.score == THRESHOLD - 1)
			{
				if (mods.security.hasNickPerm( new ChoobPermission( "quote.delete" ), mes ))
				{
					quote.score++;
					quote.up++;
					irc.sendContextReply( mes, "OK, quote " + quoteID + " is now leet enough to be seen! Current karma is " + quote.score + "." );
				}
				else
				{
					irc.sendContextReply( mes, "Sorry, that quote is on the karma threshold. Only an admin can make it more leet!" );
					return;
				}
			}
			else
			{
				quote.score++;
				quote.up++;
				irc.sendContextReply( mes, "OK, quote " + quoteID + " is now more leet! Current karma is " + quote.score + "." );
			}
		}
		else if (quote.score == THRESHOLD)
		{
			if (mods.security.hasNickPerm( new ChoobPermission( "quote.delete" ), mes ))
			{
				quote.score--;
				quote.down++;
				irc.sendContextReply( mes, "OK, quote " + quoteID + " is now too lame to be seen! Current karma is " + quote.score + "." );
			}
			else
			{
				irc.sendContextReply( mes, "Sorry, that quote is on the karma threshold. Only an admin can make it more lame!" );
				return;
			}
		}
		else
		{
			quote.score--;
			quote.down++;

			irc.sendContextReply( mes, "OK, quote " + quoteID + " is now more lame! Current karma is " + quote.score + "." );
		}
		mods.odb.update(quote);
	}

	/**
	 * Simple parser for quote searches...
	 */
	private String getClause(final String text)
	{
		final List<String> clauses = new ArrayList<String>();
		boolean score = false; // True if score clause added.
		int pos = text.equals("") ? -1 : 0;
		int joins = 0;
		while(pos != -1)
		{
			// Avoid problems with initial zero value...
			if (pos != 0)
				pos++;

			// Initially, cut at space
			int endPos = text.indexOf(' ', pos);
			if (endPos == -1)
				endPos = text.length();
			String param = text.substring(pos, endPos);

			String user = null; // User for regexes. Used later.
			int colon = param.indexOf(':');
			final int slash = param.indexOf('/');

			// If there is a /, make sure the color is before it.
			if (slash >= 0 && colon > slash)
				colon = -1;

			boolean fiddled = false; // Set to true if param already parsed
			if (colon != -1)
			{
				final String first = param.substring(0, colon).toLowerCase();
				param = param.substring(colon + 1);

				if (param.length()==0)
					throw new ChoobError("Empty selector: " + first);

				if (param.charAt(0) == '/')
				{
					// This must be a regex with a user parameter. Save for later.
					user = mods.nick.getBestPrimaryNick( first );
					pos = pos + colon + 1;
				}
				// OK, not a regex. What else might it be?
				else if (first.equals("length"))
				{
					// Length modifier.
					if (param.length() <= 1)
						throw new ChoobError("Invalid/empty length selector.");
					final char op = param.charAt(0);
					if (op != '>' && op != '<' && op != '=')
						throw new ChoobError("Invalid length selector: " + param);
					int length;
					try
					{
						length = Integer.parseInt(param.substring(1));
					}
					catch (final NumberFormatException e)
					{
						throw new ChoobError("Invalid length selector: " + param);
					}
					clauses.add("lines " + op + " " + length);
					fiddled = true;
				}
				else if (first.equals("quoter"))
				{
					if (param.length() < 1)
						throw new ChoobError("Empty quoter nickname.");
					clauses.add("quoter = \"" + mods.odb.escapeString(param) + "\"");
					fiddled = true;
				}
				else if (first.equals("score"))
				{
					if (param.length() <= 1)
						throw new ChoobError("Invalid/empty score selector.");
					final char op = param.charAt(0);
					if (op != '>' && op != '<' && op != '=')
						throw new ChoobError("Invalid score selector: " + param);
					int value;
					try
					{
						value = Integer.parseInt(param.substring(1));
					}
					catch (final NumberFormatException e)
					{
						throw new ChoobError("Invalid score selector: " + param);
					}
					clauses.add("score " + op + " " + value);
					score = true;
					fiddled = true;
				}
				else if (first.equals("id"))
				{
					if (param.length() <= 1)
						throw new ChoobError("Invalid/empty id selector.");

					final char op = param.charAt(0);
					if (op != '>' && op != '<' && op != '=')
						throw new ChoobError("Invalid id selector: " + param);

					int value;
					try
					{
						value = Integer.parseInt(param.substring(1));
					}
					catch (final NumberFormatException e)
					{
						throw new ChoobError("Invalid id selector: " + param);
					}
					clauses.add("id " + op + " " + value);
					fiddled = true;
				}
				else if (first.equals("last"))
				{
					if (param.length() == 0)
						throw new ChoobError("Empty nickname in last: selector.");
					clauses.add("join"+joins+".quoteID = id");
					clauses.add("lines - 1 = join" + joins + ".lineNumber");
					clauses.add("join" + joins + ".nick = \"" + mods.odb.escapeString(param) + "\"");
					joins++;
					fiddled = true;
				}
				// That's all the special cases out of the way. If we're still
				// here, were's screwed...
				else
				{
					throw new ChoobError("Unknown selector type: " + first);
				}
			}

			if (fiddled)
			{
				// Do nothing
			}
			// Right. We know that we either have a quoted nickname or a regex...
			else if (param.charAt(0) == '/')
			{
				// This is a regex, then.
				// Get a matcher on th region from here to the end of the string...
				final Matcher ma = Pattern.compile("^(?:\\\\.|[^\\\\/])*?/", Pattern.CASE_INSENSITIVE).matcher(text).region(pos+1,text.length());
				if (!ma.find())
					throw new ChoobError("Regular expression has no end!");
				final int end = ma.end();
				final String regex = text.substring(pos + 1, end - 1);
				clauses.add("join"+joins+".message RLIKE \"" + mods.odb.escapeForRLike(regex) + "\"");
				if (user != null)
					clauses.add("join"+joins+".nick = \"" + mods.odb.escapeString(user) + "\"");
				clauses.add("join"+joins+".quoteID = id");
				joins++;

				pos = end-1; // In case there's a space, this is the /
			}
			else if (param.charAt(0) >= '0' && param.charAt(0) <= '9')
			{
				// Number -> Quote-by-ID
				int value;
				try
				{
					value = Integer.parseInt(param);
				}
				catch (final NumberFormatException e)
				{
					throw new ChoobError("Invalid quote number: " + param);
				}
				clauses.add("id = " + value);
			}
			else
			{
				// This is a name
				user = mods.nick.getBestPrimaryNick( param );
				clauses.add("join"+joins+".nick = \"" + mods.odb.escapeString(user) + "\"");
				clauses.add("join"+joins+".quoteID = id");
				joins++;
			}

			// Make sure we skip any double spaces...
			pos = text.indexOf(' ', pos + 1);
			while( pos < text.length() - 1 && text.charAt(pos + 1) == ' ')
			{
				pos++;
			}

			// And that we haven't hopped off the end...
			if (pos == text.length() - 1)
				pos = -1;
		}

		// All those joins hate MySQL.
		if (joins > MAXJOINS)
			throw new ChoobError("Sorry, due to MySQL being whorish, only " + MAXJOINS + " nickname or line clause(s) allowed for now.");
		else if (clauses.size() > MAXCLAUSES)
			throw new ChoobError("Sorry, due to MySQL being whorish, only " + MAXCLAUSES + " clause(s) allowed for now.");

		if (!score)
			clauses.add("score > " + (THRESHOLD - 1));

		final StringBuffer search = new StringBuffer();
		for(int i=0; i<joins; i++)
			search.append("WITH " + QuoteLine.class.getName() + " AS join" + i + " ");
		if (clauses.size() > 0)
		{
			search.append("WHERE ");
			for(int i=0; i<clauses.size(); i++)
			{
				if (i != 0)
					search.append(" AND ");
				search.append(clauses.get(i));
			}
		}
		return search.toString();
	}

	public String[] optionsUser = { "JoinMessage", "JoinQuote" };
	public String[] optionsUserDefaults = { "1", "1" };
	public boolean optionCheckUserJoinQuote( final String optionValue, final String userName ) { return _optionCheck( optionValue ); }
	public boolean optionCheckUserJoinMessage( final String optionValue, final String userName ) { return _optionCheck( optionValue ); }

	public String[] optionsGeneral = { "JoinMessage", "JoinQuote" };
	public String[] optionsGeneralDefaults = { "1", "1" };
	public boolean optionCheckGeneralJoinQuote( final String optionValue ) { return _optionCheck( optionValue ); }
	public boolean optionCheckGeneralJoinMessage( final String optionValue ) { return _optionCheck( optionValue ); }

	public String[] helpOptionJoinQuote = {
		  "Determine whether or not you recieve a quote upon joining a channel.",
		  "Set this to \"0\" to disable quotes, \"1\" to enable them (default),"
		+ " or \"<N>:<Chans>\" with <N> \"0\" or \"1\" and <Chans> a"
		+ " comma-seperated list of channels to apply <N> to. Other channels get"
		+ " the opposite.",
		  "Example: \"1:#bots\" to enable only for #bots, or \"0:#compsoc,#tech\""
		+ " to enable everywhere but #compsoc and #tech."
	};
	public String[] helpOptionJoinMessage = {
		  "Determine whether or not you recieve a message upon joining a channel.",
		  "Set this to \"0\" to disable quotes, \"1\" to enable them (default),"
		+ " or \"<N>:<Chans>\" with <N> \"0\" or \"1\" and <Chans> a"
		+ " comma-seperated list of channels to apply <N> to. Other channels get"
		+ " the opposite.",
		  "Example: \"1:#bots\" to enable only for #bots, or \"0:#compsoc,#tech\""
		+ " to enable everywhere but #compsoc and #tech."
	};

	// format: {0,1}[:<chanlist>]
	private boolean _optionCheck(final String optionValue)
	{
		final String[] parts = optionValue.split(":", -1);

		if (parts.length > 2)
			return false;

		if (!parts[0].equals("1") && !parts[0].equals("0"))
			return false;

		if (parts.length > 1)
		{
			// Make sure they're all channels.
			final String[] chans = parts[1].split(",");
			for(int i=0; i<chans.length; i++)
			{
				if (!chans[i].startsWith("#"))
					return false;
			}
			return true;
		}
		return true;
	}

	private boolean shouldMessage( final ChannelJoin ev )
	{
		return checkOption( ev, "JoinMessage" );
	}

	private boolean shouldQuote( final ChannelJoin ev )
	{
		return checkOption( ev, "JoinQuote" );
	}

	private boolean checkOption( final ChannelJoin ev, final String name )
	{
		return checkOption(ev, name, true) && checkOption(ev, name, false);
	}

	private boolean checkOption( final ChannelJoin ev, final String name, final boolean global )
	{
		try
		{
			String value;
			if (global)
				value = (String)mods.plugin.callAPI("Options", "GetGeneralOption", name, "1");
			else
				value = (String)mods.plugin.callAPI("Options", "GetUserOption", ev.getNick(), name, "1");

			final String[] parts = value.split(":", -1);
			boolean soFar;
			if (parts[0].equals("1"))
				soFar = true;
			else
				soFar = false;

			if (parts.length > 1)
			{
				// If it's in the list, same, else invert.
				final String[] chans = parts[1].split(",");
				final String lcChan = ev.getChannel().toLowerCase();
				for (final String chan : chans)
				{
					if (chan.toLowerCase().equals(lcChan))
						return soFar;
				}
				return !soFar;
			}
			// No list, so always same as first param.
			return soFar;
		}
		catch (final ChoobNoSuchCallException e)
		{
			return true;
		}
	}

	class NoJoinQuoteException extends Exception { }
	/**
	 * @return the quote string to use as a greeting when this user joins the channel
	 * @throws NoJoinQuoteException if no quote should be used when the user joins the channel.
	 */
	private String getGreetingQuoteForUserJoinEvent(final ChannelJoin ev) throws NoJoinQuoteException
	{
		if ( shouldQuote(ev) )
		{
			try
			{
				String quote = null;
				quote = apiSingleLineQuote( ev.getNick(), ev.getContext(), "score:>-1" );
				if (quote == null)
					quote = apiSingleLineQuote( ev.getNick(), ev.getContext());
				if (quote != null)
					return quote;
			} catch (final ObjectDBError e)
			{
				//We'll just have no join quote if there is an error reading the database.
				System.err.println(e.getMessage());
				e.printStackTrace();
			}
		}

		throw new NoJoinQuoteException();
	}

	class NoGreetingException extends Exception { }
	/**
	 * @return the greeting to use when this user joins the channel
	 * @throws NoGreetingException if no greeting should be used when the user joins the channel.
	 */
	private String getGreetingForUserJoinEvent(final ChannelJoin ev) throws NoGreetingException
	{
		if (!shouldMessage(ev))
			throw new NoGreetingException();

		try
		{
			return (String)mods.plugin.callAPI("Greetings", "GreetingFor", ev) + ev.getNick();
		}
		catch (final ChoobNoSuchCallException e)
		{
			return "Hello, " + ev.getNick();
		}
	}

	/**
	 * Adds apostrophes into user's nicks so that they do not get pinged by other people's joins.
	 */
	private String apostrophiseNicks(final String toApostrophise, final ChannelJoin ev)
	{
		final StringBuilder nick_r = new StringBuilder();
		final List<String> nicklist = irc.getUsersList(ev.getChannel());

		nick_r.append("(?i)\\b(?:");
		nick_r.append(nicklist.remove(0));

		for (final String nick : nicklist)
		{
			if ( nick.equals( ev.getNick() ) )
				continue;

			nick.replaceAll("([^a-zA-Z0-9_])", "\\\\$1");

			nick_r.append("|");
			nick_r.append(nick);
		}

		nick_r.append(")\\b");

		// Match the nicks, ignoring case.
		final Pattern nick_pattern = Pattern.compile(nick_r.toString());
		final Matcher nick_matcher = nick_pattern.matcher(toApostrophise);

		// Insert an apostrophe into all occurrences.
		final StringBuffer quote_sb = new StringBuffer(); //Needs to be string buffer (see sun bug 5066679)
		while (nick_matcher.find()) {
			// FIXME: This is as ugly as your mum.
			nick_matcher.appendReplacement(quote_sb, "");
			final StringBuilder new_nick = new StringBuilder(nick_matcher.group());
			/* FIXME: Why is this if() really needed?
			*
			* It breaks in real life, even without pathological nicks.
			* It could be a problem with the regex above, or getUsers().
			*/
			if (new_nick.length() > 0)
				new_nick.insert(1, '\'');
			quote_sb.append(new_nick);
		}
		nick_matcher.appendTail(quote_sb);
		return quote_sb.toString();
	}

	public synchronized  void onJoin( final ChannelJoin ev )
	{
		if (ev.getLogin().endsWith("Choob") || ev.getLogin().endsWith("choob")) // XXX
			return;

		try
		{
			final int ret = ((Integer)mods.plugin.callAPI("Flood", "IsFlooding", ev.getChannel(), Integer.valueOf(2000), Integer.valueOf(4))).intValue();
			if (ret != 0)
				return;
		}
		catch (final ChoobNoSuchCallException e)
		{
			// ignore
		}
		catch (final Throwable e)
		{
			System.err.println("Couldn't do antiflood call: " + e);
		}
			
		try
		{
			final StringBuilder greetingBuilder = new StringBuilder();
			greetingBuilder.append(getGreetingForUserJoinEvent(ev));

			try
			{
				final String greetingQuote = getGreetingQuoteForUserJoinEvent(ev);
				greetingBuilder
					.append(": \"")
					.append(apostrophiseNicks(greetingQuote,ev))
					.append("\"");
			} catch (final NoJoinQuoteException e)
			{
				greetingBuilder.append("!");
			}
			irc.sendContextMessage( ev, greetingBuilder.toString() );
		} catch (final NoGreetingException e)
		{
			return;
		}
	}

	public void onNickChange(final NickChange ev)
	{
		if (ev.getNewNick().equals(irc.getNickname()))
			updatePatterns();
	}

	private String safeHTML(final String text)
	{
		return text.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
	}

	public void webGetQuote(final PrintWriter out, final String args, final String[] from)
	{
		try
		{
			out.println("HTTP/1.0 200 OK");
			out.println("Content-Type: text/plain");
			out.println();

			final String whereClause = getClause(args);
			List<QuoteObject> quotes;
			try
			{
				quotes = mods.odb.retrieve(QuoteObject.class, "SORT BY RANDOM LIMIT (1) " + whereClause);
			}
			catch (final ObjectDBError e)
			{
				return;
			}

			if (quotes.size() == 0)
				return;

			final QuoteObject quote = quotes.get(0);
			final List<QuoteLine> lines = mods.odb.retrieve(QuoteLine.class, "WHERE quoteID = " + quote.id + " ORDER BY lineNumber");
			final Iterator<QuoteLine> l = lines.iterator();
			if (!l.hasNext())
				return;

			while(l.hasNext())
			{
				final QuoteLine line = l.next();
				if (line.isAction)
					out.println("* " + line.nick +  " " + line.message + "\n");
				else
					out.println( "<" + line.nick + "> " + line.message + "\n");
			}
		}
		catch (final Exception e)
		{
			out.println("ERROR!");
			e.printStackTrace();
		}
	}

	private static Pattern qotdAction = Pattern.compile("^(\\d+)/(\\w+)$", Pattern.CASE_INSENSITIVE);

	public void webQOTD(final PrintWriter out, final String args, final String[] from)
	{
		try
		{
			out.println("HTTP/1.0 200 OK");
			out.println("Content-Type: text/html");
			out.println();

			if (args.length() == 0)
			{
				// Show a single, random score=0 quote.
				List<QuoteObject> quotes;
				try
				{
					quotes = mods.odb.retrieve(QuoteObject.class, "WHERE score = 0 SORT BY RANDOM");
				}
				catch (final ObjectDBError e)
				{
					return;
				}
				if (quotes.size() == 0)
				{
					out.println("<P>Shock! There are no score=0 quotes.</P>");
					return;
				}

				final QuoteObject quote = quotes.get(0);
				final List<QuoteLine> lines = mods.odb.retrieve(QuoteLine.class, "WHERE quoteID = " + quote.id + " ORDER BY lineNumber");
				final Iterator<QuoteLine> l = lines.iterator();
				if (!l.hasNext())
					return;

				while(l.hasNext())
				{
					final QuoteLine line = l.next();
					if (line.isAction)
						out.println(safeHTML("* " + line.nick +  " " + line.message) + "<BR>");
					else
						out.println(safeHTML( "<" + line.nick + "> " + line.message) + "<BR>");
				}
				out.println("<P><A HREF='?" + quote.id + "/" + "leet'>Leetquote</A> <B>OR</B> <A HREF='?" + quote.id + "/" + "lame'>Lamequote</A> [this is quote ID " + quote.id + "]</P>");
				out.println("<P>(" + quotes.size() + " quotes with score=0 remaining.)</P>");
			}
			else
			{
				final Matcher qotdMatch = qotdAction.matcher(args);

				if (qotdMatch.find())
				{
					final List<QuoteObject> quotes = mods.odb.retrieve(QuoteObject.class, "WHERE id = " + qotdMatch.group(1));
					if (quotes.size() == 0)
					{
						out.println("<P>Quote ID " + qotdMatch.group(1) + " does not exist!</P>");
						return;
					}

					final QuoteObject quote = quotes.get(0);
					if (quote.score != 0)
					{
						out.println("<P>Sorry, this quote has already been leeted/lamed from 0.</P>");
						return;
					}

					if (qotdMatch.group(2).equals("leet"))
					{
						quote.score++;
						quote.up++;
						out.println("<P>Quote ID " + qotdMatch.group(1) + " leeted.</P>");
					}
					else if (qotdMatch.group(2).equals("lame"))
					{
						quote.score--;
						quote.down++;
						out.println("<P>Quote ID " + qotdMatch.group(1) + " lamed.</P>");
					}
					else
					{
						out.println("<P>I can't let you do that.</P>");
						return;
					}
					mods.odb.update(quote);
					out.println("<P><A HREF='?'>Give me another</A></P>");
				}
				else
				{
					out.println("<P>Sorry, that's just stupid!</P>");
				}
			}
		}
		catch (final Exception e)
		{
			out.println("ERROR!");
			e.printStackTrace();
		}
	}
}

