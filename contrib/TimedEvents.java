import org.uwcs.choob.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import org.uwcs.choob.modules.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.SimpleDateFormat;

/**
 * Timed events plugin for Choob
 *
 * @author bucko
 */
public class TimedEvents
{
	private static String lastDelivery=null;
	public void commandIn( Message mes, Modules mods, IRCInterface irc )
	{
		// Stop recursion
		if (mes.getSynthLevel() > 1) {
			irc.sendContextReply(mes, "Synthetic event recursion detected. Stopping.");
			return;
		}

		List<String> params = mods.util.getParams( mes, 2 );

		if (params.size() <= 2) {
			irc.sendContextReply(mes, "Syntax is: in <time> <command>");
			return;
		}

		String time = params.get(1);
		String command = params.get(2);

		int period;
		try {
			// This try/catch doesn't seem to work!
			period = apiDecodePeriod(time);
		} catch (NumberFormatException e) {
			irc.sendContextReply(mes, "Bad time format: " + time + ". Examples: 10h, 5m2s, 3d2h.");
			return;
		}

		// Does the command have a trigger?
		if (!Pattern.compile(irc.getTriggerRegex()).matcher(command).find())
			// No.
			command = irc.getTrigger() + command;

		IRCEvent newMes = mes.cloneEvent( command );

		long callbackTime = period * 1000;

		mods.interval.callBack( newMes, callbackTime );
		System.out.println(System.currentTimeMillis() + callbackTime);
		irc.sendContextReply(mes, "OK, will do at " + new Date(System.currentTimeMillis() + callbackTime) + ".");
	}

	public void commandAt( Message mes, Modules mods, IRCInterface irc )
	{
		// XXX /Lots/ of duplicated code from in.

		List<String> params = mods.util.getParams( mes, 2 );

		if (params.size() <= 2) {
			irc.sendContextReply(mes, "Syntax is: at <time> <command>");
			return;
		}

		String time = params.get(1);
		String command = params.get(2);

		// Does the command have a trigger?
		if (!Pattern.compile(irc.getTriggerRegex()).matcher(command).find())
			// No.
			command = irc.getTrigger() + command;

		IRCEvent newMes = mes.cloneEvent( command );

		// Java--

		GregorianCalendar g = new GregorianCalendar();
		Matcher ma = Pattern.compile("([0-9]|1[0-9]|2[0-3]):([0-5][0-9])(?::([0-5][0-9]))? ?(am|pm)?").matcher(time);
		if (!ma.matches())
		{
			irc.sendContextReply(mes, "Bad date format: " + time + ".");
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
				if (h<=12)
					h+=12;
			}
		}

		g.set(Calendar.HOUR_OF_DAY, h);
		g.set(Calendar.MINUTE, m);
		g.set(Calendar.SECOND, s);

		if (g.getTimeInMillis() < System.currentTimeMillis())
			g.add(Calendar.DAY_OF_MONTH, 1);

		long callbackTime = g.getTimeInMillis() - System.currentTimeMillis();

		mods.interval.callBack( newMes, callbackTime );

		irc.sendContextReply(mes, "OK, will do at " + g.getTime().toString() + ".");
	}

	public int apiDecodePeriod(String time) throws NumberFormatException {
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

	public void interval( Object parameter, Modules mods, IRCInterface irc )
	{
		if (parameter != null && parameter instanceof Message) {
			// It's a message to be redelivered

			lastDelivery=((Message)parameter).getNick() + " queued the following command on " + new Date(((Message)parameter).getMillis()).toString() + ": " + ((Message)parameter).getMessage();

			mods.synthetic.doSyntheticMessage( (Message)parameter);
		}
	}

	public void commandWQT( Message mes, Modules mods, IRCInterface irc )
	{
		irc.sendContextReply(mes, (lastDelivery != null ? lastDelivery : "Nobody queued nuffin', gov'ner."));
	}

}
