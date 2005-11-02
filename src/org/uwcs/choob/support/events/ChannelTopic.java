/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;

public class ChannelTopic extends IRCEvent implements MessageEvent, ChannelEvent, ContextEvent
{
	/**
	 * message
	 */
	private final String message;

	/**
	 * Get the value of message
	 * @return The value of message
	 */
	public String getMessage() {
		 return message;
	}

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
	 * Get the reply context in which this event resides
	 * @return The context
	 */
	public String getContext() {
		return getChannel();
	}


	/**
	 * Construct a new ChannelTopic.
	 */
	public ChannelTopic(String methodName, long millis, int random, String message, String channel)
	{
		super(methodName, millis, random);
		this.message = message;
		this.channel = channel;
	}

	/**
	 * Synthesize a new ChannelTopic from an old one.
	 */
	public ChannelTopic(ChannelTopic old, String message)
	{
		super(old);
		this.message = message;
		this.channel = old.channel;
	}

	/**
	 * Synthesize a new ChannelTopic from this one.
	 * @return The new ChannelTopic object.
	 */
	public IRCEvent cloneEvent(String message)
	{
		return new ChannelTopic(this, message);
	}

	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof ChannelTopic))
			return false;
		if ( !super.equals(obj) )
			return false;
		ChannelTopic thing = (ChannelTopic)obj;
		if ( true && message.equals(thing.message) && channel.equals(thing.channel) )
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("ChannelTopic(");
		out.append(super.toString());
		out.append(", message = " + message);
		out.append(", channel = " + channel);
		out.append(")");
		return out.toString();
	}

}
