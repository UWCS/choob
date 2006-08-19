import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

public class QuoteObject
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

public class QuoteLine
{
	public int id;
	public int quoteID;
	public int lineNumber;
	public String nick;
	public String message;
	public boolean isAction;
}

public class RecentQuote
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

public class Quote
{
	private static int MINLENGTH = 7; // Minimum length of a line to be quotable using simple syntax.
	private static int MINWORDS = 2; // Minimum words in a line to be quotable using simple syntax.
	private static int HISTORY = 100; // Lines of history to search.
	private static int EXCERPT = 40; // Maximum length of excerpt text in replies to create.
	private static int MAXLINES = 10; // Maximum allowed lines in a quote.
	private static int MAXCLAUSES = 20; // Maximum allowed lines in a quote.
	private static int MAXJOINS = 6; // Maximum allowed lines in a quote.
	private static int RECENTLENGTH = 20; // Maximum length of "recent quotes" list for a context.
	private static String IGNORE = "quoteme|quote|quoten|quote.create"; // Ignore these when searching for regex quotes.
	private static int THRESHOLD = -3; // Lowest karma of displayed quote.

	private HashMap<String,List<RecentQuote>> recentQuotes;

	private Modules mods;
	private IRCInterface irc;
	private Pattern ignorePattern;

	public Quote( Modules mods, IRCInterface irc )
	{
		this.mods = mods;
		this.irc = irc;
		recentQuotes = new HashMap<String,List<RecentQuote>>();
		updatePatterns();
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
	class ScoreTracker implements Comparable
	{
		public String name;
		public int count;

		// Create a new ScoreTracker, given the name of the person.
		public ScoreTracker(String tname)
		{
			name=tname;
		}

		public void addQuote()
		{
			count++;
		}

		// Compare to another ScoreTracker.
		public int compareTo(Object o)
		{
			// Ignore the "null" case.
			if (o == null)
				return 1;

			return ((ScoreTracker)o).count - count;
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
	public void commandCreate( Message mes ) throws ChoobException
	{
		if (!(mes instanceof ChannelEvent))
		{
			irc.sendContextReply( mes, "Sorry, this command can only be used in a channel" );
			return;
		}
		String chan = ((ChannelEvent)mes).getChannel();
		List<Message> history = mods.history.getLastMessages( mes, HISTORY );

		String param = mods.util.getParamString(mes).trim();

		// Default is privmsg
		if ( param.equals("") || ((param.charAt(0) < '0' || param.charAt(0) > '9') && param.charAt(0) != '/' && param.indexOf(':') == -1 && param.indexOf(' ') == -1) )
			param = "privmsg:" + param;

		final List<Message> lines = new ArrayList<Message>();
		if (param.charAt(0) >= '0' && param.charAt(0) <= '9')
		{
			// First digit is a number. That means the rest are, too! (Or at least, we assume so.)
			String bits[] = param.split(" +");
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
				catch (NumberFormatException e)
				{
					irc.sendContextReply(mes, "Numeric offset " + bits[1] + " was not a valid integer...");
					return;
				}
			}
			try
			{
				size = Integer.parseInt(bits[0]);
			}
			catch (NumberFormatException e)
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
			String bits[] = param.split("\\s+");
			if (bits.length > 2)
			{
				irc.sendContextReply(mes, "When quoting by nickname, you must supply only 1 parameter.");
				return;
			}
			String findNick = mods.nick.getBestPrimaryNick( bits[0] ).toLowerCase();
			for(int i=0; i<history.size(); i++)
			{
				Message line = history.get(i);
				String text = line.getMessage();
				if (text.length() < MINLENGTH || text.split(" +").length < MINWORDS)
					continue;
				String guessNick = mods.nick.getBestPrimaryNick( line.getNick() );
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
			Class thing;
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
				Message line = history.get(i);
				String text = line.getMessage();
				if (!thing.isInstance(line))
					continue;

				if (findNick != null)
				{
					String guessNick = mods.nick.getBestPrimaryNick( line.getNick() );
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
			Matcher ma = Pattern.compile(token + "(?:\\s+" + token + ")?", Pattern.CASE_INSENSITIVE).matcher(param);

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
		Message last = lines.get(lines.size() - 1);
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

		final List quoteLines = new ArrayList(lines.size());

		mods.odb.runTransaction( new ObjectDBTransaction() {
			public void run()
		{
			quote.id = 0;
			save(quote);

			// Now have a quote ID!
			quoteLines.clear();
			for(int i=0; i<lines.size(); i++)
			{
				QuoteLine quoteLine = new QuoteLine();
				quoteLine.quoteID = quote.id;
				quoteLine.id = 0;
				quoteLine.lineNumber = i;
				quoteLine.nick = mods.nick.getBestPrimaryNick(lines.get(i).getNick());
				quoteLine.message = lines.get(i).getMessage();
				quoteLine.isAction = (lines.get(i) instanceof ChannelAction);
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
	public void commandAdd(Message mes)
	{
		mods.security.checkNickPerm(permissionCommandAdd, mes);

		String params = mods.util.getParamString( mes );

		String[] lines = params.split("\\s+\\|\\|\\|\\s+");

		final QuoteLine[] content = new QuoteLine[lines.length];

		for(int i=0; i<lines.length; i++)
		{
			String line = lines[i];
			String nick, text;
			boolean action = false;
			if (line.charAt(0) == '*')
			{
				int spacePos1 = line.indexOf(' ');
				if (spacePos1 == -1)
				{
					irc.sendContextReply(mes, "Line " + i + " was invalid!");
					return;
				}
				int spacePos2 = line.indexOf(' ', spacePos1 + 1);
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
				int spacePos = line.indexOf(' ');
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
				int spacePos = line.indexOf(' ');
				if (spacePos == -1)
				{
					irc.sendContextReply(mes, "Line " + i + " was invalid!");
					return;
				}
				nick = line.substring(0, spacePos);
				text = line.substring(spacePos + 1);
			}
			QuoteLine quoteLine = new QuoteLine();
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

		final List quoteLines = new ArrayList(lines.length);

		mods.odb.runTransaction( new ObjectDBTransaction() {
			public void run()
		{
			// Have to set ID etc. here in case transaction blows up.
			quote.id = 0;
			save(quote);

			// Now have a quote ID!
			quoteLines.clear();
			for(int i=0; i<content.length; i++)
			{
				QuoteLine quoteLine = content[i];
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

	private String formatPreview(List<QuoteLine> lines)
	{
		if (lines.size() == 1)
		{
			final QuoteLine line = lines.get(0);
			return formatPreviewLine(line.nick, line.message, line.isAction);
		}
		else
		{
			// last is initalised above
			QuoteLine first = lines.get(0);

			String firstText;
			if (first.isAction)
				firstText = "* " + first.nick;
			else
				firstText = "<" + first.nick + ">";
			if (first.message.length() > EXCERPT)
				firstText += " " + first.message.substring(0, 27) + "...";
			else
				firstText += " " + first.message;

			QuoteLine last = lines.get(lines.size() - 1);
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
	}

	public String[] helpCommandGet = {
		"Get a random quote from the database.",
		"[ <Clause> [ <Clause> ... ]]",
		"<Clause> is a clause to select quotes with (see Quote.UsingGet)"
	};
	public void commandGet( Message mes ) throws ChoobException
	{
		String whereClause = getClause( mods.util.getParamString( mes ) );
		List quotes;
		try
		{
			quotes = mods.odb.retrieve( QuoteObject.class, "SORT BY RANDOM LIMIT (1) " + whereClause );
		}
		catch (ObjectDBError e)
		{
			if (e.getCause() instanceof java.sql.SQLException)
			{
				irc.sendContextReply( mes, "Could not retrieve: " + e.getCause() );
				return;
			}
			else
				throw e;
		}

		if (quotes.size() == 0)
		{
			irc.sendContextReply( mes, "No quotes found!" );
			return;
		}

		QuoteObject quote = (QuoteObject)quotes.get(0);
		List lines = mods.odb.retrieve( QuoteLine.class, "WHERE quoteID = " + quote.id + " ORDER BY lineNumber");
		Iterator l = lines.iterator();
		if (!l.hasNext())
		{
			irc.sendContextReply( mes, "Found quote " + quote.id + " but it was empty!" );
			return;
		}
		while(l.hasNext())
		{
			QuoteLine line = (QuoteLine)l.next();
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
	public String apiSingleLineQuote(String nick)
	{
		return apiSingleLineQuote(nick, null);
	}
	public String apiSingleLineQuote(String nick, String context)
	{
		String whereClause = getClause(nick + " length:=1");
		List quotes = mods.odb.retrieve( QuoteObject.class, "SORT BY RANDOM LIMIT (1) " + whereClause );
		if (quotes.size() == 0)
			return null;

		QuoteObject quote = (QuoteObject)quotes.get(0);
		List <QuoteLine>lines = mods.odb.retrieve( QuoteLine.class, "WHERE quoteID = " + quote.id );

		if (lines.size() == 0)
		{
			System.err.println("Found quote " + quote.id + " but it was empty!" );
			return null;
		}

		if (context != null)
			addLastQuote(context, quote, 0);

		QuoteLine line = lines.get(0);

		if (line.isAction)
			return "/me " + line.message;
		else
			return line.message;
	}

	private void addLastQuote(String context, QuoteObject quote, int type)
	{
		synchronized(recentQuotes)
		{
			List<RecentQuote> recent = recentQuotes.get(context);
			if (recent == null)
			{
				recent = new LinkedList<RecentQuote>();
				recentQuotes.put( context, recent );
			}

			RecentQuote info = new RecentQuote();
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
	public void commandCount( Message mes ) throws ChoobException
	{
		final String whereClause = getClause( mods.util.getParamString( mes ) );
		final List<QuoteObject> quoteCounts = mods.odb.retrieve( QuoteObject.class, whereClause );
		final Set<Integer> ids = new HashSet<Integer>();

		for (QuoteObject q : quoteCounts)
			ids.add(q.id);

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
	public void commandSummary( Message mes ) throws ChoobException
	{
		String whereClause = getClause( mods.util.getParamString(mes) );
		List<QuoteObject> quoteKarmasSpam = mods.odb.retrieve( QuoteObject.class, whereClause );

		Map<Integer, QuoteObject> quoteKarmaIds = new HashMap<Integer, QuoteObject>();
		for (QuoteObject q : quoteKarmasSpam)
			quoteKarmaIds.put(q.id, q);

		List<Integer> quoteKarmas = new ArrayList<Integer>();
		for (QuoteObject q : quoteKarmaIds.values())
			quoteKarmas.add(q.score);

		int count = quoteKarmas.size();
		int nonZeroCount = 0;
		int total = 0;
		int max = 0, min = 0;
		for(Integer i: quoteKarmas)
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

		DecimalFormat format = new DecimalFormat("##0.00");

		String avKarma = format.format((double) total / (double) count);
		String avNonZeroKarma = format.format((double) total / (double) nonZeroCount);

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
	public void commandInfo( Message mes ) throws ChoobException
	{
		int quoteID = -1;
		List<String> params =  mods.util.getParams(mes);
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
		catch ( NumberFormatException e )
		{
			irc.sendContextReply( mes, "Syntax: 'Quote.Info " + helpCommandInfo[1] + "'." );
			return;
		}

		if (quoteID == -1)
		{
			synchronized(recentQuotes)
			{
				String context = mes.getContext();
				List<RecentQuote> recent = recentQuotes.get(context);
				if (recent == null || recent.size() == 0)
				{
					irc.sendContextReply( mes, "Sorry, no quotes seen from here!" );
					return;
				}

				quoteID = recent.get(0).quote.id;
			}
		}

		List<QuoteObject> quotes = mods.odb.retrieve( QuoteObject.class, "WHERE id = " + quoteID );
		if (quotes.size() == 0)
		{
			irc.sendContextReply( mes, "Quote " + quoteID + " does not exist!" );
			return;
		}

		QuoteObject quote = quotes.get(0);

		if (quote.time == 0)
			irc.sendContextReply(mes, "Quote #" + quote.id + ": Quoted by " + quote.quoter + " at Bob knows when. This is a " + quote.lines + " line quote with a karma of " + quote.score + " (" + quote.up + " up, " + quote.down + " down)." );
		else
			irc.sendContextReply(mes, "Quote #" + quote.id + ": Quoted by " + quote.quoter + " on " + (new Date(quote.time)) + ". This is a " + quote.lines + " line quote with a karma of " + quote.score + " (" + quote.up + " up, " + quote.down + " down)." );
	}

	public String[] helpCommandRemove = {
		"Remove your most recently added quote.",
	};
	public void commandRemove( Message mes ) throws ChoobException
	{
		// Quotes are stored by context...
		synchronized(recentQuotes)
		{
			String context = mes.getContext();
			List<RecentQuote> recent = recentQuotes.get(context);
			if (recent == null || recent.size() == 0)
			{
				irc.sendContextReply( mes, "Sorry, no quotes seen from here!" );
				return;
			}

			String nick = mods.nick.getBestPrimaryNick(mes.getNick()).toLowerCase();
			String hostmask = (mes.getLogin() + "@" + mes.getHostname()).toLowerCase();

			Iterator<RecentQuote> iter = recent.iterator();
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
				public void run()
			{
				delete(theQuote);

				// Now have a quote ID!
				for(QuoteLine line: quoteLines)
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
	public void commandLast( Message mes ) throws ChoobException
	{
		// Get a count...
		int count = 1;
		String param =  mods.util.getParamString(mes);
		try
		{
			if ( param.length() > 0 )
				count = Integer.parseInt( param );
		}
		catch ( NumberFormatException e )
		{
			irc.sendContextReply( mes, "'" + param + "' is not a valid integer!" );
			return;
		}
		synchronized(recentQuotes)
		{
			String context = mes.getContext();
			List<RecentQuote> recent = recentQuotes.get(context);
			if (recent == null || recent.size() == 0)
			{
				irc.sendContextReply( mes, "Sorry, no quotes seen from here!" );
				return;
			}

			// Ugly hack to avoid lack of last()...
			ListIterator<RecentQuote> iter = recent.listIterator();

			RecentQuote info = null;
			QuoteObject quote = null;
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
	public synchronized void commandKarmaMod( Message mes ) throws ChoobException
	{
		int quoteID = -1;
		boolean up = true;
		List<String> params =  mods.util.getParams(mes);
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
		catch ( NumberFormatException e )
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
				String context = mes.getContext();
				List<RecentQuote> recent = recentQuotes.get(context);
				if (recent == null || recent.size() == 0)
				{
					irc.sendContextReply( mes, "Sorry, no quotes seen from here!" );
					return;
				}

				quoteID = recent.get(0).quote.id;
			}
		}

		List quotes = mods.odb.retrieve( QuoteObject.class, "WHERE id = " + quoteID );
		if (quotes.size() == 0)
		{
			irc.sendContextReply( mes, "No such quote to " + leet + "!" );
			return;
		}

		QuoteObject quote = (QuoteObject)quotes.get(0);
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
	private String getClause(String text)
	{
		List<String> clauses = new ArrayList<String>();
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
			int slash = param.indexOf('/');

			// If there is a /, make sure the color is before it.
			if ((slash >= 0) && (colon > slash))
				colon = -1;

			boolean fiddled = false; // Set to true if param already parsed
			if (colon != -1)
			{
				String first = param.substring(0, colon).toLowerCase();
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
					char op = param.charAt(0);
					if (op != '>' && op != '<' && op != '=')
						throw new ChoobError("Invalid length selector: " + param);
					int length;
					try
					{
						length = Integer.parseInt(param.substring(1));
					}
					catch (NumberFormatException e)
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
					char op = param.charAt(0);
					if (op != '>' && op != '<' && op != '=')
						throw new ChoobError("Invalid score selector: " + param);
					int value;
					try
					{
						value = Integer.parseInt(param.substring(1));
					}
					catch (NumberFormatException e)
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

					char op = param.charAt(0);
					if (op != '>' && op != '<' && op != '=')
						throw new ChoobError("Invalid id selector: " + param);

					int value;
					try
					{
						value = Integer.parseInt(param.substring(1));
					}
					catch (NumberFormatException e)
					{
						throw new ChoobError("Invalid id selector: " + param);
					}
					clauses.add("id " + op + " " + value);
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
			{ } // Do nothing
			// Right. We know that we either have a quoted nickname or a regex...
			else if (param.charAt(0) == '/')
			{
				// This is a regex, then.
				// Get a matcher on th region from here to the end of the string...
				Matcher ma = Pattern.compile("^(?:\\\\.|[^\\\\/])*?/", Pattern.CASE_INSENSITIVE).matcher(text).region(pos+1,text.length());
				if (!ma.find())
					throw new ChoobError("Regular expression has no end!");
				int end = ma.end();
				String regex = text.substring(pos + 1, end - 1);
				clauses.add("join"+joins+".message RLIKE \"" + mods.odb.escapeString(regex) + "\"");
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
				catch (NumberFormatException e)
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

		StringBuffer search = new StringBuffer();
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
	public boolean optionCheckUserJoinQuote( String optionValue, String userName ) { return _optionCheck( optionValue ); }
	public boolean optionCheckUserJoinMessage( String optionValue, String userName ) { return _optionCheck( optionValue ); }

	public String[] optionsGeneral = { "JoinMessage", "JoinQuote" };
	public String[] optionsGeneralDefaults = { "1", "1" };
	public boolean optionCheckGeneralJoinQuote( String optionValue ) { return _optionCheck( optionValue ); }
	public boolean optionCheckGeneralJoinMessage( String optionValue ) { return _optionCheck( optionValue ); }

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
	private boolean _optionCheck(String optionValue)
	{
		String[] parts = optionValue.split(":", -1);

		if (parts.length > 2)
			return false;

		if (!parts[0].equals("1") && !parts[0].equals("0"))
			return false;

		if (parts.length > 1)
		{
			// Make sure they're all channels.
			String[] chans = parts[1].split(",");
			for(int i=0; i<chans.length; i++)
			{
				if (!chans[i].startsWith("#"))
					return false;
			}
			return true;
		}
		else
			return true;
	}

	private boolean shouldMessage( ChannelJoin ev )
	{
		return checkOption( ev, "JoinMessage" );
	}

	private boolean shouldQuote( ChannelJoin ev )
	{
		return checkOption( ev, "JoinQuote" );
	}

	private boolean checkOption( ChannelJoin ev, String name )
	{
		return checkOption(ev, name, true) && checkOption(ev, name, false);
	}

	private boolean checkOption( ChannelJoin ev, String name, boolean global )
	{
		try
		{
			String value;
			if (global)
				value = (String)mods.plugin.callAPI("Options", "GetGeneralOption", name, "1");
			else
				value = (String)mods.plugin.callAPI("Options", "GetUserOption", ev.getNick(), name, "1");

			String[] parts = value.split(":", -1);
			boolean soFar;
			if (parts[0].equals("1"))
				soFar = true;
			else
				soFar = false;

			if (parts.length > 1)
			{
				// If it's in the list, same, else invert.
				String[] chans = parts[1].split(",");
				String lcChan = ev.getChannel().toLowerCase();
				for(int i=0; i<chans.length; i++)
				{
					if (chans[i].toLowerCase().equals(lcChan))
						return soFar;
				}
				return !soFar;
			}
			else
				// No list, so always same as first param.
				return soFar;
		}
		catch (ChoobNoSuchCallException e)
		{
			return true;
		}
	}

	public synchronized  void onJoin( ChannelJoin ev, Modules mods, IRCInterface irc )
	{
		if (ev.getLogin().equalsIgnoreCase("Choob")) // XXX
			return;

		if (shouldMessage(ev))
		{
			String quote;
			if ( shouldQuote(ev) )
				quote = apiSingleLineQuote( ev.getNick(), ev.getContext() );
			else
				quote = null;

			String greeting;
			try
			{
				greeting = (String)mods.plugin.callAPI("Greetings", "GreetingFor", ev);
			}
			catch (ChoobNoSuchCallException e)
			{
				greeting = "Hello, ";
			}

			if (quote == null)
				irc.sendContextMessage( ev, greeting + ev.getNick() + "!");
			else
			{
				for (String nick : irc.getUsers(ev.getChannel()))
				{
					if ( nick.equals( ev.getNick() ) )
						continue;

					quote = quote.replaceAll(
						nick.replaceAll("([^a-zA-Z0-9_])", "\\\\$1"),
						nick.replaceAll("([a-zA-Z])([^, ]+)","$1'$2")
					);
				}
				irc.sendContextMessage( ev, greeting + ev.getNick() + ": \"" + quote + "\"");
			}
		}
	}
	
	public void onNickChange(NickChange ev, Modules mods, IRCInterface irc)
	{
		if (ev.getNewNick().equals(irc.getNickname()))
			updatePatterns();
	}
}

