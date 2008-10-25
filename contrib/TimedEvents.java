import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.IRCEvent;
import uk.co.uwcs.choob.support.events.Message;

class TimedEvent
{
	public int id;
	public int mesID; // For duplication
	public int synthLevel; // Remember this.
	public String flags;
	public String command;
	public long executeAt;
}

/**
 * Timed events plugin for Choob
 *
 * @author bucko
 */
public class TimedEvents
{
	public String[] info()
	{
		return new String[] {
			"Allows users to save events to be executed in the future.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private static int MAX_DELAYED_COUNT = 2; // Maximum number of times we'll allow an event to be delayed.

	private final Map<String,TimedEvent> lastDelivery;
	private final Modules mods;
	private final IRCInterface irc;

	public TimedEvents(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;

		lastDelivery = new HashMap<String,TimedEvent>();

		// If we were reloaded, don't want old intervals hanging around.
		mods.interval.reset();

		// Reload all old queued objects...
		final long time = System.currentTimeMillis();
		final List<TimedEvent> events = mods.odb.retrieve(TimedEvent.class, "WHERE 1");
		for(final TimedEvent event: events)
		{
			mods.interval.callBack( event, event.executeAt - time, -1 );
		}
	}

	public String[] helpCommandIn = {
		"Make a command execute in the future.",
		"<When> <Command>",
		"<When> is a non-empty time of the form [<Days>d][<Hours>h][<Minutes>m][<Seconds>s]",
		"<Command> is the command to execute"
	};
	public void commandIn( final Message mes )
	{
		if (!checkForAndUpdateRecursion(mes)) {
			irc.sendContextReply(mes, "Synthetic event recursion detected (timedevents.delayed). Stopping.");
			return;
		}

		final List<String> params = mods.util.getParams(mes, 2);

		if (params.size() <= 2) {
			irc.sendContextReply(mes, "Syntax is: TimedEvents.In <When> <Command>");
			return;
		}

		final String time = params.get(1);
		String command = params.get(2);

		long period;
		try {
			period = apiDecodePeriod(time);
		} catch (final NumberFormatException e) {
			irc.sendContextReply(mes, "Bad time format: " + time + ". Examples: 10h, 5m2s, 3d2h.");
			return;
		}

		// Does the command have a trigger?
		final Matcher trigMatch = Pattern.compile(irc.getTriggerRegex()).matcher(command);
		if (trigMatch.find())
			// Yes. Blat it. We'll add it later.
			command = command.substring(trigMatch.end());

		{
			final int spacePos = command.indexOf(' ');

			String actual_command;
			if (spacePos==-1)
				actual_command=command;
			else
				actual_command=command.substring(0, spacePos);

			if (!mods.plugin.validCommand(actual_command))
			{
				irc.sendContextReply(mes, "'" + actual_command + "' could not be identified as a valid command or alias, please try again.");
				return;
			}
		}

		final TimedEvent timedEvent = new TimedEvent();
		timedEvent.mesID = mods.history.getMessageID(mes);
		timedEvent.synthLevel = mes.getSynthLevel();
		timedEvent.flags = encodeFlags(((IRCEvent)mes).getFlags());
		timedEvent.command = command;
		timedEvent.executeAt = System.currentTimeMillis() + period * 1000;

		if (timedEvent.mesID == -1)
		{
			irc.sendContextReply(mes, "Internal error: The message you sent apparently did not exist!");
			return;
		}

		mods.odb.save(timedEvent);
		mods.interval.callBack( timedEvent, period * 1000, -1 );

		irc.sendContextReply(mes, "OK, will do at " + new Date(timedEvent.executeAt) + ".");
	}

	public String[] helpCommandAt = {
		"Make a command execute at a specified time in the future.",
		"[ <Date> ] <Time> <Command>",
		"<Date> is an (optional) date of the form DD/MM/YY[YY]",
		"<Time> is a time of the form HH[[:]MM[[:]SS]][am|pm]",
		"<Command> is the command to execute"
	};
	public void commandAt( final Message mes )
	{
		if (!checkForAndUpdateRecursion(mes)) {
			irc.sendContextReply(mes, "Synthetic event recursion detected (timedevents.delayed). Stopping.");
			return;
		}

		final List<String> params = mods.util.getParams(mes, 2);

		if (params.size() <= 2)
		{
			irc.sendContextReply(mes, "Syntax: 'TimedEvents.At " + helpCommandAt[1] + "'.");
			return;
		}

		String time = params.get(1);
		String command = params.get(2);

		final GregorianCalendar cal = new GregorianCalendar();

		boolean dateSet = false;
		final Pattern timePat = Pattern.compile("(0?[0-9]|1[0-9]|2[0-3])(?::?([0-5][0-9])(?::?([0-5][0-9]))?)?(am|pm)?");
		Matcher ma = timePat.matcher(time);
		if (!ma.matches())
		{
			// OK, not a time then. Is it a date?
			ma = Pattern.compile("(\\d{1,2})/(\\d{1,2})(?:/(\\d{2}(?:\\d{2})?))?").matcher(time);
			if (!ma.matches())
			{
				irc.sendContextReply(mes, "Bad date/time format: " + time + ".");
				return;
			}

			if (ma.group(3) != null)
			{
				int year = Integer.parseInt(ma.group(3));
				// Y3K bug!
				if (year < 100)
					year += 2000;
				cal.set(Calendar.YEAR, year);
			}

			final int month = Integer.parseInt(ma.group(2));
			if (month > 12 || month == 0)
			{
				irc.sendContextReply(mes, "Invalid month: " + month + "!");
				return;
			}
			cal.set(Calendar.MONTH, month - 1);

			final int day = Integer.parseInt(ma.group(1));
			// TODO: check? This will be difficult.
			if (day > 31 || day == 0)
			{
				irc.sendContextReply(mes, "Invalid day of month: " + day + "!");
				return;
			}
			cal.set(Calendar.DAY_OF_MONTH, day);

			// (Oh, the humanity!)
			final int spacePos = command.indexOf(' ');
			if (spacePos == -1 || spacePos == command.length() - 1)
			{
				irc.sendContextReply(mes, "Syntax: 'TimedEvents.At " + helpCommandAt[1] + "'.");
				return;
			}

			time = command.substring(0, spacePos);
			command = command.substring(spacePos + 1);
			ma = timePat.matcher(time);
			if (!ma.matches())
			{
				irc.sendContextReply(mes, "Bad time format: " + time + ".");
				return;
			}
			dateSet = true;
		}

		int h,m,s;
		try
		{
			h = Integer.parseInt(ma.group(1));

			m = 0;
			if (ma.group(2) != null)
				m = Integer.parseInt(ma.group(2));
			s = 0;

			if (ma.group(3) != null)
				s = Integer.parseInt(ma.group(3));
		}
		catch (final NumberFormatException e)
		{
			irc.sendContextReply( mes, "Bad time format: " + time + ".");
			return;
		}

		if (ma.group(4) != null)
		{
			if (ma.group(4).equalsIgnoreCase("am") || ma.group(4).equalsIgnoreCase("pm"))
			{
				// AM or PM only allow an hour of 1 - 12.
				if (h < 1 || h > 12) {
					irc.sendContextReply( mes, "Bad time format: " + time + ".");
					return;
				}
				if (h == 12)
					h = 0;
			}

			if (ma.group(4).equalsIgnoreCase("pm"))
				h += 12;
		}

		cal.set(Calendar.HOUR_OF_DAY, h);
		cal.set(Calendar.MINUTE, m);
		cal.set(Calendar.SECOND, s);

		if (cal.getTimeInMillis() < System.currentTimeMillis())
		{
			if (dateSet)
			{
				irc.sendContextReply(mes, "Can't make things happen in the past!");
				return;
			}
			cal.add(Calendar.DAY_OF_MONTH, 1);
		}

		// Does the command have a trigger?
		final Matcher trigMatch = Pattern.compile(irc.getTriggerRegex()).matcher(command);
		if (trigMatch.find())
			// Yes. Blat it. We'll add it later.
			command = command.substring(trigMatch.end());


		{
			final int spacePos = command.indexOf(' ');

			String actual_command;
			if (spacePos==-1)
				actual_command=command;
			else
				actual_command=command.substring(0, spacePos);

			if (!mods.plugin.validCommand(actual_command))
			{
				irc.sendContextReply(mes, "'" + actual_command + "' could not be identified as a valid command or alias, please try again.");
				return;
			}
		}


		final TimedEvent timedEvent = new TimedEvent();
		timedEvent.mesID = mods.history.getMessageID(mes);
		timedEvent.synthLevel = mes.getSynthLevel();
		timedEvent.flags = encodeFlags(((IRCEvent)mes).getFlags());
		timedEvent.command = command;
		timedEvent.executeAt = cal.getTimeInMillis();

		if (timedEvent.mesID == -1)
		{
			irc.sendContextReply(mes, "Internal error: The message you sent apparently did not exist!");
			return;
		}

		final long callbackTime = cal.getTimeInMillis() - System.currentTimeMillis();

		mods.odb.save(timedEvent);
		mods.interval.callBack( timedEvent, callbackTime, -1 );

		irc.sendContextReply(mes, "OK, will do at " + cal.getTime() + ".");
	}

	public long apiDecodePeriod(final String time) throws NumberFormatException {
		int period = 0;

		int currentPos = -1;
		int lastPos = 0;

		if ( (currentPos = time.indexOf('d', lastPos)) >= 0 ) {
			period += 60 * 60 * 24 * Integer.parseInt(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if ( (currentPos = time.indexOf('h', lastPos)) >= 0 ) {
			period += 60 * 60 * Integer.parseInt(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if ( (currentPos = time.indexOf('m', lastPos)) >= 0 ) {
			period += 60 * Integer.parseInt(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if ( (currentPos = time.indexOf('s', lastPos)) >= 0 ) {
			period += Integer.parseInt(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if (lastPos != time.length())
			throw new NumberFormatException("Invalid time format: " + time);

		return period;
	}

	public synchronized void interval( final Object parameter )
	{
		if (parameter != null && parameter instanceof TimedEvent) {
			// It's a message to be redelivered

			final TimedEvent timedEvent = (TimedEvent) parameter;

			final Message mes = mods.history.getMessage(timedEvent.mesID);
			if (mes == null)
			{
				System.err.println("Event number " + timedEvent.mesID + " appears to have gone!");
				return;
			}

			final String command = irc.getTrigger() + timedEvent.command;

			Message newMes = (Message)mes.cloneEvent(command);
			// Resynth...
			for(int i=0; i<timedEvent.synthLevel; i++)
				newMes = (Message)newMes.cloneEvent(command);

			// Put back flags.
			final Map<String,String> mesFlags = ((IRCEvent)newMes).getFlags();
			decodeFlags(mesFlags, timedEvent.flags);

			lastDelivery.put(mes.getContext(), timedEvent);

			// Get the plugin/command.

			mods.synthetic.doSyntheticMessage( newMes );

			mods.odb.delete(timedEvent);
		}
	}

	public String[] helpCommandLast = {
		"Find out what the last queued event to be executed was."
	};
	public void commandLast( final Message mes )
	{
		final TimedEvent last = lastDelivery.get(mes.getContext());
		if (last != null)
		{
			final Message lMes = mods.history.getMessage(last.mesID);
			if (lMes != null)
				irc.sendContextReply(mes, lMes.getNick() + " queued the following command on " + new Date(lMes.getMillis()).toString() + ": " + last.command);
			else
				irc.sendContextReply(mes, "The following command was queued: " + last.command);
		}
		else
			irc.sendContextReply(mes, "Nobody queued nuffin', gov'ner.");
	}

	String encodeFlags(final Map<String,String> flags)
	{
		String rv = "";
		for (final String prop : flags.keySet())
		{
			// Properties starting "_" should not be cloned implicitly.
			if (prop.startsWith("_"))
				continue;

			if (rv.length() > 0)
				rv += ",";
			rv += prop.replaceAll("(\\\\|\"|=|,)", "\\\\$1");
			rv += "=";
			rv += flags.get(prop).replaceAll("(\\\\|\"|=|,)", "\\\\$1");
		}
		return rv;
	}

	void decodeFlags(final Map<String,String> flags, final String data)
	{
		if (data == null)
			return;
		final String[] flagItems = data.split("(?<!\\\\),");
		for (final String flagItem : flagItems)
		{
			final String[] parts = flagItem.split("(?<!\\\\)=", 2);
			flags.put(parts[0].replaceAll("\\\\([\\\\\"=,])", "$1"), parts[1].replaceAll("\\\\([\\\\\"=,])", "$1"));
		}
	}

	boolean checkForAndUpdateRecursion(final Message mes)
	{
		// Message extends IRCEvent, so this cast will always succeed.
		final Map<String,String> mesFlags = ((IRCEvent)mes).getFlags();

		if (mesFlags.containsKey("timedevents.delayed"))
		{
			final int recurseLevel = Integer.parseInt(mesFlags.get("timedevents.delayed")) + 1;

			// Stop recursion.
			if (recurseLevel > MAX_DELAYED_COUNT) {
				return false;
			}

			mesFlags.put("timedevents.delayed", Integer.valueOf(recurseLevel).toString());
		}
		else
		{
			mesFlags.put("timedevents.delayed", "1");
		}
		return true;
	}
}
