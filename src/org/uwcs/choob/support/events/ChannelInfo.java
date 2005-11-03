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
	 * Get the value of channel
	 * @return The value of channel
	 */
	public String getChannel() {
		 return channel;
	}


	/**
	 * Construct a new ChannelInfo.
	 */
	public ChannelInfo(String methodName, long millis, int random, String channel)
	{
		super(methodName, millis, random);
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
	public Event cloneEvent()
	{
		return new ChannelInfo(this);
	}

	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof ChannelInfo))
			return false;
		if ( !super.equals(obj) )
			return false;
		ChannelInfo thing = (ChannelInfo)obj;
		if ( true && channel.equals(thing.channel) )
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("ChannelInfo(");
		out.append(super.toString());
		out.append(", channel = " + channel);
		out.append(")");
		return out.toString();
	}

}
