/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;

public class PrivateAction extends Message implements PrivateEvent
{

	/**
	 * Construct a new PrivateAction
	 */
	public PrivateAction(String methodName, String message, String nick, String login, String hostname, String target)
	{
		super(methodName, message, nick, login, hostname, target);

	}

	/**
	 * Synthesize a new PrivateAction from an old one.
	 */
	public PrivateAction(PrivateAction old, String message)
	{
		super(old, message);

	}

	/**
	 * Synthesize a new PrivateAction from this one.
	 * @return The new PrivateAction object.
	 */
	public IRCEvent cloneEvent(String message) {
		return new PrivateAction(this, message);
	}



}
