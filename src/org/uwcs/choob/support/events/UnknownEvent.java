/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class UnknownEvent extends IRCEvent 
{

	/**
	 * Construct a new UnknownEvent
	 */
	public UnknownEvent(String methodName)
	{
		super(methodName);

	}

	/**
	 * Synthesize a new UnknownEvent from an old one.
	 */
	public UnknownEvent(UnknownEvent old)
	{
		super(old);

	}

	/**
	 * Synthesize a new UnknownEvent from this one.
	 * @returns The new UnknownEvent object.
	 */
	public IRCEvent cloneEvent() {
		return new UnknownEvent(this);
	}



}
