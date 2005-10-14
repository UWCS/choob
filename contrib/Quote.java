import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.sql.*;
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
	private static int MINLENGTH = 10; // Minimum length of a line to be quotable using simple syntax.
	private static int MINWORDS = 2; // Minimum words in a line to be quotable using simple syntax.
	private static int HISTORY = 30; // Lines of history to search.
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
		this.ignorePattern = Pattern.compile(
				"^(?:" + irc.getTriggerRegex() + ")" +
				"(?:" + IGNORE + ")", Pattern.CASE_INSENSITIVE);
		recentQuotes = new HashMap<String,List<RecentQuote>>();
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
		else if (param.toLowerCase().startsWith("action:"))
		{
			// XXX untested
			// It's an action from a nickname
			String bits[] = param.split(" +");
			if (bits.length > 2)
			{
				irc.sendContextReply(mes, "When quoting by nickname, you must supply only 1 parameter.");
				return;
			}
			bits = bits[0].split(":");
			String findNick = mods.nick.getBestPrimaryNick( bits[1] ).toLowerCase();
			for(int i=0; i<history.size(); i++)
			{
				Message line = history.get(i);
				String text = line.getMessage();
				if (!(line instanceof ChannelAction) || text.length() < MINLENGTH || text.split(" +").length < MINWORDS)
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

		// Is it a sensible size?
		if (lines.size() > MAXLINES)
		{
			irc.sendContextReply(mes, "Sorry, this quote is longer than the maximum size of " + MAXLINES + " lines.");
			return;
		}

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
		q.quoter = mods.nick.getBestPrimaryNick(mes.getNick());
		q.hostmask = (mes.getLogin() + "@" + mes.getHostname()).toLowerCase();
		q.lines = lines.size();
		q.score = 0;
		q.up = 0;
		q.down = 0;
		q.time = System.currentTimeMillis();

		// QuoteLine object; quoteID will be filled in later.

		final List quoteLines = new ArrayList(lines.size());

		try
		{
			mods.odb.runTransaction( new ObjectDBTransaction() {
				public void run() throws ChoobException
			{
				save(q);

				// Now have a quote ID!
				for(int i=0; i<lines.size(); i++)
				{
					QuoteLine ql = new QuoteLine();
					ql.quoteID = q.id;
					ql.id = 0;
					ql.lineNumber = i;
					ql.nick = lines.get(i).getNick();
					ql.message = lines.get(i).getMessage();
					ql.isAction = (lines.get(i) instanceof ChannelAction);
					save(ql);
					quoteLines.add(ql);
				}
			}});
		}
		catch (ChoobException e)
		{
			irc.sendContextReply(mes, "Could not add quote: " + e);
			return;
		}

		// Remember this quote for later...
		synchronized(recentQuotes)
		{
			String context = mes.getContext();
			List<RecentQuote> recent = recentQuotes.get(context);
			if (recent == null)
			{
				recent = new LinkedList<RecentQuote>();
				recentQuotes.put( context, recent );
			}

			RecentQuote info = new RecentQuote();
			info.quote = q;
			info.time = System.currentTimeMillis();
			info.type = 1;

			recent.add(0, info);

			while (recent.size() > RECENTLENGTH)
				recent.remove( RECENTLENGTH );
		}

		irc.sendContextReply( mes, "OK, added quote " + q.id + ": " + formatPreview(quoteLines) );
	}

	private String formatPreview(List lines)
	{
		if (lines.size() == 1)
		{
			QuoteLine line = (QuoteLine)lines.get(0);
			String text;
			if (line.message.length() > EXCERPT)
				text = line.message.substring(0, 27) + "...";
			else
				text = line.message;
			String prefix;
			if (line.isAction)
				prefix = "* " + line.nick;
			else
				prefix = "<" + line.nick + ">";
			return prefix + " " + text;
		}
		else
		{
			// last is initalised above
			QuoteLine first = (QuoteLine)lines.get(0);

			String firstText;
			if (first.isAction)
				firstText = "* " + first.nick;
			else
				firstText = "<" + first.nick + ">";
			if (first.message.length() > EXCERPT)
				firstText += " " + first.message.substring(0, 27) + "...";
			else
				firstText += " " + first.message;

			QuoteLine last = (QuoteLine)lines.get(lines.size() - 1);
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

	public void commandGet( Message mes ) throws ChoobException
	{
		String whereClause = getClause( mods.util.getParamString( mes ) );
		List quotes = mods.odb.retrieve( QuoteObject.class, "SORT BY RANDOM LIMIT (1) " + whereClause );

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

		// Remember this quote for later...
		synchronized(recentQuotes)
		{
			String context = mes.getContext();
			List<RecentQuote> recent = recentQuotes.get(context);
			if (recent == null)
			{
				recent = new LinkedList<RecentQuote>();
				recentQuotes.put( context, recent );
			}

			RecentQuote info = new RecentQuote();
			info.quote = quote;
			info.time = System.currentTimeMillis();
			info.type = 0;

			recent.add(0, info);

			while (recent.size() > RECENTLENGTH)
				recent.remove( RECENTLENGTH );
		}
	}

	public void commandCount( Message mes ) throws ChoobException
	{
		String whereClause = getClause( mods.util.getParamString( mes ) );
		List<Integer> quoteCounts = mods.odb.retrieveInt( QuoteObject.class, whereClause );

		int count = quoteCounts.size();

		if (count == 0)
			irc.sendContextReply( mes, "Sorry, no quotes match!" );
		else if (count == 1)
			irc.sendContextReply( mes, "There's just the one quote..." );
		else
			irc.sendContextReply( mes, "There's " + count + " quotes!" );
	}

	// quotekarma, quoteinfo
	public void commandInfo( Message mes ) throws ChoobException
	{
		String whereClause = getClause( mods.util.getParamString(mes) );
		List<Integer> quoteKarmas = mods.odb.retrieveInt( QuoteObject.class, "SELECT score " + whereClause );

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
			try
			{
				mods.odb.runTransaction( new ObjectDBTransaction() {
					public void run() throws ChoobException
				{
					delete(theQuote);

					// Now have a quote ID!
					for(QuoteLine line: quoteLines)
					{
						delete(line);
					}
				}});
			}
			catch (ChoobException e)
			{
				irc.sendContextReply( mes, "Could not unquote: " + e );
				return;
			}

			recent.remove(info); // So the next unquote doesn't hit it

			irc.sendContextReply( mes, "OK, unquoted quote " + quote.id + " (" + formatPreview(quoteLines) + ")." );
		}
	}

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
			irc.sendContextReply( mes, "The text " + param + " is not a valid number!" );
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
				irc.sendContextReply( mes, "Most recent quote IDs: " + output + "." );
		}
	}

	public void commandKarmaMod( Message mes ) throws ChoobException
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
			irc.sendContextReply( mes, "The text " + params.get(2) + " is not a valid number!" );
			return;
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
				if (!mods.security.hasPerm( new ChoobPermission( "quote.delete" ), mes.getNick() ))
				{
					irc.sendContextReply( mes, "Sorry, that quote is on the karma threshold. Only an admin can make it more leet!" );
					return;
				}
				quote.score++;
				quote.up++;
				irc.sendContextReply( mes, "OK, quote " + quoteID + " is now leet enough to be seen! Current karma is " + quote.score + "." );
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
			if (mods.security.hasPerm( new ChoobPermission( "quote.delete" ), mes.getNick() ))
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
					user = mods.nick.getBestPrimaryNick( first );
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
				// Number -> Quote-by-ID
				int value;
				try
				{
					value = Integer.parseInt(param);
				}
				catch (NumberFormatException e)
				{
					throw new ChoobException("Invalid quote number: " + param);
				}
				clauses.add("id = " + value);
			}
			else
			{
				// This is a name
				user = mods.nick.getBestPrimaryNick( param );
				clauses.add("join"+joins+".nick = \"" + user.replaceAll("(\\W)", "\\\\1") + "\"");
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
			throw new ChoobException("Sorry, due to MySQL being whorish, only " + MAXJOINS + " nickname or line clause(s) allowed for now.");
		else if (clauses.size() > MAXCLAUSES)
			throw new ChoobException("Sorry, due to MySQL being whorish, only " + MAXCLAUSES + " clause(s) allowed for now.");

		if (!score)
			clauses.add("score > " + (THRESHOLD - 1));

		StringBuffer search = new StringBuffer();
		for(int i=0; i<joins; i++)
			search.append("WITH AS join" + i + " " + QuoteLine.class.getName() + " ");
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

