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
	 * Get the value of channel
	 * @return The value of channel
	 */
	public String getChannel() {
		 return channel;
	}

	/**
	 * modes
	 */
	private final String modes;

	/**
	 * Get the value of modes
	 * @return The value of modes
	 */
	public String getModes() {
		 return modes;
	}


	/**
	 * Construct a new ChannelModes.
	 */
	public ChannelModes(String methodName, long millis, int random, String channel, String modes)
	{
		super(methodName, millis, random);
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
	public Event cloneEvent()
	{
		return new ChannelModes(this);
	}

	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof ChannelModes))
			return false;
		if ( !super.equals(obj) )
			return false;
		ChannelModes thing = (ChannelModes)obj;
		if ( true && channel.equals(thing.channel) && modes.equals(thing.modes) )
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("ChannelModes(");
		out.append(super.toString());
		out.append(", channel = " + channel);
		out.append(", modes = " + modes);
		out.append(")");
		return out.toString();
	}

}
