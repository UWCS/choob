import org.uwcs.choob.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.modules.*;
import java.net.*;
import java.io.*;

/**
 * Timed events plugin for Choob
 *
 * @author bucko
 */
class TimedEvents
{
	public void create( Modules mods )
	{
		/* Loader shell
		Object[] saved = mods.db.get(SavedTimedEvent.class, blah);
		for(Object event: saved)
		{
			SavedTimedEvent foo = (SavedTimedEvent)saved;
			addTimedEvent(foo);
		} */
	}

	public void commandIn( Message mes, Modules mods, IRCInterface irc )
	{
		// Stop recursion
		if (mes instanceof SyntheticMessage) {
			if (((SyntheticMessage)mes).getSynthLevel() > 1) {
				irc.sendContextReply(mes, "Synthetic event recursion detected. Stopping.");
				return;
			}
		}

		List params = mods.util.getParms( mes, 2 );

		if (params.size() <= 2) {
			irc.sendContextReply(mes, "Syntax is: in <time> <command>");
			return;
		}

		String time = (String)params.get(1);
		String command = (String)params.get(2);

		int period;
		try {
			// This try/catch doesn't seem to work!
			period = apiDecodePeriod(time);
		} catch (NumberFormatException e) {
			irc.sendContextReply(mes, "Bad time format: " + time + ". Examples: 10h, 5m2s, 3d2h.");
			return;
		}

		SyntheticMessage newMes = new SyntheticMessage(mes);
		newMes.setText( "~" + command ); // XXX hack

		Date callbackTime = new Date( (new Date().getTime()) + period * 1000);

		mods.interval.callBack( this, newMes, callbackTime );
		irc.sendContextReply(mes, "OK, will do in " + period + "secs.");
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
			mods.synthetic.doSyntheticMessage((SyntheticMessage) parameter);
		}
	}
}
