import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

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

public class Quote
{
	private static int MINLENGTH = 10; // Minimum length of a line to be quotable using simple syntax.
	private static int MINWORDS = 2; // Minimum words in a line to be quotable using simple syntax.
	private static int HISTORY = 30; // Lines of history to search.
	private static int EXCERPT = 40; // Maximum length of excerpt text in replies to create.
	private static String IGNORE = "quote|quoten|quote.create"; // Ignore these when searching for regex quotes.

	private Modules mods;
	private IRCInterface irc;
	private Pattern ignorePattern;
	public Quote( Modules mods, IRCInterface irc )
	{
		this.mods = mods;
		this.irc = irc;
		this.ignorePattern = Pattern.compile(
				"^(?:" + irc.getTriggerRegex() + ")" +
				"(?:" + IGNORE + ")", Pattern.CASE_INSENSITIVE);
	}

	public void commandCreate( Message mes ) throws ChoobException
	{
		if (!(mes instanceof ChannelEvent))
		{
			irc.sendContextReply( mes, "Sorry, this command can only be used in a channel" );
			return;
		}
		String chan = ((ChannelEvent)mes).getChannel();
		List<Message> history = mods.history.getLastMessages( mes, HISTORY );

		String param = mods.util.getParamString(mes);

		final List<Message> lines = new ArrayList<Message>();
		if ( param.equals("") )
		{
			if (history.size() == 0)
			{
				irc.sendContextReply(mes, "Can't quote -- memory like a seive!");
				return;
			}
			lines.add(history.get(0));
		}
		else if (param.charAt(0) >= '0' && param.charAt(0) <= '9')
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
		else if (param.charAt(0) != '/' && param.indexOf(':') == -1)
		{
			// It's a nickname.
			String bits[] = param.split(" +");
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
		else
		{
			// Final case: Regex quoting.
			// Matches anything of the form [NICK:]/REGEX/ [[NICK:]/REGEX/]
			Matcher ma = Pattern.compile("(?:([^\\s:]+):)?/((?:\\\\.|[^\\\\/])+)/(?:\\s+(?:([^\\s:]+):)?/((?:\\\\.|[^\\\\/])+)/)?").matcher(param);
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
				startRegex = ".*" + ma.group(2) + ".*";
				endNick = ma.group(3);
				endRegex = ".*" + ma.group(4) + ".*";
			}
			else
			{
				startNick = null;
				startRegex = null;
				endNick = ma.group(1);
				endRegex = ".*" + ma.group(2) + ".*";
			}

			if (startNick != null)
				startNick = mods.nick.getBestPrimaryNick( startNick ).toLowerCase();
			if (endNick != null)
				endNick = mods.nick.getBestPrimaryNick( endNick ).toLowerCase();

			// OK, do the match!
			int endIndex = -1, startIndex = -1;
			for(int i=0; i<history.size(); i++)
			{
				Message line = history.get(i);
				String nick = mods.nick.getBestPrimaryNick( line.getNick() ).toLowerCase();
				if ( endRegex != null )
				{
					// Not matched the end yet

					// For this one, we must avoid triggering on quote commands.
					if (ignorePattern.matcher(line.getMessage()).find())
						continue;

					if ((endNick == null || endNick.equals(nick))
							&& line.getMessage().matches(endRegex))
					{
						// But have now...
						endRegex = null;
						endIndex = i;
						if ( startRegex == null )
						{
							startIndex = i;
							break;
						}
					}
				}
				else
				{
					// Matched the end; looking for the start.
					if ((startNick == null || startNick.equals(nick))
							&& line.getMessage().matches(startRegex))
					{
						startIndex = i;
						break;
					}
				}
			}
			if (startIndex == -1 || endIndex == -1)
			{
				irc.sendContextReply(mes, "Sorry, no quote found!");
				return;
			}
			for(int i=startIndex; i>=endIndex; i--)
				lines.add(history.get(i));
		}

		// Have some lines.

		// Check for people suspiciously quoting themselves.
		// For now, that's just if they are the final line in the quote.
		Message last = lines.get(lines.size() - 1);
		/*/ Remove the first slash to comment me out.
		if (last.getLogin().compareToIgnoreCase(mes.getLogin()) == 0
				&& last.getHostname().compareToIgnoreCase(mes.getHostname()) == 0)
		{
			// Suspicious!
			irc.sendContextReply(mes, "Sorry, no quoting yorself!");
			return;
		} //*/

		// OK, build a QuoteObject...
		final QuoteObject q = new QuoteObject();
		q.id = 0;
		q.quoter = mes.getNick();
		q.hostmask = mes.getLogin() + "@" + mes.getHostname();
		q.lines = lines.size();
		q.score = 0;
		q.up = 0;
		q.down = 0;
		q.time = System.currentTimeMillis();

		// QuoteLine object; quoteID will be filled in later.

		try
		{
			mods.odb.runTransaction( new ObjectDBTransaction() {
				public void run() throws ChoobException
			{
				save(q);

				// Now have a quote ID!
				QuoteLine ql = new QuoteLine();
				ql.quoteID = q.id;
				for(int i=0; i<lines.size(); i++)
				{
					ql.id = 0;
					ql.lineNumber = i;
					ql.nick = lines.get(i).getNick();
					ql.message = lines.get(i).getMessage();
					ql.isAction = (lines.get(i) instanceof ChannelAction);
					save(ql);
				}
			}});
		}
		catch (ChoobException e)
		{
			irc.sendContextReply(mes, "Could not add quote: " + e);
			return;
		}

		if (lines.size() == 1)
		{
			Message line = lines.get(0);
			String text;
			if (line.getMessage().length() > EXCERPT)
				text = line.getMessage().substring(0, 27) + "...";
			else
				text = line.getMessage();
			String prefix;
			if (line instanceof ChannelAction)
				prefix = "* " + line.getNick();
			else
				prefix = "<" + line.getNick() + ">";
			irc.sendContextReply( mes, "Quoted this: " + prefix + " " + text );
		}
		else
		{
			// last is initalised above
			Message first = lines.get(0);

			String firstText;
			if (first instanceof ChannelAction)
				firstText = "* " + first.getNick();
			else
				firstText = "<" + first.getNick() + ">";
			if (first.getMessage().length() > EXCERPT)
				firstText += " " + first.getMessage().substring(0, 27) + "...";
			else
				firstText += " " + first.getMessage();

			String lastText;
			if (last instanceof ChannelAction)
				lastText = "* " + last.getNick();
			else
				lastText = "<" + last.getNick() + ">";
			if (last.getMessage().length() > EXCERPT)
				lastText += " " + last.getMessage().substring(0, 27) + "...";
			else
				lastText += " " + last.getMessage();

			irc.sendContextReply( mes, "Quoted this: " + firstText + " -> " + lastText );
		}
	}

	public void commandGet( Message mes ) throws ChoobException
	{
		String whereClause = getClause( mods.util.getParamString( mes ) );
		List quotes = mods.odb.retrieve( QuoteObject.class, whereClause );

		if (quotes.size() == 0)
		{
			irc.sendContextReply( mes, "No quotes found!" );
			return;
		}

		QuoteObject quote = (QuoteObject)quotes.get(0);
		List lines = mods.odb.retrieve( QuoteLine.class, "WHERE quoteID = " + quote.id );
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
	}

	/**
	 * Simple parser for quote searches...
	 */
	private String getClause(String text) throws ChoobException
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
			boolean fiddled = false; // Set to true if param already parsed
			if (colon != -1)
			{
				String first = param.substring(0, colon).toLowerCase();
				param = param.substring(colon + 1);

				if (param.charAt(0) == '/')
				{
					// This must be a regex with a user parameter. Save for later.
					user = first;
					pos = pos + colon + 1;
				}
				// OK, not a regex. What else might it be?
				else if (first.equals("length"))
				{
					// Length modifier.
					if (param.length() <= 1)
						throw new ChoobException("Invalid/empty length selector.");
					char op = param.charAt(0);
					if (op != '>' && op != '<' && op != '=')
						throw new ChoobException("Invalid length selector: " + param);
					int length;
					try
					{
						length = Integer.parseInt(param.substring(1));
					}
					catch (NumberFormatException e)
					{
						throw new ChoobException("Invalid length selector: " + param);
					}
					clauses.add("lines " + op + " " + length);
					fiddled = true;
				}
				else if (first.equals("quoter"))
				{
					if (param.length() < 1)
						throw new ChoobException("Empty quoter nickname.");
					clauses.add("quoter = \"" + param.replaceAll("(\\W)", "\\\\1") + "\"");
					fiddled = true;
				}
				else if (first.equals("score"))
				{
					if (param.length() <= 1)
						throw new ChoobException("Invalid/empty score selector.");
					char op = param.charAt(0);
					if (op != '>' && op != '<' && op != '=')
						throw new ChoobException("Invalid score selector: " + param);
					int value;
					try
					{
						value = Integer.parseInt(param.substring(1));
					}
					catch (NumberFormatException e)
					{
						throw new ChoobException("Invalid score selector: " + param);
					}
					clauses.add("score " + op + " " + value);
					score = true;
					fiddled = true;
				}
				// That's all the special cases out of the way. If we're still
				// here, were's screwed...
				else
				{
					throw new ChoobException("Unknown selector type: " + first);
				}
			}

			if (fiddled)
			{ } // Do nothing
			// Right. We know that we either have a quoted nickname or a regex...
			else if (param.charAt(0) == '/')
			{
				// This is a regex, then.
				// Get a matcher on th region from here to the end of the string...
				Matcher ma = Pattern.compile("^(?:\\\\.|[^\\\\/])*?/").matcher(text).region(pos+1,text.length());
				if (!ma.find())
					throw new ChoobException("Regular expression has no end!");
				int end = ma.end();
				String regex = text.substring(pos + 1, end - 1);
				clauses.add("join"+joins+".message RLIKE \"" + regex.replaceAll("(\\W)", "$1") + "\"");
				if (user != null)
					clauses.add("join"+joins+".nick = \"" + user.replaceAll("(\\W)", "$1") + "\"");
				clauses.add("join"+joins+".quoteID = id");
				joins++;
				pos = end-1; // In case there's a space, this is the /
			}
			else if (param.charAt(0) >= '0' && param.charAt(0) <= '9')
			{
				int value;
				try
				{
					value = Integer.parseInt(param);
				}
				catch (NumberFormatException e)
				{
					throw new ChoobException("Invalid score selector: " + param);
				}
				clauses.add("id = " + value);
			}
			else
			{
				// This is a name
				clauses.add("join"+joins+".nick = \"" + param.replaceAll("(\\W)", "\\\\1") + "\"");
				clauses.add("join"+joins+".quoteID = id");
				joins++;
			}
			pos = text.indexOf(' ', pos + 1);
		}

		if (!score)
			clauses.add("score > -3");

		StringBuffer search = new StringBuffer();
		for(int i=0; i<joins; i++)
			search.append("WITH AS join" + i + " " + QuoteLine.class.getName() + " ");
		search.append("SORT BY RANDOM ");
		search.append("LIMIT (1) ");
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
}

