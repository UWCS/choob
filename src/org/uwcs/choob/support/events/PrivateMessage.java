/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class PrivateMessage extends Message implements PrivateEvent, CommandEvent, FilterEvent
{

	/**
	 * Construct a new PrivateMessage
	 */
	public PrivateMessage(String methodName, long millis, int random, String message, String nick, String login, String hostname, String target)
	{
		super(methodName, millis, random, message, nick, login, hostname, target);

	}

	/**
	 * Synthesize a new PrivateMessage from an old one.
	 */
	public PrivateMessage(PrivateMessage old, String message)
	{
		super(old, message);

	}

	/**
	 * Synthesize a new PrivateMessage from this one.
	 * @return The new PrivateMessage object.
	 */
	public IRCEvent cloneEvent(String message) {
		return new PrivateMessage(this, message);
	}


	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof PrivateMessage))
			return false;
		if (!super.equals(obj))
			return false;
		PrivateMessage thing = (PrivateMessage)obj;
		if ( true
)
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("PrivateMessage(");
		out.append(super.toString());
		return out.toString();
	}


}
