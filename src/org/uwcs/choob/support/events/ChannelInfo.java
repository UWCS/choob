/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class ChannelInfo extends IRCEvent implements ChannelEvent
{
	/**
	 * channel
	 */
	private final String channel;


	/**
	 * Construct a new ChannelInfo
	 */
	public ChannelInfo(String methodName, String channel)
	{
		super(methodName);
		this.channel = channel;

	}

	/**
	 * Synthesize a new ChannelInfo from an old one.
	 */
	public ChannelInfo(ChannelInfo old)
	{
		super(old);
		this.channel = old.channel;

	}

	/**
	 * Synthesize a new ChannelInfo from this one.
	 * @return The new ChannelInfo object.
	 */
	public IRCEvent cloneEvent() {
		return new ChannelInfo(this);
	}

	/**
	 * Get the value of channel
	 * @return The value of channel
	 */
	public String getChannel() {
		return channel;
	}



}
