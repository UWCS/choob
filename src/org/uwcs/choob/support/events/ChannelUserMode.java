/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class ChannelUserMode extends ChannelMode implements AimedEvent
{
	/**
	 * set
	 */
	private boolean set;

	/**
	 * target
	 */
	private String target;


	/**
	 * Construct a new ChannelUserMode
	 */
	public ChannelUserMode(String methodName, String channel, String mode, boolean set, String target)
	{
		super(methodName, channel, mode, (boolean)set);
		this.set = set;
		this.target = target;

	}

	/**
	 * Synthesize a new ChannelUserMode from an old one.
	 */
	public ChannelUserMode(ChannelUserMode old)
	{
		super(old);
		this.set = old.set;
		this.target = old.target;

	}

	/**
	 * Get the value of set
	 * @returns The value of set
	 */
	public boolean isSet() {
		return set;
	}

	/**
	 * Get the value of target
	 * @returns The value of target
	 */
	public String getTarget() {
		return target;
	}



}
