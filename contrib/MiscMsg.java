import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.io.*;

public class MiscMsg
{
	public String[] info()
	{
		return new String[] {
			"Plugin to do miscellaneous short message commands.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private IRCInterface irc;
	private Modules mods;

	final static Random rand = new Random();

	public MiscMsg(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	private static boolean hascoin = true;

	public String[] helpCommandCT = {
		"Replies indicating whether the bot suspects your connection is up the spout."
	};
	public void commandCT( Message mes )
	{
		randomReply(mes, new String[] { "Yes, your connection is working fine.", "No, your connection seems really broken." });
	}

	public String[] helpCommandTime = {
		"Replies with the current time."
	};
	public void commandTime( Message mes )
	{
		irc.sendContextReply(mes, new SimpleDateFormat("'The time is 'HH:mm:ss'.'").format(new Date()));
	}

	public String[] helpCommandDate = {
		"Replies with the current date."
	};
	public void commandDate( Message mes )
	{
		irc.sendContextReply(mes, new SimpleDateFormat("'The date is 'd MMM yyyy'.'").format(new Date()));
	}

	public String[] helpCommandRandom = {
		"Generate a random number.",
		"[<Max>]",
		"<Max> is the optional maximum to return"
	};
	public void commandRandom( Message mes )
	{
		double max = 1;
		try
		{
			max = Double.parseDouble(mods.util.getParamString(mes));
		}
		catch (NumberFormatException e) {}
		irc.sendContextReply(mes, "Random number between 0 and " + max + " is " + new Random().nextDouble()*max + ".");
	}

	private void randomReply(Message mes, String[] replies )
	{
		irc.sendContextReply(mes, replies[(new Random()).nextInt(replies.length)]);
	}

	public String[] helpCommandFeatureRequest = { "Provides the URL from where feature requests can be made." };

	public void commandFeatureRequest( Message mes )
	{
		irc.sendContextReply(mes, "Feature requests can be made from: http://trac.uwcs.co.uk/choob/cgi-bin/trac.cgi/newticket");
	}


	public String[] helpCommandBugReport = { "Provides the URL from where bug reports can be made." };

	public void commandBugReport( Message mes )
	{
		irc.sendContextReply(mes, "Bug reports can be made from: http://trac.uwcs.co.uk/choob/cgi-bin/trac.cgi/newticket");
	}

	public String[] helpCommandFlipACoin = {
		"Flip a coin and find the result.",
		"[<Reply> or <Reply> [ or <Reply> ... ]]",
		"<Reply> is some reply to write on one of the sides of the coin (coins can have more than 2 sides!)"
	};

	public void commandFlipACoin( Message mes )
	{
		if (!hascoin)
		{
			irc.sendContextReply(mes, "I've lost my coin. :-(");
			return;
		}

		String params = mods.util.getParamString(mes);

		if (params.length() == 0)
		{
			// http://sln.fi.edu/fellows/fellow7/mar99/probability/gold_coin_flip.shtml
			if (rand.nextDouble()==0)
			{
				irc.sendContextReply(mes, "Shit, I flicked it too hard, it's gone into orbit.");
				hascoin = false;
			}
			// Wikipedia++ http://en.wikipedia.org/wiki/Coin_tossing#Physics_of_coin_flipping
			else if (rand.nextInt(6000) == 0)
				irc.sendContextReply(mes, "Edge!");
			else
				irc.sendContextReply(mes, (rand.nextBoolean() ? "Heads" : "Tails" ) + "!");

			return;
		}

		// Cut off a question mark, if any.
		if (params.charAt(params.length()-1) == '?')
			params = params.substring(0,params.length()-1);

		// Pre: String contains " or ", split on " or " or ", ";
		String[] tokens = params.split("(?:^|\\s*,\\s*|\\s+)or(?:\\s*,\\s*|\\s+|$)");
		if (tokens.length <= 1)
		{
			irc.sendContextReply(mes, "Answer to \"" + params + "\" is " + (rand.nextBoolean() ? "yes" : "no" ) + ".");
			return;
		}

		// Then split the first group on ","
		String[] tokens2 = tokens[0].split("\\s*,\\s*");

		int choice = rand.nextInt(tokens.length + tokens2.length - 1);

		// Java can't see it's guaranteed to be set.
		// Let's all laugh at its expense!
		String output = null;
		for(int i=0; i<tokens2.length; i++)
		{
			System.out.println(tokens2[i]);
			if (tokens2[i].equals(""))
			{
				irc.sendContextReply(mes, "Reply number " + (i + 1) + " is empty!");
				return;
			}
			if (i == choice)
				output = tokens2[i];
		}

		for(int i=1; i<tokens.length; i++)
		{
			System.out.println(tokens[i]);
			if (tokens[i].equals(""))
			{
				irc.sendContextReply(mes, "Reply number " + (tokens2.length + i) + " is empty!");
				return;
			}
			if (tokens2.length + i - 1 == choice)
				output = tokens[i];
		}

		irc.sendContextReply(mes, "Answer to \"" + params + "\" is " + output + ".");
	}

	public String[] helpCommand8Ball = {
		"Ask the magical 8 ball to sort out your life.",
		"<Question>",
		"<Question> is some a question for the 8 ball the think over."
	};
	public void command8Ball( Message mes )
	{
		// http://r.wesley.edwards.net/writes/JavaScript/magic8ball.js
		randomReply(mes, new String[] {"Signs point to yes.", "Yes.", "Reply hazy, try again.", "Without a doubt.", "My sources say no.", "As I see it, yes.", "You may rely on it.", "Concentrate and ask again.", "Outlook not so good.", "It is decidedly so.", "Better not tell you now.", "Very doubtful.", "Yes - definitely.", "It is certain.", "Cannot predict now.", "Most likely.", "Ask again later.", "My reply is no.", "Outlook good.", "Don't count on it." });
	}

	public void commandServerUptime(Message mes)
	{
		StringBuilder rep=new StringBuilder();

		try
		{
			String str;

			Process proc = Runtime.getRuntime().exec("uptime");

			// get its output (your input) stream

			DataInputStream in = new DataInputStream( proc.getInputStream());

			try
			{
				while ((str = in.readLine()) != null)
				{
					rep.append(str);
				}
			}
			catch (IOException e)
			{
				rep.append("IOException. ").append(e);
			}

		}
		catch (IOException e)
		{
			rep.append("IOException (2). ").append(e);
		}

		irc.sendContextReply(mes, rep.toString());
	}

	public String[] helpCommandUptime = {
		"Find out how long the bot has been running for.",
	};

	public void commandUptime( Message mes )
	{
		irc.sendContextReply(mes, "I have been up " + mods.date.timeLongStamp((new Date()).getTime() - mods.util.getStartTime(), 3) + ".");
	}

	public String[] helpCommandWeek = {
		"Displays the week number for the current date or specified date/week.",
		"[ <week> [OF TERM <term>] | RBW <week> | <date> ]",
		"<week> is the week (or room booking week [RBW]) to look up",
		"<term> is the term (1, 2 or 3 only)",
		"<date> is the date, in \"yyyy-mm-dd\", \"d/m/yyyy\" or \"d mmm yyyy\" format."
	};

	final int termStart = 1127692800; // 2005-09-26

	public void commandWeek(Message mes)
	{
		List<String> params = mods.util.getParams(mes);
		String message = mods.util.getParamString(mes);

		Matcher dateMatcher = null;
		if (params.size() >= 2) {
			dateMatcher = Pattern.compile("^(?:(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d)" +
					"|(\\d\\d?)/(\\d\\d?)/(\\d\\d\\d?\\d?)" +
					"|(\\d\\d?) (\\w\\w\\w) (\\d\\d\\d?\\d?))$", Pattern.CASE_INSENSITIVE).matcher(message);
		}

		if (params.size() <= 1) {
			Date now = new Date();
			DateFormat dayFmt = new SimpleDateFormat("EEE");

			int diff = (int)Math.floor(((int)(now.getTime() / 1000) - termStart) / 86400);
			String day = dayFmt.format(now);
			String week = diffToTermWeek(diff);

			if (week.indexOf("week") != -1) {
				irc.sendContextReply(mes, "It's " + day + ", " + diffToTermWeek(diff) + ".");
			} else {
				irc.sendContextReply(mes, "It's " + diffToTermWeek(diff) + ".");
			}
		} else if ((params.size() >= 2) && dateMatcher.matches()) {
			Date now = new Date();
			DateFormat dayFmt = new SimpleDateFormat("EEE, d MMM yyyy");

			if (dateMatcher.group(1) != null) {
				now = (new GregorianCalendar(Integer.parseInt(dateMatcher.group(1)), Integer.parseInt(dateMatcher.group(2)) - 1, Integer.parseInt(dateMatcher.group(3)), 12, 0, 0)).getTime();
			} else if (dateMatcher.group(4) != null) {
				now = (new GregorianCalendar(Integer.parseInt(dateMatcher.group(6)), Integer.parseInt(dateMatcher.group(5)) - 1, Integer.parseInt(dateMatcher.group(4)), 12, 0, 0)).getTime();
			} else if (dateMatcher.group(7) != null) {
				int month = nameToMonth(dateMatcher.group(8));
				if (month == -1) {
					irc.sendContextReply(mes, "Sorry, I can't parse that date. Please use yyyy-mm-dd, dd/mm/yyyy or dd mmm yyyy.");
					return;
				}

				now = (new GregorianCalendar(Integer.parseInt(dateMatcher.group(9)), month, Integer.parseInt(dateMatcher.group(7)), 12, 0, 0)).getTime();
			} else {
				irc.sendContextReply(mes, "Sorry, I can't parse that date. Please use yyyy-mm-dd, dd/mm/yyyy or dd mmm yyyy.");
				return;
			}

			int diff = (int)Math.floor(((int)(now.getTime() / 1000) - termStart) / 86400);
			String day = dayFmt.format(now);
			String week = diffToTermWeek(diff);

			irc.sendContextReply(mes, day + " is " + diffToTermWeek(diff) + ".");
		} else if ((isNumber(params.get(1)) && (
					((params.size() == 2)) ||
					((params.size() == 4) && params.get(2).equals("term") && isNumber(params.get(3))) ||
					((params.size() == 5) && params.get(2).equals("of") && params.get(3).equals("term") && isNumber(params.get(4)))
				)) ||
					((params.size() == 3) && params.get(1).toLowerCase().equals("rbw") && isNumber(params.get(2)))) {
			// Week number.
			int weekNum = 0;
			int num = 0;
			boolean rbw = params.get(1).toLowerCase().equals("rbw");

			if (rbw) {
				weekNum = Integer.parseInt(params.get(2));
				num = weekNum;
				if ((weekNum < 1) || (weekNum > 39)) {
					irc.sendContextReply(mes, weekNum + " isn't a valid Room Booking week number. It must be between 1 and 39, inclusive.");
					return;
				}
			} else {
				weekNum = Integer.parseInt(params.get(1));
				if (params.size() > 3) {
					if ((weekNum < 1) || (weekNum > 10)) {
						irc.sendContextReply(mes, weekNum + " isn't a valid week number. It must be between 1 and 10, inclusive.");
						return;
					}
					int term = Integer.parseInt(params.get(params.size() - 1));
					if ((term < 1) || (term > 3)) {
						irc.sendContextReply(mes, term + " isn't a valid term. It must be between 1 and 3, inclusive.");
						return;
					}
					weekNum += 10 * (term - 1);
				}
				if ((weekNum < 1) || (weekNum > 30)) {
					irc.sendContextReply(mes, weekNum + " isn't a valid week number. It must be between 1 and 30, inclusive.");
					return;
				}
				num = weekNum;
				if (weekNum > 20) weekNum += 5;
				if (weekNum > 10) weekNum += 4;
			}

			DateFormat weekFmt = new SimpleDateFormat("EEE, d MMM yyyy");

			int dateSt = termStart + ((weekNum - 1) * 86400 * 7);
			int dateEn = dateSt + (86400 * 6);

			if ((weekNum == 24) || (weekNum == 39)) {
				dateEn -= (86400 * 2);
			}

			String dateStS = weekFmt.format(new Date((long)dateSt * 1000));
			String dateEnS = weekFmt.format(new Date((long)dateEn * 1000));

			irc.sendContextReply(mes, (rbw ? "Room Booking week " : "Week ") + num + " is from " + dateStS + " to " + dateEnS + ".");
		} else {
			irc.sendContextReply(mes, "Sorry, I don't know what you mean.");
		}
	}

	boolean isNumber(String item) {
		try {
			return (item.equals((new Integer(item)).toString()));
		} catch(Exception e) {
		}
		return false;
	}

	int nameToMonth(String name) {
		name = name.toLowerCase();

		if (name.equals("jan")) return 0;
		if (name.equals("feb")) return 1;
		if (name.equals("mar")) return 2;
		if (name.equals("apr")) return 3;
		if (name.equals("may")) return 4;
		if (name.equals("jun")) return 5;
		if (name.equals("jul")) return 6;
		if (name.equals("aug")) return 7;
		if (name.equals("sep")) return 8;
		if (name.equals("oct")) return 9;
		if (name.equals("nov")) return 10;
		if (name.equals("dec")) return 11;
		return -1;
	}

	String diffToTermWeek(int diff) {
		//           Christmas             Easter
		//  <term1>  <4 weeks>  <term2>  <5 weeks>  <term3>
		// 0   -   10    -    14   -   24    -    29   -   39

		if (diff < -7 * 13) {
			return "before the current academic year";
		} else if (diff < 0) {
			return "the summer holidays";
		} else if (diff < 7 * 10 - 2) {
			int week = (int)Math.floor(diff / 7) + 1;
			int tw = week - 0;
			int rbw = week + 0;
			return "week " + week + " (week " + tw + " of term 1)";
		} else if (diff < 7 * 14 - 0) {
			int week = (int)Math.floor(diff / 7) - 9;
			int rbw = week + 10;
			return "week " + week + " of the Christmas holidays (room booking week " + rbw + ")";
		} else if (diff < 7 * 24 - 2) {
			int week = (int)Math.floor(diff / 7) - 3;
			int tw = week - 10;
			int rbw = week + 4;
			return "week " + week + " (week " + tw + " of term 2, room booking week " + rbw + ")";
		} else if (diff < 7 * 29 - 0) {
			int week = (int)Math.floor(diff / 7) - 23;
			int rbw = week + 24;
			return "week " + week + " of the Easter holidays (room booking week " + rbw + ")";
		} else if (diff < 7 * 39 - 2) {
			int week = (int)Math.floor(diff / 7) - 8;
			int tw = week - 20;
			int rbw = week + 9;
			return "week " + week + " (week " + tw + " of term 3, room booking week " + rbw + ")";
		} else if(diff < 7 * 52) {
			int week = (int)Math.floor(diff / 7) - 38;
			return "week " + week + " of the summer holidays";
		} else {
			return "after the current academic year";
		}
	}

}
