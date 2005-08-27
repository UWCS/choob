/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class PrivateMessage extends Message implements PrivateEvent
{

	/**
	 * Construct a new PrivateMessage
	 */
	public PrivateMessage(String methodName, String message, String nick, String login, String hostname, String target)
	{
		super(methodName, message, nick, login, hostname, target);

	}

	/**
	 * Synthesize a new PrivateMessage from an old one.
	 */
	public PrivateMessage(PrivateMessage old)
	{
		super(old);

	}



}
