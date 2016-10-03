import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobBadSyntaxError;
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
	@Produces("text/plain")
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
		"[<week> [[OF] TERM <term>] | RBW <week> | YEAR <week> [[OF] <year>] | <date>]",
		"<week> is the week (or room booking week [RBW] or week of year [YEAR]) to look up",
		"<term> is the term (1, 2 or 3 only)",
		"<year> is the year for the week",
		"<date> is the date, in \"yyyy-mm-dd\", \"d/m/yyyy\" or \"d mmm yyyy\" format."
	};

	final int[] yearStarts = {
			970441200, // 2000-10-02
			1001890800, // 2001-10-01
			1033340400, // 2002-09-30
			1064790000, // 2003-09-29
			1096239600, // 2004-09-27
			1127689200, // 2005-09-26
			1159743600, // 2006-10-02
			1191193200, // 2007-10-01
			1222642800, // 2008-09-29
			1254697200, // 2009-10-05
			1286146800, // 2010-10-04
			1317596400, // 2011-10-03
			1349046000, // 2012-10-01
			1380495600, // 2013-09-30
			1411945200, // 2014-09-29
			1444086000, // 2015-10-06
			1475449200, // 2016-10-03
			1506898800, // 2017-10-02
			1538348400, // 2018-10-01
			1569798000, // 2019-09-30
			1601852400, // 2020-10-05
			1633302000, // 2021-10-04
			1664751600, // 2022-10-03
			1696201200, // 2023-10-02
			1727650800, // 2024-09-30
			1759705200 // 2025-10-06
		};

	public String commandWeek(final String mes)
	{
		final List<String> params = mods.util.getParams(mes);
		final DateFormat dateFormat = new SimpleDateFormat("EEE, d MMM yyyy");

		if (params.size() == 1) {
			return "It is " + getWeekString() + ".";
		}

		if ((params.size() == 2) && isNumber(params.get(1))) {
			final int tWeek = Integer.parseInt(params.get(1));
			if ((tWeek < 1) || (tWeek > 30))
				return "Error: <week> must be between 1 and 30 inclusive.";

			final int rbWeek = tWeek > 20 ? tWeek + 9 : tWeek > 10 ? tWeek + 4 : tWeek;
			final Date time = new Date(getYearStart().getTime() + (rbWeek - 1) * 7 * 86400 * 1000L);
			return "Week " + tWeek + " is " + getWeekDates(time) + ", " + getWeekString(time) + ".";
		}

		if (((params.size() == 4) && isNumber(params.get(1)) && params.get(2).equalsIgnoreCase("term") && isNumber(params.get(3)))
				|| ((params.size() == 5) && isNumber(params.get(1)) && params.get(2).equalsIgnoreCase("of") && params.get(3).equalsIgnoreCase("term") && isNumber(params.get(4)))) {
			final int week = Integer.parseInt(params.get(1));
			final int term = Integer.parseInt(params.get(params.size() - 1));
			if ((week < 1) || (week > 10))
				return "Error: term <week> must be between 1 and 10 inclusive.";
			if ((term < 1) || (term > 3))
				return "Error: <term> must be between 1 and 3 inclusive.";

			final int rbWeek = (term == 3 ? 29 : term == 2 ? 14 : 0) + week;
			final Date time = new Date(getYearStart().getTime() + (rbWeek - 1) * 7 * 86400 * 1000L);
			return "Week " + week + " of term " + term + " is " + getWeekDates(time) + ", " + getWeekString(time) + ".";
		}

		if ((params.size() == 3) && params.get(1).equalsIgnoreCase("rbw") && isNumber(params.get(2))) {
			final int rbWeek = Integer.parseInt(params.get(2));
			if ((rbWeek < 1) || (rbWeek > 39))
				return "Error: room booking <week> must be between 1 and 39 inclusive.";

			final Date time = new Date(getYearStart().getTime() + (rbWeek - 1) * 7 * 86400 * 1000L);
			return "Room Booking Week " + rbWeek + " is " + getWeekDates(time) + ", " + getWeekString(time) + ".";
		}

		if (((params.size() == 3) && params.get(1).equalsIgnoreCase("year") && isNumber(params.get(2)))
				|| ((params.size() == 4) && params.get(1).equalsIgnoreCase("year") && isNumber(params.get(2)) && isNumber(params.get(3)))
				|| ((params.size() == 5) && params.get(1).equalsIgnoreCase("year") && isNumber(params.get(2)) && params.get(3).equalsIgnoreCase("of") && isNumber(params.get(4)))) {
			final int yWeek = Integer.parseInt(params.get(2));
			final int year = params.size() > 3 ? Integer.parseInt(params.get(params.size() - 1)) : 0;
			if ((yWeek < 1) || (yWeek > 53))
				return "Error: <week> of year must be between 1 and 53 inclusive.";

			final Calendar cal = new GregorianCalendar();
			final int endYear = year > 0 ? year : cal.get(Calendar.YEAR) + 1;
			if (year > 0) {
				cal.set(Calendar.YEAR, year);
				cal.set(Calendar.MONTH, Calendar.JANUARY);
				cal.set(Calendar.DAY_OF_MONTH, 1);
			} else {
				while (cal.get(Calendar.WEEK_OF_YEAR) == yWeek)
					cal.add(Calendar.DAY_OF_MONTH, -1);
			}
			while ((cal.get(Calendar.WEEK_OF_YEAR) != yWeek) && (cal.get(Calendar.YEAR) <= endYear))
				cal.add(Calendar.DAY_OF_MONTH, 1);
			if (cal.get(Calendar.YEAR) > endYear)
				return "Error: week " + yWeek + " was not found in " + (year > 0 ? year : endYear - 1) + ".";

			return "Week " + yWeek + " of " + cal.get(Calendar.YEAR) + " is " + getWeekDates(cal.getTime()) + ", " + getWeekString(cal.getTime()) + ".";
		}

		Matcher dateMatcher = Pattern.compile(
				"^(?:(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d)" +
				"|(\\d\\d?)/(\\d\\d?)/(\\d\\d?\\d?\\d?)" +
				"|(\\d\\d?) (\\w\\w\\w) (\\d\\d?\\d?\\d?))$",
				Pattern.CASE_INSENSITIVE).matcher(mes);
		if ((params.size() >= 2) && dateMatcher.matches()) {
			Date now = null;
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
				if (month == -1)
					return "Error: <date>'s month name must be the 3-letter abbreviation.";
				int year = Integer.parseInt(dateMatcher.group(9));
				if (year < 80)
					year += 2000;
				if (year < 100)
					year += 1900;
				now = new GregorianCalendar(year, month, Integer.parseInt(dateMatcher.group(7)), 12, 0, 0).getTime();
			} else {
				return "Error: <date> is invalid in some unexpected way.";
			}

			return dateFormat.format(now) + " is " + getWeekString(now) + ".";
		}

		throw new ChoobBadSyntaxError();
	}

	boolean isNumber(final String item) {
		try {
			return item.equals(Integer.valueOf(item).toString());
		} catch(final Exception e) {
			return false;
		}
	}

	int nameToMonth(String name) {
		if (name.equalsIgnoreCase("jan")) return 0;
		if (name.equalsIgnoreCase("feb")) return 1;
		if (name.equalsIgnoreCase("mar")) return 2;
		if (name.equalsIgnoreCase("apr")) return 3;
		if (name.equalsIgnoreCase("may")) return 4;
		if (name.equalsIgnoreCase("jun")) return 5;
		if (name.equalsIgnoreCase("jul")) return 6;
		if (name.equalsIgnoreCase("aug")) return 7;
		if (name.equalsIgnoreCase("sep")) return 8;
		if (name.equalsIgnoreCase("oct")) return 9;
		if (name.equalsIgnoreCase("nov")) return 10;
		if (name.equalsIgnoreCase("dec")) return 11;
		return -1;
	}

	Date getYearStart() {
		// We're shifting the date forwards by 13 weeks, the length of the
		// summary holidays, so that the default year during those holidays is
		// is the *next* year, not the one just finished.
		final Date now = new Date();
		return getYearStart(new Date(now.getTime() + 13 * 7 * 86400 * 1000L));
	}

	Date getYearStart(final Date date) {
		final int time = (int)Math.floor(date.getTime() / 1000);
		for (int i = 1; i < yearStarts.length; i++)
			if (yearStarts[i] > time)
				return new Date(yearStarts[i - 1] * 1000L);
		return new Date(yearStarts[yearStarts.length - 1] * 1000L);
	}

	int getTermDay(final Date date) {
		return (int)Math.floor((date.getTime() - getYearStart(date).getTime()) / 1000 / 86400);
	}

	int getTermWeek(final int day) {
		// Term 1 (day 0 - 67)
		if ((day >= 0) && (day <= 67))
			return (int)Math.floor(day / 7) + 1;

		// Christmas Holiday (day 68 - 97)
		if ((day >= 68) && (day <= 97))
			return 0;

		// Term 2 (day 98 - 165)
		if ((day >= 98) && (day <= 165))
			return (int)Math.floor(day / 7) - 3;

		// Easter Holiday (day 166 - 202)
		if ((day >= 166) && (day <= 202))
			return 0;

		// Term 3 (day 203 - 270)
		if ((day >= 203) && (day <= 270))
			return (int)Math.floor(day / 7) - 8;

		// Anything else within 13 weeks before or 53 weeks after the start is "in range".
		if ((day >= -91) && (day <= 371))
			return 0;

		return -1;
	}

	int getRoomBookingWeek(final int day) {
		// Term 1            (day   0 -  67)
		// Christmas Holiday (day  68 -  97)
		// Term 2            (day  98 - 165)
		// Easter Holiday    (day 166 - 202)
		// Term 3            (day 203 - 270)
		if ((day >= 0) && (day <= 270))
			return (int)Math.floor(day / 7) + 1;

		// Anything else within 13 weeks before or 53 weeks after the start is "in range".
		if ((day >= -91) && (day <= 371))
			return 0;

		return -1;
	}

	String getYearWeek(final Date date) {
		final Calendar cal = new GregorianCalendar();
		cal.setTime(date);
		cal.add(Calendar.DAY_OF_MONTH, -((cal.get(Calendar.DAY_OF_WEEK) + 7 - Calendar.MONDAY) % 7));
		return "week " + cal.get(Calendar.WEEK_OF_YEAR) + " of " + cal.get(Calendar.YEAR);
	}

	String getWeekString() {
		return getWeekString(new Date());
	}

	String getWeekString(final Date date) {
		final int tDay = getTermDay(date);
		final int tWeek = getTermWeek(tDay);
		final int rbWeek = getRoomBookingWeek(tDay);
		final String yWeek = getYearWeek(date);
		return getWeekString(tWeek, rbWeek, yWeek);
	}

	String getWeekString(final int tWeek, final int rbWeek, final String yWeek) {
		//          Christmas           Easter              Summer
		// <term 1> <4 weeks> <term 2 > <5 weeks> <term 3 > <~13 weeks>
		// 1  -  10           11  -  20           21  -  30             (week)
		// 1  -  10 11  -  14 15  -  24 25  -  29 30  -  39             (room booking week)
		if (rbWeek < 0)
			return "beyond the academic year data (" + yWeek + ")";
		if (rbWeek == 0)
			return "the summer holidays (" + yWeek + ")";
		if ((rbWeek < 15) && (tWeek == 0))
			return "week " + (rbWeek - 10) + " of the Christmas holidays (room booking week " + rbWeek + ", " + yWeek + ")";
		if (tWeek == 0)
			return "week " + (rbWeek - 24) + " of the Easter holidays (room booking week " + rbWeek + ", " + yWeek + ")";
		if (tWeek < 11)
			return "week " + tWeek + " of term 1 (room booking week " + rbWeek + ", " + yWeek + ")";
		if (tWeek < 21)
			return "week " + (tWeek - 10) + " of term 2 (room booking week " + rbWeek + ", " + yWeek + ")";
		return "week " + (tWeek - 20) + " of term 3 (room booking week " + rbWeek + ", " + yWeek + ")";
	}

	String getWeekDates(final Date date) {
		final DateFormat dateFormat = new SimpleDateFormat("EEE d MMM yyyy");
		final Date date2 = new Date(date.getTime() + 6 * 86400 * 1000L);

		return dateFormat.format(date) + " - " + dateFormat.format(date2);
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
