/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;

public class ChannelModes extends IRCEvent implements ChannelEvent, MultiModeEvent
{
	/**
	 * channel
	 */
	private final String channel;

	/**
	 * modes
	 */
	private final String modes;


	/**
	 * Construct a new ChannelModes
	 */
	public ChannelModes(String methodName, String channel, String modes)
	{
		super(methodName);
		this.channel = channel;
		this.modes = modes;

	}

	/**
	 * Synthesize a new ChannelModes from an old one.
	 */
	public ChannelModes(ChannelModes old)
	{
		super(old);
		this.channel = old.channel;
		this.modes = old.modes;

	}

	/**
	 * Synthesize a new ChannelModes from this one.
	 * @return The new ChannelModes object.
	 */
	public IRCEvent cloneEvent() {
		return new ChannelModes(this);
	}

	/**
	 * Get the value of channel
	 * @return The value of channel
	 */
	public String getChannel() {
		return channel;
	}

	/**
	 * Get the value of modes
	 * @return The value of modes
	 */
	public String getModes() {
		return modes;
	}



}
