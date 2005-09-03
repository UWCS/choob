/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;

public class ChannelMode extends IRCEvent implements ChannelEvent, ModeEvent
{
	/**
	 * channel
	 */
	private final String channel;

	/**
	 * mode
	 */
	private final String mode;

	/**
	 * set
	 */
	private final boolean set;


	/**
	 * Construct a new ChannelMode
	 */
	public ChannelMode(String methodName, String channel, String mode, boolean set)
	{
		super(methodName);
		this.channel = channel;
		this.mode = mode;
		this.set = set;

	}

	/**
	 * Synthesize a new ChannelMode from an old one.
	 */
	public ChannelMode(ChannelMode old)
	{
		super(old);
		this.channel = old.channel;
		this.mode = old.mode;
		this.set = old.set;

	}

	/**
	 * Synthesize a new ChannelMode from this one.
	 * @return The new ChannelMode object.
	 */
	public IRCEvent cloneEvent() {
		return new ChannelMode(this);
	}

	/**
	 * Get the value of channel
	 * @return The value of channel
	 */
	public String getChannel() {
		return channel;
	}

	/**
	 * Get the value of mode
	 * @return The value of mode
	 */
	public String getMode() {
		return mode;
	}

	/**
	 * Get the value of set
	 * @return The value of set
	 */
	public boolean isSet() {
		return set;
	}



}
