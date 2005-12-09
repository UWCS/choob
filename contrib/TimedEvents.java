import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import uk.co.uwcs.choob.modules.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.SimpleDateFormat;

public class TimedEvent
{
	public int id;
	public int mesID; // For duplication
	public int synthLevel; // Remember this.
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
			mods.util.getVersion()
		};
	}

	private TimedEvent lastDelivery = null;
	private Modules mods;
	private IRCInterface irc;

	public TimedEvents(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;

		// If we were reloaded, don't want old intervals hanging around.
		mods.interval.reset();

		// Reload all old queued objects...
		long time = System.currentTimeMillis();
		List<TimedEvent> events = mods.odb.retrieve(TimedEvent.class, "WHERE 1");
		for(TimedEvent event: events)
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
	public void commandIn( Message mes )
	{
		// Stop recursion
		if (mes.getSynthLevel() > 1) {
			irc.sendContextReply(mes, "Synthetic event recursion detected. Stopping.");
			return;
		}

		List<String> params = mods.util.getParams(mes, 2);

		if (params.size() <= 2) {
			irc.sendContextReply(mes, "Syntax is: TimedEvents.In <When> <Command>");
			return;
		}

		String time = params.get(1);
		String command = params.get(2);

		long period;
		try {
			period = apiDecodePeriod(time);
		} catch (NumberFormatException e) {
			irc.sendContextReply(mes, "Bad time format: " + time + ". Examples: 10h, 5m2s, 3d2h.");
			return;
		}

		// Does the command have a trigger?
		Matcher trigMatch = Pattern.compile(irc.getTriggerRegex()).matcher(command);
		if (trigMatch.find())
			// Yes. Blat it. We'll add it later.
			command = command.substring(trigMatch.end());

		TimedEvent timedEvent = new TimedEvent();
		timedEvent.mesID = mods.history.getMessageID(mes);
		timedEvent.synthLevel = mes.getSynthLevel();
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
		"Make a command execute in the future.",
		"<When> <Command>",
		"<When> is a time of the form HH[:]MM[[:]SS][am|pm]",
		"<Command> is the command to execute"
	};
	public void commandAt( Message mes )
	{
		// Stop recursion
		if (mes.getSynthLevel() > 1) {
			irc.sendContextReply(mes, "Synthetic event recursion detected. Stopping.");
			return;
		}

		// XXX /Lots/ of duplicated code from in.
		List<String> params = mods.util.getParams(mes, 2);

		if (params.size() <= 2) {
			irc.sendContextReply(mes, "Syntax is: TimedEvents.At <When> <Command>");
			return;
		}

		String time = params.get(1);
		String command = params.get(2);

		// Does the command have a trigger?
		Matcher trigMatch = Pattern.compile(irc.getTriggerRegex()).matcher(command);
		if (trigMatch.find())
			// Yes. Blat it. We'll add it later.
			command = command.substring(trigMatch.end());

		// Java--
		GregorianCalendar cal = new GregorianCalendar();
		Matcher ma = Pattern.compile("((?:0?[0-9])|(?:1[0-9])|(?:2[0-3])):?([0-5][0-9])(?::?([0-5][0-9]))?(am|pm)?").matcher(time);
		if (!ma.matches())
		{
			irc.sendContextReply(mes, "Bad time format: " + time + ".");
			return;
		}

		int h,m,s;
		try
		{
			h = Integer.parseInt(ma.group(1));
			m = Integer.parseInt(ma.group(2));
			s = 0;

			if (ma.group(3) != null)
				s = Integer.parseInt(ma.group(3));
		}
		catch (NumberFormatException e)
		{
			irc.sendContextReply( mes, "Bad date format: " + time + ".");
			return;
		}

		if (ma.group(4) != null)
		{
			if (ma.group(4).equalsIgnoreCase("pm"))
			{
				if (h<12)
					h+=12;
			}
		}

		cal.set(Calendar.HOUR_OF_DAY, h);
		cal.set(Calendar.MINUTE, m);
		cal.set(Calendar.SECOND, s);

		if (cal.getTimeInMillis() < System.currentTimeMillis())
			cal.add(Calendar.DAY_OF_MONTH, 1);

		TimedEvent timedEvent = new TimedEvent();
		timedEvent.mesID = mods.history.getMessageID(mes);
		timedEvent.synthLevel = mes.getSynthLevel();
		timedEvent.command = command;
		timedEvent.executeAt = cal.getTimeInMillis();

		if (timedEvent.mesID == -1)
		{
			irc.sendContextReply(mes, "Internal error: The message you sent apparently did not exist!");
			return;
		}

		long callbackTime = cal.getTimeInMillis() - System.currentTimeMillis();

		mods.odb.save(timedEvent);
		mods.interval.callBack( timedEvent, callbackTime, -1 );

		irc.sendContextReply(mes, "OK, will do at " + cal.getTime() + ".");
	}

	public long apiDecodePeriod(String time) throws NumberFormatException {
		int period = 0;

		int currentPos = -1;
		int lastPos = 0;

		if ( (currentPos = time.indexOf('d', lastPos)) >= 0 ) {
			period += 60 * 60 * 24 * Integer.valueOf(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if ( (currentPos = time.indexOf('h', lastPos)) >= 0 ) {
			period += 60 * 60 * Integer.valueOf(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if ( (currentPos = time.indexOf('m', lastPos)) >= 0 ) {
			period += 60 * Integer.valueOf(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if ( (currentPos = time.indexOf('s', lastPos)) >= 0 ) {
			period += Integer.valueOf(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if (lastPos != time.length())
			throw new NumberFormatException("Invalid time format: " + time);

		return period;
	}

	public synchronized void interval( Object parameter )
	{
		if (parameter != null && parameter instanceof TimedEvent) {
			// It's a message to be redelivered

			TimedEvent timedEvent = (TimedEvent) parameter;

			Message mes = mods.history.getMessage(timedEvent.mesID);
			if (mes == null)
			{
				System.err.println("Event number " + timedEvent.mesID + " appears to have gone!");
				return;
			}

			String command = irc.getTrigger() + timedEvent.command;

			Message newMes = (Message)mes.cloneEvent(command);
			// Resynth...
			for(int i=0; i<timedEvent.synthLevel; i++)
				newMes = (Message)newMes.cloneEvent(command);

			lastDelivery = timedEvent;

			// Get the plugin/command.

			mods.synthetic.doSyntheticMessage( newMes );

			mods.odb.delete(timedEvent);
		}
	}

	public String[] helpCommandLast = {
		"Find out what the last queued event to be executed was."
	};
	public void commandLast( Message mes, Modules mods, IRCInterface irc )
	{
		if (lastDelivery != null)
		{
			Message lMes = mods.history.getMessage(lastDelivery.mesID);
			if (lMes != null)
				irc.sendContextReply(mes, lMes.getNick() + " queued the following command on " + new Date(lMes.getMillis()).toString() + ": " + lastDelivery.command);
			else
				irc.sendContextReply(mes, "The following command was queued: " + lastDelivery.command);
		}
		else
			irc.sendContextReply(mes, "Nobody queued nuffin', gov'ner.");
	}

}
