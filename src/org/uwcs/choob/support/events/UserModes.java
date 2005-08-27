/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class UserModes extends IRCEvent implements MultiModeEvent
{
	/**
	 * modes
	 */
	private String modes;


	/**
	 * Construct a new UserModes
	 */
	public UserModes(String methodName, String modes)
	{
		super(methodName);
		this.modes = modes;

	}

	/**
	 * Synthesize a new UserModes from an old one.
	 */
	public UserModes(UserModes old)
	{
		super(old);
		this.modes = old.modes;

	}

	/**
	 * Get the value of modes
	 * @returns The value of modes
	 */
	public String getModes() {
		return modes;
	}



}