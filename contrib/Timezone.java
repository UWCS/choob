import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;
public class Timezone
{

	private final Modules mods;
	private final IRCInterface irc;


	public String[] info()
	{
		return new String[] {
			"Plugin to find time in different parts of the world.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev $Date"
		};
	}

	public Timezone(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}
	private static final String PATTERN = "HH:mm:ss";

	private String fixZone(final String inputZone)
	{
		String fixed = inputZone;
		if (fixed.length() == 3)
			fixed = fixed.toUpperCase();
		else
		{
			try
			{
				final int offset = Integer.parseInt(fixed.replaceAll(":|\\+",""));
				final String[] ids = TimeZone.getAvailableIDs(offset * 36000);
				if (ids.length > 0)
					fixed = ids[0];
			} catch (final NumberFormatException e)
			{
				// Can't fix it, just return it.
			}
		}
		return fixed;
	}

	public String[] helpCommandWhenIs = {
		"Converts specified time & timezone to specified timezone, or GMT if the latter is not specified.",
		"<Time (HH:mm[:ss])> <Original TimeZone (eg GMT or Europe/London or +00:00)> [in <Target TimeZone (eg GMT or Europe/London or +00:00)>]",
		"<Time> is the time to convert.",
		"<Original TimeZone> is the timezone to convert from.",
		"<Target TimeZone> is the TimeZone to convert to."
	};
	public void commandWhenIs(final Message mes)
	{
		final List<String> params = mods.util.getParams(mes,5);
		TimeZone targetTimeZone = TimeZone.getTimeZone("Europe/London");
		if (params.size() == 5 && params.get(3).equals("in"))
		{
			final String input = fixZone(params.get(4));
			targetTimeZone = TimeZone.getTimeZone(input);
		} else if (params.size() != 3)
		{
			irc.sendContextReply(mes,"Usage whenis <time> <timezone> [in <timezone>]");
			return;
		}

		try
		{
			final SimpleDateFormat df = new SimpleDateFormat(PATTERN);
			final GregorianCalendar cal = new GregorianCalendar();
			final String zone = fixZone(params.get(2));
			final TimeZone sourceTimeZone = TimeZone.getTimeZone(zone);
			cal.setTimeZone(sourceTimeZone);
			final String[] splitTime = params.get(1).split(":");

			int hour = 0;
			int min = 0;
			int sec = 0;
			try
			{
				if (splitTime.length >= 2)
				{
					hour = Integer.parseInt(splitTime[0]);
					min = Integer.parseInt(splitTime[1]);
					if (splitTime.length > 2)
					{
						sec = Integer.parseInt(splitTime[2]);
					}
				} else
				{
					throw new ParseException("Wrong time format",0);
				}
			} catch (final NumberFormatException e)
			{
				throw new ParseException("Wrong time format",0);
			}

			cal.set(Calendar.HOUR_OF_DAY,hour);
			cal.set(Calendar.MINUTE,min);
			cal.set(Calendar.SECOND,sec);
			df.setTimeZone(targetTimeZone);
			final boolean sourceInDst = sourceTimeZone.inDaylightTime(cal.getTime());
			final boolean targetInDst = targetTimeZone.inDaylightTime(cal.getTime());

			final StringBuilder response = new StringBuilder();
			response.append(params.get(1));
			response.append(" (");
			response.append(sourceTimeZone.getDisplayName(sourceInDst,TimeZone.LONG));
			response.append(") is ");
			response.append(df.format(cal.getTime()));
			response.append(" (");
			response.append(targetTimeZone.getDisplayName(targetInDst,TimeZone.LONG));
			response.append(")");
			irc.sendContextReply(mes,response.toString());

		} catch (final ParseException e)
		{
			irc.sendContextReply(mes,"Error parsing input time, should be in format: " + PATTERN);
			return;
		}
	}

	public String[] optionsUser = { "Zone" };
	public String[] optionsUserDefaults = { "Europe/London" };

	public String[] helpOptionTimezoneZone = {
		"Set your timezone for the timein command with no parameters."
	};

	public boolean optionCheckUserTimezone(final String value, final String nick) {
		return true; //hmm
	}

	private String checkOption(final String userNick) {
		try {
 			return (String)mods.plugin.callAPI("Options", "GetUserOption", userNick,"Zone", optionsUserDefaults[0]);
		} catch (final ChoobNoSuchCallException e) {
			return optionsUserDefaults[0];
		}
	}


	public String[] helpCommandTimeIn = {
		"Returns the current time in specified time zone.",
		"<TimeZone (eg GMT or Europe/London or +00:00)>",
		"<TimeZone> is the timezone to return the current time of."
	};
	public void commandTimeIn(final Message mes)
	{
		final List<String> params = mods.util.getParams(mes,3);
		String input = checkOption(mes.getNick());

		if (params.size() != 2 && input == null)
		{
			irc.sendContextReply(mes,"Usage timein <timezone>");
			return;
		}

		if (params.size() == 2 || input == null)
			input = fixZone(params.get(1));


		final SimpleDateFormat df = new SimpleDateFormat(PATTERN);
		final GregorianCalendar now = new GregorianCalendar();
		final TimeZone timeZone = TimeZone.getTimeZone(input);
		final boolean dst = timeZone.inDaylightTime(now.getTime());
		df.setTimeZone(timeZone);
		irc.sendContextReply(mes,"Current time in " + timeZone.getDisplayName(dst,TimeZone.LONG) + " is: " + df.format(now.getTime()));

	}

}
