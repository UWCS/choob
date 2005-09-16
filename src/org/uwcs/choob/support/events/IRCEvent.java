/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class IRCEvent  
{
	/**
	 * methodName
	 */
	private final String methodName;

	/**
	 * millis
	 */
	private final long millis;

	/**
	 * random
	 */
	private final int random;

	/**
	 * synthLevel
	 */
	private final int synthLevel;


	/**
	 * Construct a new IRCEvent
	 */
	public IRCEvent(String methodName, long millis, int random)
	{

		java.security.AccessController.checkPermission(new org.uwcs.choob.support.ChoobPermission("event.create"));
		this.methodName = methodName;
		this.millis = millis;
		this.random = random;
		this.synthLevel = 0;

	}

	/**
	 * Synthesize a new IRCEvent from an old one.
	 */
	public IRCEvent(IRCEvent old)
	{

		java.security.AccessController.checkPermission(new org.uwcs.choob.support.ChoobPermission("event.create"));
		this.methodName = old.methodName;
		this.millis = old.millis;
		this.random = old.random;
		this.synthLevel = old.synthLevel + 1;

	}

	/**
	 * Synthesize a new IRCEvent from this one.
	 * @return The new IRCEvent object.
	 */
	public IRCEvent cloneEvent() {
		return new IRCEvent(this);
	}

	/**
	 * Get the value of methodName
	 * @return The value of methodName
	 */
	public String getMethodName() {
		return methodName;
	}

	/**
	 * Get the value of millis
	 * @return The value of millis
	 */
	public long getMillis() {
		return millis;
	}

	/**
	 * Get the value of random
	 * @return The value of random
	 */
	public int getRandom() {
		return random;
	}

	/**
	 * Get the value of synthLevel
	 * @return The value of synthLevel
	 */
	public int getSynthLevel() {
		return synthLevel;
	}


	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof IRCEvent))
			return false;
		IRCEvent thing = (IRCEvent)obj;
		if ( true
 && methodName.equals(thing.methodName) && (millis == thing.millis) && (random == thing.random) && (synthLevel == thing.synthLevel))
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("IRCEvent(");
		out.append(", methodName = " + methodName);
		out.append(", millis = " + millis);
		out.append(", random = " + random);
		out.append(", synthLevel = " + synthLevel);
		return out.toString();
	}


}
