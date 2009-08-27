import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.ProduceMime;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

@Path("MiscMsg")
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

	private final IRCInterface irc;
	private final Modules mods;

	final static Random rand = new Random();

	public MiscMsg(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	private static boolean hascoin = true;

	public String[] helpCommandCT = {
		"Replies indicating whether the bot suspects your connection is up the spout."
	};
	public String commandCT( final String mes )
	{
		return randomReply(new String[] { "Yes, your connection is working fine.", "No, your connection seems really broken." });
	}

	/**
	 * Example usage of Jersey plugin.
	 */
	@GET
	@ProduceMime("text/plain")
	@Path("ct")
	public String ct()
	{
		return new String[] { "Yes, your connection is working fine.", "No, your connection seems really broken." }[new Random().nextInt(2)];
	}

	public String[] helpCommandTime = {
		"Replies with the current time."
	};
	public String commandTime( final String mes )
	{
		return new SimpleDateFormat("'The time is 'HH:mm:ss'.'").format(new Date());
	}

	public String[] helpCommandDate = {
		"Replies with the current date."
	};
	public String commandDate( final String mes )
	{
		return new SimpleDateFormat("'The date is 'd MMM yyyy'.'").format(new Date());
	}

	public String[] helpCommandRandom = {
		"Generate a random number.",
		"[<Max>]",
		"<Max> is the optional maximum to return"
	};
	public String commandRandom( final String mes )
	{
		double max = 1;
		try
		{
			max = Double.parseDouble(mes);
		}
		catch (final NumberFormatException e)
		{
			// Assume 1.
		}
		return "Random number between 0 and " + max + " is " + Math.random()*max + ".";
	}

	private String randomReply(final String[] replies)
	{
		return randomReply(replies, "", "");
	}

	private String randomReply(final String[] replies, String prefix, String suffix)
	{
		return prefix + replies[new Random().nextInt(replies.length)] + suffix;
	}

	public String[] helpCommandFeatureRequest = { "Provides the URL from where feature requests can be made." };

	public void commandFeatureRequest( final Message mes )
	{
		irc.sendContextReply(mes, "Feature requests can be made from: http://trac.uwcs.co.uk/choob/cgi-bin/trac.cgi/newticket");
	}


	public String[] helpCommandBugReport = { "Provides the URL from where bug reports can be made." };

	public void commandBugReport( final Message mes )
	{
		irc.sendContextReply(mes, "Bug reports can be made from: http://trac.uwcs.co.uk/choob/cgi-bin/trac.cgi/newticket");
	}

	public String[] helpCommandFlipACoin = {
		"Flip a coin and find the result.",
		"[<Reply> or <Reply> [ or <Reply> ... ]]",
		"<Reply> is some reply to write on one of the sides of the coin (coins can have more than 2 sides!)"
	};

	public String commandFlipACoin( final String mes )
	{
		if (!hascoin)
			return "I've lost my coin. :-(";

		String params = mes;

		if (params.length() == 0)
		{
			// http://sln.fi.edu/fellows/fellow7/mar99/probability/gold_coin_flip.shtml
			if (rand.nextDouble()==0)
			{
				hascoin = false;
				return "Shit, I flicked it too hard, it's gone into orbit.";
			}
			// Wikipedia++ http://en.wikipedia.org/wiki/Coin_tossing#Physics_of_coin_flipping
			else if (rand.nextInt(6000) == 0)
				return "Edge!";
			else
				return (rand.nextBoolean() ? "Heads" : "Tails" ) + "!";
		}

		// Cut off a question mark, if any.
		if (params.charAt(params.length()-1) == '?')
			params = params.substring(0,params.length()-1);

		// Pre: String contains " or ", split on " or " or ", ";
		final String[] tokens = params.split("(?:^|\\s*,\\s*|\\s+)or(?:\\s*,\\s*|\\s+|$)");
		if (tokens.length <= 1)
			return "My answer is " + (rand.nextBoolean() ? "yes" : "no" ) + ".";

		// Then split the first group on ","
		final String[] tokens2 = tokens[0].split("\\s*,\\s*");

		final int choice = rand.nextInt(tokens.length + tokens2.length - 1);

		// Java can't see it's guaranteed to be set.
		// Let's all laugh at its expense!
		String output = null;
		for(int i=0; i<tokens2.length; i++)
		{
			if (tokens2[i].equals(""))
				return "Reply number " + (i + 1) + " is empty!";
			if (i == choice)
				output = tokens2[i];
		}

		for(int i=1; i<tokens.length; i++)
		{
			if (tokens[i].equals(""))
				return "Reply number " + (tokens2.length + i) + " is empty!";
			if (tokens2.length + i - 1 == choice)
				output = tokens[i];
		}

		return "My answer is " + output + ".";
	}

	public String[] helpCommand8Ball = {
		"Ask the magical 8 ball to sort out your life.",
		"<Question>",
		"<Question> is some a question for the 8 ball the think over."
	};
	public String command8Ball(final String mes)
	{
		// http://r.wesley.edwards.net/writes/JavaScript/magic8ball.js
		return randomReply(new String[] { "Signs point to yes.", "Yes.", "Reply hazy, try again.",
				"Without a doubt.", "My sources say no.", "As I see it, yes.",
				"You may rely on it.", "Concentrate and ask again.", "Outlook not so good.",
				"It is decidedly so.", "Better not tell you now.", "Very doubtful.",
				"Yes - definitely.", "It is certain.", "Cannot predict now.", "Most likely.",
				"Ask again later.", "My reply is no.", "Outlook good.", "Don't count on it." });
	}

	public String[] helpCommandTarot = { "Draw a card from the tarot pack.", };

	public String commandTarot(final String mes)
	{
		// http://en.wikipedia.org/wiki/Major_Arcana
		return randomReply(new String[] { "The Fool", "The Magician", "The High Priestess",
				"The Empress", "The Emperor", "The Hierophant", "The Pope", "The Lovers",
				"The Chariot", "Strength", "The Hermit", "Wheel of Fortune", "Justice",
				"The Hanged Man", "Death", "Temperance", "The Devil", "The Tower", "The Star",
				"The Moon", "The Sun", "Judgment", "The World" }, "You drew ", ".");
	}


	public String commandDiscordianDate(final String mes)
	{
		return shellExec("ddate");
	}

	public String commandServerUptime(final String mes)
	{
		return shellExec("uptime");
	}

	private String shellExec(final String command)
	{
		final StringBuilder rep=new StringBuilder();

		try
		{
			String str;

			final Process proc = Runtime.getRuntime().exec(command);

			// There's a nefarious reason why this is here.
			try { proc.waitFor(); } catch (final InterruptedException e1)
			{
				// If anything went wrong, the next stage'll bomb properly.
			}

			final BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));

			try
			{
				while ((str = in.readLine()) != null)
					rep.append(str);
			}
			catch (final IOException e)
			{
				rep.append("IOException. ").append(e);
			}

		}
		catch (final IOException e)
		{
			rep.append("IOException (2). ").append(e);
		}

		return rep.toString();
	}

	public String[] helpCommandUptime = {
		"Find out how long the bot has been running for.",
	};

	public String commandUptime( final String mes )
	{
		return "I have been up " + mods.date.timeLongStamp(new Date().getTime() - mods.util.getStartTime(), 3) + ".";
	}

	public String[] helpCommandWeek = {
		"Displays the week number for the current date or specified date/week.",
		"[ <week> [OF TERM <term>] | RBW <week> | <date> ]",
		"<week> is the week (or room booking week [RBW]) to look up",
		"<term> is the term (1, 2 or 3 only)",
		"<date> is the date, in \"yyyy-mm-dd\", \"d/m/yyyy\" or \"d mmm yyyy\" format."
	};

	final int[] termStarts = {
			1064793600, // 2003-09-29
			1096243200, // 2004-09-27
			1127692800, // 2005-09-26
			1159747200, // 2006-10-02
			1191196800, // 2007-10-01
			1222646400, // 2008-09-29
			1254096000, // 2009-09-28
			1286150400, // 2010-10-04
			1317600000, // 2011-10-03
			1349049600, // 2012-10-01
			1380499200, // 2013-09-30
			1411948800, // 2014-09-29
			1444089600 // 2015-10-06
		};

	@SuppressWarnings("null")
	public String commandWeek(final String mes)
	{
		final List<String> params = mods.util.getParams(mes);
		final String message = mes;

		Matcher dateMatcher = null;
		if (params.size() >= 2) {
			dateMatcher = Pattern.compile("^(?:(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d)" +
					"|(\\d\\d?)/(\\d\\d?)/(\\d\\d?\\d?\\d?)" +
					"|(\\d\\d?) (\\w\\w\\w) (\\d\\d?\\d?\\d?))$", Pattern.CASE_INSENSITIVE).matcher(message);
		}

		if (params.size() <= 1) {
			final Date now = new Date();
			final DateFormat dayFmt = new SimpleDateFormat("EEE");

			final int diff = (int)Math.floor(getTimeRelativeToTerm((int)(now.getTime() / 1000)) / 86400);
			final String day = dayFmt.format(now);
			final String week = diffToTermWeek(diff);

			if (week.indexOf("week") != -1) {
				return "It's " + day + ", " + diffToTermWeek(diff) + ".";
			}
			return "It's " + diffToTermWeek(diff) + ".";
		} else if (params.size() >= 2 && dateMatcher.matches()) {
			Date now = new Date();
			final DateFormat dayFmt = new SimpleDateFormat("EEE, d MMM yyyy");

			if (dateMatcher.group(1) != null) {
				now = new GregorianCalendar(Integer.parseInt(dateMatcher.group(1)), Integer.parseInt(dateMatcher.group(2)) - 1, Integer.parseInt(dateMatcher.group(3)), 12, 0, 0).getTime();
			} else if (dateMatcher.group(4) != null) {
				int year = Integer.parseInt(dateMatcher.group(6));
				if (year < 80)
					year += 2000;
				if (year < 100)
					year += 1900;

				now = new GregorianCalendar(year, Integer.parseInt(dateMatcher.group(5)) - 1, Integer.parseInt(dateMatcher.group(4)), 12, 0, 0).getTime();
			} else if (dateMatcher.group(7) != null) {
				final int month = nameToMonth(dateMatcher.group(8));
				if (month == -1) {
					return "Sorry, I can't parse that date. Please use yyyy-mm-dd, dd/mm/yyyy or dd mmm yyyy.";
				}

				int year = Integer.parseInt(dateMatcher.group(9));
				if (year < 80)
					year += 2000;
				if (year < 100)
					year += 1900;

				now = new GregorianCalendar(year, month, Integer.parseInt(dateMatcher.group(7)), 12, 0, 0).getTime();
			} else {
				return "Sorry, I can't parse that date. Please use yyyy-mm-dd, dd/mm/yyyy or dd mmm yyyy.";
			}

			final int diff = (int)Math.floor(getTimeRelativeToTerm((int)(now.getTime() / 1000)) / 86400);
			final String day = dayFmt.format(now);

			return day + " is " + diffToTermWeek(diff) + ".";
		} else if (isNumber(params.get(1)) && (
					params.size() == 2 ||
					params.size() == 4 && params.get(2).equals("term") && isNumber(params.get(3)) ||
					params.size() == 5 && params.get(2).equals("of") && params.get(3).equals("term") && isNumber(params.get(4))
				) ||
					params.size() == 3 && params.get(1).toLowerCase().equals("rbw") && isNumber(params.get(2))) {
			// Week number.
			int weekNum = 0;
			int num = 0;
			final boolean rbw = params.get(1).toLowerCase().equals("rbw");

			if (rbw) {
				weekNum = Integer.parseInt(params.get(2));
				num = weekNum;
				if (weekNum < 1 || weekNum > 39) {
					return weekNum + " isn't a valid Room Booking week number. It must be between 1 and 39, inclusive.";
				}
			} else {
				weekNum = Integer.parseInt(params.get(1));
				if (params.size() > 3) {
					if (weekNum < 1 || weekNum > 10) {
						return weekNum + " isn't a valid week number. It must be between 1 and 10, inclusive.";
					}
					final int term = Integer.parseInt(params.get(params.size() - 1));
					if (term < 1 || term > 3) {
						return term + " isn't a valid term. It must be between 1 and 3, inclusive.";
					}
					weekNum += 10 * (term - 1);
				}
				if (weekNum < 1 || weekNum > 30) {
					return weekNum + " isn't a valid week number. It must be between 1 and 30, inclusive.";
				}
				num = weekNum;
				if (weekNum > 20) weekNum += 5;
				if (weekNum > 10) weekNum += 4;
			}

			final DateFormat weekFmt = new SimpleDateFormat("EEE, d MMM yyyy");

			final int dateSt = getCurrentTermStart() + (weekNum - 1) * 86400 * 7;
			int dateEn = dateSt + 86400 * 6;

			if (weekNum == 24 || weekNum == 39) {
				dateEn -= 86400 * 2;
			}

			final String dateStS = weekFmt.format(new Date((long)dateSt * 1000));
			final String dateEnS = weekFmt.format(new Date((long)dateEn * 1000));

			return (rbw ? "Room Booking week " : "Week ") + num + " is from " + dateStS + " to " + dateEnS + ".";
		} else {
			return "Sorry, I don't know what you mean.";
		}
	}

	boolean isNumber(final String item) {
		try {
			return item.equals(Integer.valueOf(item).toString());
		} catch(final Exception e) {
			return false;
		}
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

	int getCurrentTermStart() {
		final int time = (int)(new Date().getTime() / 1000);

		for (final int termStart : termStarts)
		{
			if (termStart + 39 * 7 * 86400 > time) {
				return termStart;
			}
		}

		return time;
	}

	int getTimeRelativeToTerm(final int time) {
		for (int i = 1; i < termStarts.length; i++) {
			if (termStarts[i] > time) {
				return time - termStarts[i - 1];
			}
		}

		return time - termStarts[termStarts.length - 1];
	}

	String diffToTermWeek(final int diff) {
		//           Christmas             Easter
		//  <term1>  <4 weeks>  <term2>  <5 weeks>  <term3>
		// 0   -   10    -    14   -   24    -    29   -   39

		if (diff < -7 * 13) {
			return "beyond the academic year data";
		} else if (diff < 0) {
			return "the summer holidays";
		} else if (diff < 7 * 10 - 2) {
			final int week = (int)Math.floor(diff / 7) + 1;
			final int tw = week - 0;
			return "week " + week + " (week " + tw + " of term 1)";
		} else if (diff < 7 * 14 - 0) {
			final int week = (int)Math.floor(diff / 7) - 9;
			final int rbw = week + 10;
			return "week " + week + " of the Christmas holidays (room booking week " + rbw + ")";
		} else if (diff < 7 * 24 - 2) {
			final int week = (int)Math.floor(diff / 7) - 3;
			final int tw = week - 10;
			final int rbw = week + 4;
			return "week " + week + " (week " + tw + " of term 2, room booking week " + rbw + ")";
		} else if (diff < 7 * 29 - 0) {
			final int week = (int)Math.floor(diff / 7) - 23;
			final int rbw = week + 24;
			return "week " + week + " of the Easter holidays (room booking week " + rbw + ")";
		} else if (diff < 7 * 39 - 2) {
			final int week = (int)Math.floor(diff / 7) - 8;
			final int tw = week - 20;
			final int rbw = week + 9;
			return "week " + week + " (week " + tw + " of term 3, room booking week " + rbw + ")";
		} else if(diff < 7 * 53) {
			final int week = (int)Math.floor(diff / 7) - 38;
			return "week " + week + " of the summer holidays";
		} else {
			return "beyond the academic year data";
		}
	}


	public String[] helpCommandExchange = {
		"Converts a monetary amount from one currency to another.",
		"<from> <to> [amount]",
		"<from> is the three-letter code of the currency to convert from.",
		"<to> is the three-letter code of the currency to convert to.",
		"[amount] is the amount to convert (defaults to 1).",
	};

	public String commandExchange(final String mes)
	{
		String[] command = mes.replaceAll("[\\(\\),]+", " ").trim().split(" +");
		if (command.length == 2)
			command = new String[] { command[0], command[1], "1" };
		if (command.length != 3)
			return "Incorrect number of arguments specified.";

		command[0]=command[0].toUpperCase();
		command[1]=command[1].toUpperCase();

		URL url;
		try
		{
			url = new URL("http://finance.google.com/finance/converter?a=" + URLEncoder.encode(command[2], "UTF-8") + "&from=" + URLEncoder.encode(command[0], "UTF-8") + "&to=" + URLEncoder.encode(command[1], "UTF-8"));
		}
		catch (final UnsupportedEncodingException e)
		{
			return "Unexpected exception generating url.";
		}
		catch (final MalformedURLException e)
		{
			return "Error, malformed url generated.";
		}

		String s;
		try
		{
			s = mods.scrape.getContentsCached(url);
		}
		catch (final IOException e)
		{
			return "Failed to read site.";
		}

		final Matcher fromFull = Pattern.compile("<option.*value=\"" + command[0] + "\">(.*) \\(" + command[0] + "\\)").matcher(s);
		final Matcher toFull = Pattern.compile("<option.*value=\"" + command[1] + "\">(.*) \\(" + command[1] + "\\)").matcher(s);
		final Matcher converted = Pattern.compile("(?s)currency_converter.*bld>(.*) " + command[1]).matcher(s);

		if (fromFull.find() && toFull.find() && converted.find())
			return command[2] + " " + command[0] + " (" + fromFull.group(1) + ") is " + converted.group(1) + " " + command[1] + " (" + toFull.group(1) + ").";
		else
			return "Failed to parse reply, unsupported currency? (http://finance.google.com/finance/converter for a list)";


	}
}
