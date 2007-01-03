import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.text.ParseException;
import java.lang.StringBuilder;
public class Timezone
{

	private Modules mods;
	private IRCInterface irc;


	public String[] info()
	{
		return new String[] {
			"Plugin to find time in different parts of the world.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev $Date"
		};
	}

	public Timezone(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}
	private static final String PATTERN = "HH:mm:ss";

	private String fixZone(String inputZone)
	{
		String fixed = inputZone;
		if (fixed.length() == 3)
			fixed = fixed.toUpperCase();
		else
		{
			try
			{
				int offset = Integer.parseInt(fixed.replaceAll(":|\\+",""));
				String[] ids = TimeZone.getAvailableIDs(offset * 36000);
				if (ids.length > 0)
					fixed = ids[0];
			} catch (NumberFormatException e)
			{

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
	public void commandWhenIs(Message mes)
	{
		List<String> params = mods.util.getParams(mes,5);
		TimeZone targetTimeZone = TimeZone.getTimeZone("Europe/London");
		if ((params.size() == 5) && (params.get(3).equals("in")))
		{
			String input = fixZone(params.get(4));
			targetTimeZone = TimeZone.getTimeZone(input);
		} else if (params.size() != 3)
		{
			irc.sendContextReply(mes,"Usage whenis <time> <timezone> [in <timezone>]");
			return;
		}

		String input = params.get(1) + " " + params.get(2);
		try
		{
			SimpleDateFormat df = new SimpleDateFormat(PATTERN);
			GregorianCalendar cal = new GregorianCalendar();
			cal.setTimeZone(TimeZone.getTimeZone(params.get(2)));
			String[] splitTime = params.get(1).split(":");
				
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
			} catch (NumberFormatException e)
			{
				throw new ParseException("Wrong time format",0);
			}

			cal.set(Calendar.HOUR_OF_DAY,hour);
			cal.set(Calendar.MINUTE,min);
			cal.set(Calendar.SECOND,sec);
			df.setTimeZone(targetTimeZone);

			String zone = fixZone(params.get(2));

			StringBuilder response = new StringBuilder();
			response.append(params.get(1));
			response.append(" (");
			response.append(TimeZone.getTimeZone(zone).getDisplayName());
			response.append(") is ");
			response.append(df.format(cal.getTime()));
			response.append(" (");
			response.append(targetTimeZone.getDisplayName());
			response.append(")");
			irc.sendContextReply(mes,response.toString());

		} catch (ParseException e)
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

	public boolean optionCheckUserTimezone(String value, String nick) {
		return true; //hmm
	}
	
	private String checkOption(String userNick) {
		try {
 			return (String)mods.plugin.callAPI("Options", "GetUserOption", userNick,"Zone", optionsUserDefaults[0]);
		} catch (ChoobNoSuchCallException e) {
			return optionsUserDefaults[0];
		}
	}


	public String[] helpCommandTimeIn = {
		"Returns the current time in specified time zone.",
		"<TimeZone (eg GMT or Europe/London or +00:00)>",
		"<TimeZone> is the timezone to return the current time of."
	};
	public void commandTimeIn(Message mes)
	{
		List<String> params = mods.util.getParams(mes,3);
		HashSet<String> lists = new HashSet<String>();
		String input = checkOption(mes.getNick());

		if ((params.size() != 2) && (input == null))
		{
			irc.sendContextReply(mes,"Usage timein <timezone>");
			return;
		}

		if ((params.size() == 2) || (input == null))
			input = fixZone(params.get(1));
		

		SimpleDateFormat df = new SimpleDateFormat(PATTERN);
		GregorianCalendar now = new GregorianCalendar();
		TimeZone timeZone = TimeZone.getTimeZone(input);
		df.setTimeZone(timeZone);
		irc.sendContextReply(mes,"Current time in " + timeZone.getDisplayName() + " is: " + df.format(now.getTime()));
	
	}

}
