/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class PrivateNotice extends Message implements PrivateEvent
{

	/**
	 * Construct a new PrivateNotice
	 */
	public PrivateNotice(String methodName, long millis, int random, String message, String nick, String login, String hostname, String target)
	{
		super(methodName, millis, random, message, nick, login, hostname, target);

	}

	/**
	 * Synthesize a new PrivateNotice from an old one.
	 */
	public PrivateNotice(PrivateNotice old, String message)
	{
		super(old, message);

	}

	/**
	 * Synthesize a new PrivateNotice from this one.
	 * @return The new PrivateNotice object.
	 */
	public IRCEvent cloneEvent(String message) {
		return new PrivateNotice(this, message);
	}


	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof PrivateNotice))
			return false;
		if (!super.equals(obj))
			return false;
		PrivateNotice thing = (PrivateNotice)obj;
		if ( true
)
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("PrivateNotice(");
		out.append(super.toString());
		return out.toString();
	}


}
