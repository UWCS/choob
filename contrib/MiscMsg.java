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
	public void commandCT( final Message mes )
	{
		randomReply(mes, new String[] { "Yes, your connection is working fine.", "No, your connection seems really broken." });
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
	public void commandTime( final Message mes )
	{
		irc.sendContextReply(mes, new SimpleDateFormat("'The time is 'HH:mm:ss'.'").format(new Date()));
	}

	public String[] helpCommandDate = {
		"Replies with the current date."
	};
	public void commandDate( final Message mes )
	{
		irc.sendContextReply(mes, new SimpleDateFormat("'The date is 'd MMM yyyy'.'").format(new Date()));
	}

	public String[] helpCommandRandom = {
		"Generate a random number.",
		"[<Max>]",
		"<Max> is the optional maximum to return"
	};
	public void commandRandom( final Message mes )
	{
		double max = 1;
		try
		{
			max = Double.parseDouble(mods.util.getParamString(mes));
		}
		catch (final NumberFormatException e)
		{
			// Assume 1.
		}
		irc.sendContextReply(mes, "Random number between 0 and " + max + " is " + Math.random()*max + ".");
	}

	private void randomReply(final Message mes, final String[] replies)
	{
		randomReply(mes, replies, "", "");
	}

	private void randomReply(final Message mes, final String[] replies, String prefix, String suffix)
	{
		irc.sendContextReply(mes, prefix + replies[new Random().nextInt(replies.length)] + suffix);
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

	public void commandFlipACoin( final Message mes )
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
		final String[] tokens = params.split("(?:^|\\s*,\\s*|\\s+)or(?:\\s*,\\s*|\\s+|$)");
		if (tokens.length <= 1)
		{
			irc.sendContextReply(mes, "My answer is " + (rand.nextBoolean() ? "yes" : "no" ) + ".");
			return;
		}

		// Then split the first group on ","
		final String[] tokens2 = tokens[0].split("\\s*,\\s*");

		final int choice = rand.nextInt(tokens.length + tokens2.length - 1);

		// Java can't see it's guaranteed to be set.
		// Let's all laugh at its expense!
		String output = null;
		for(int i=0; i<tokens2.length; i++)
		{
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
			if (tokens[i].equals(""))
			{
				irc.sendContextReply(mes, "Reply number " + (tokens2.length + i) + " is empty!");
				return;
			}
			if (tokens2.length + i - 1 == choice)
				output = tokens[i];
		}

		irc.sendContextReply(mes, "My answer is " + output + ".");
	}

	public String[] helpCommand8Ball = {
		"Ask the magical 8 ball to sort out your life.",
		"<Question>",
		"<Question> is some a question for the 8 ball the think over."
	};
	public void command8Ball(final Message mes)
	{
		// http://r.wesley.edwards.net/writes/JavaScript/magic8ball.js
		randomReply(mes, new String[] { "Signs point to yes.", "Yes.", "Reply hazy, try again.",
				"Without a doubt.", "My sources say no.", "As I see it, yes.",
				"You may rely on it.", "Concentrate and ask again.", "Outlook not so good.",
				"It is decidedly so.", "Better not tell you now.", "Very doubtful.",
				"Yes - definitely.", "It is certain.", "Cannot predict now.", "Most likely.",
				"Ask again later.", "My reply is no.", "Outlook good.", "Don't count on it." });
	}

	public String[] helpCommandTarot = { "Draw a card from the tarot pack.", };

	public void commandTarot(final Message mes)
	{
		// http://en.wikipedia.org/wiki/Major_Arcana
		randomReply(mes, new String[] { "The Fool", "The Magician", "The High Priestess",
				"The Empress", "The Emperor", "The Hierophant or The Pope", "The Lovers",
				"The Chariot", "Strength", "The Hermit", "Wheel of Fortune", "Justice",
				"The Hanged Man", "Death", "Temperance", "The Devil", "The Tower", "The Star",
				"The Moon", "The Sun", "Judgment", "The World" }, "You drew ", ".");
	}


	public void commandDiscordianDate(final Message mes)
	{
		shellExec(mes, "ddate");
	}

	public void commandServerUptime(final Message mes)
	{
		shellExec(mes, "uptime");
	}

	private void shellExec(final Message mes, final String command)
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

		irc.sendContextReply(mes, rep.toString());
	}

	public String[] helpCommandUptime = {
		"Find out how long the bot has been running for.",
	};

	public void commandUptime( final Message mes )
	{
		irc.sendContextReply(mes, "I have been up " + mods.date.timeLongStamp(new Date().getTime() - mods.util.getStartTime(), 3) + ".");
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
	public void commandWeek(final Message mes)
	{
		final List<String> params = mods.util.getParams(mes);
		final String message = mods.util.getParamString(mes);

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
				irc.sendContextReply(mes, "It's " + day + ", " + diffToTermWeek(diff) + ".");
			} else {
				irc.sendContextReply(mes, "It's " + diffToTermWeek(diff) + ".");
			}
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
					irc.sendContextReply(mes, "Sorry, I can't parse that date. Please use yyyy-mm-dd, dd/mm/yyyy or dd mmm yyyy.");
					return;
				}

				int year = Integer.parseInt(dateMatcher.group(9));
				if (year < 80)
					year += 2000;
				if (year < 100)
					year += 1900;

				now = new GregorianCalendar(year, month, Integer.parseInt(dateMatcher.group(7)), 12, 0, 0).getTime();
			} else {
				irc.sendContextReply(mes, "Sorry, I can't parse that date. Please use yyyy-mm-dd, dd/mm/yyyy or dd mmm yyyy.");
				return;
			}

			final int diff = (int)Math.floor(getTimeRelativeToTerm((int)(now.getTime() / 1000)) / 86400);
			final String day = dayFmt.format(now);

			irc.sendContextReply(mes, day + " is " + diffToTermWeek(diff) + ".");
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
					irc.sendContextReply(mes, weekNum + " isn't a valid Room Booking week number. It must be between 1 and 39, inclusive.");
					return;
				}
			} else {
				weekNum = Integer.parseInt(params.get(1));
				if (params.size() > 3) {
					if (weekNum < 1 || weekNum > 10) {
						irc.sendContextReply(mes, weekNum + " isn't a valid week number. It must be between 1 and 10, inclusive.");
						return;
					}
					final int term = Integer.parseInt(params.get(params.size() - 1));
					if (term < 1 || term > 3) {
						irc.sendContextReply(mes, term + " isn't a valid term. It must be between 1 and 3, inclusive.");
						return;
					}
					weekNum += 10 * (term - 1);
				}
				if (weekNum < 1 || weekNum > 30) {
					irc.sendContextReply(mes, weekNum + " isn't a valid week number. It must be between 1 and 30, inclusive.");
					return;
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

			irc.sendContextReply(mes, (rbw ? "Room Booking week " : "Week ") + num + " is from " + dateStS + " to " + dateEnS + ".");
		} else {
			irc.sendContextReply(mes, "Sorry, I don't know what you mean.");
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

	public void commandExchange(final Message mes)
	{
		String[] command = mods.util.getParamString(mes).replaceAll("[\\(\\),]+", " ").trim().split(" +");
		if (command.length == 2)
			command = new String[] { command[0], command[1], "1" };
		if (command.length != 3)
		{
			irc.sendContextReply(mes, "Incorrect number of arguments specified.");
			return;
		}

		command[0]=command[0].toUpperCase();
		command[1]=command[1].toUpperCase();

		URL url;
		try
		{
			url = new URL("http://finance.yahoo.com/currency/convert?amt=" + URLEncoder.encode(command[2], "UTF-8") + "&from=" + URLEncoder.encode(command[0], "UTF-8") + "&to=" + URLEncoder.encode(command[1], "UTF-8") + "&submit=Convert");
		}
		catch (final UnsupportedEncodingException e)
		{
			irc.sendContextReply(mes, "Unexpected exception generating url.");
			return;
		}
		catch (final MalformedURLException e)
		{
			irc.sendContextReply(mes, "Error, malformed url generated.");
			return;
		}

		String s;
		try
		{
			s = mods.scrape.getContentsCached(url);
		}
		catch (final IOException e)
		{
			irc.sendContextReply(mes, "Failed to read site.");
			return;
		}
		final Matcher fromFull = Pattern.compile("(?s)<td class=\"yfnc_tablehead1\"><b>Symbol</b></td>\\s*<td class=\"yfnc_tablehead1\"><b>([^\n]+?)</b></td>").matcher(s);
		final Matcher toFull = Pattern.compile("(?s)Rate</b></td>\\s*<td class=\"yfnc_tablehead1\"><b>([^\n]+?)</b></td>").matcher(s);
		final Matcher converted = Pattern.compile("(?s)[0-9]</td>\\s*<td class=\"yfnc_tabledata1\"><b>([^\n]+?)</b></td>").matcher(s);

		if (fromFull.find() && toFull.find() && converted.find())
			irc.sendContextReply(mes, command[2] + " " + command[0] + " (" + fromFull.group(1) + ") is " + converted.group(1) + " " + command[1] + " (" + toFull.group(1) + ").");
		else
			irc.sendContextReply(mes, "Failed to parse reply, unsupported currency? (http://finance.yahoo.com/currency for a list)");


	}
}
