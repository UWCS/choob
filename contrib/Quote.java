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
	private Modules mods;
	private IRCInterface irc;
	public Quote( Modules mods, IRCInterface irc )
	{
		this.mods = mods;
		this.irc = irc;
	}

	public void commandCreate( Message mes ) throws ChoobException
	{
		if (!(mes instanceof ChannelEvent))
		{
			irc.sendContextReply( mes, "Sorry, this command can only be used in a channel" );
			return;
		}
		String chan = ((ChannelEvent)mes).getChannel();
		Message last = mods.history.getLastMessage( chan, mes );

		/* Comment out for testing.
		if (last.getLogin().equals(mes.getLogin()) && last.getHostname().equals(mes.getHostname()))
		{
			// Suspicious!
			irc.sendContextReply(mes, "Sorry, you can't quote yourself!");
			return;
		} //*/

		// OK, build a QuoteObject...
		final QuoteObject q = new QuoteObject();
		q.id = 0;
		q.quoter = mes.getNick();
		q.hostmask = mes.getLogin() + "@" + mes.getHostname();
		q.lines = 1;
		q.score = 0;
		q.up = 0;
		q.down = 0;
		q.time = System.currentTimeMillis();

		// QuoteLine object; quoteID will be filled in later.
		final QuoteLine ql = new QuoteLine();
		ql.id = 0;
		ql.lineNumber = 0;
		ql.nick = last.getNick();
		ql.message = last.getMessage();
		ql.isAction = (last instanceof ChannelAction);

		try
		{
			mods.odb.runTransaction( new ObjectDBTransaction() {
				public void run() throws ChoobException
			{
				save(q);
				ql.quoteID = q.id;
				save(ql);
			}});
		}
		catch (ChoobException e)
		{
			irc.sendContextReply(mes, "Could not add quote: " + e);
			return;
		}

		irc.sendContextReply( mes, "Quoted!" );
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
			System.out.println("pos is: " + pos + ", endPos is "+endPos);
			if (endPos == -1)
				endPos = text.length();
			System.out.println("endPos is now "+endPos);
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
				System.out.println("pos for regex is " + pos + ", character " + text.charAt(pos));
				Matcher ma = Pattern.compile("^(?:\\\\.|[^\\\\])*?/").matcher(text).region(pos+1,text.length());
				if (!ma.find())
					throw new ChoobException("Regular extression has no end!");
				int end = ma.end();
				String regex = text.substring(pos + 1, end - 1);
				clauses.add("join"+joins+".message RLIKE \"" + regex.replaceAll("(\\W)", "$1") + "\"");
				if (user != null)
					clauses.add("join"+joins+".nick = \"" + user.replaceAll("(\\W)", "$1") + "\"");
				clauses.add("join"+joins+".quoteID = id");
				joins++;
				System.out.println("regex was " + regex);
				pos = end-1; // In case there's a space, this is the /
				System.out.println("pos is now " + pos + ", character " + text.charAt(pos));
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
		System.out.println("Search is: " + search);
		return search.toString();
	}
}

