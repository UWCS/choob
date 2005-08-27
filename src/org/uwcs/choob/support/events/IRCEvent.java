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
	private String methodName;

	/**
	 * synthLevel
	 */
	private int synthLevel;

	/**
	 * millis
	 */
	private long millis;

	/**
	 * random
	 */
	private int random;


	/**
	 * Construct a new IRCEvent
	 */
	public IRCEvent(String methodName)
	{

		this.methodName = methodName;
		this.synthLevel = 0;
		this.millis = System.currentTimeMillis();
		this.random = ((int)(Math.random()*127));

	}

	/**
	 * Synthesize a new IRCEvent from an old one.
	 */
	public IRCEvent(IRCEvent old)
	{

		this.methodName = old.methodName;
		this.synthLevel = old.synthLevel + 1;
		this.millis = System.currentTimeMillis();
		this.random = ((int)(Math.random()*127));

	}

	/**
	 * Get the value of methodName
	 * @returns The value of methodName
	 */
	public String getMethodName() {
		return methodName;
	}

	/**
	 * Get the value of synthLevel
	 * @returns The value of synthLevel
	 */
	public int getSynthLevel() {
		return synthLevel;
	}

	/**
	 * Get the value of millis
	 * @returns The value of millis
	 */
	public long getMillis() {
		return millis;
	}

	/**
	 * Get the value of random
	 * @returns The value of random
	 */
	public int getRandom() {
		return random;
	}



}
