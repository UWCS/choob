/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class ChannelTopic extends IRCEvent implements ChannelEvent, ContextEvent, MessageEvent
{
	/**
	 * channel
	 */
	private final String channel;

	/**
	 * message
	 */
	private final String message;


	/**
	 * Construct a new ChannelTopic
	 */
	public ChannelTopic(String methodName, long millis, int random, String channel, String message)
	{
		super(methodName, millis, random);
		this.channel = channel;
		this.message = message;

	}

	/**
	 * Synthesize a new ChannelTopic from an old one.
	 */
	public ChannelTopic(ChannelTopic old, String message)
	{
		super(old);
		this.channel = old.channel;
		this.message = message;

	}

	/**
	 * Synthesize a new ChannelTopic from this one.
	 * @return The new ChannelTopic object.
	 */
	public IRCEvent cloneEvent(String message) {
		return new ChannelTopic(this, message);
	}

	/**
	 * Get the value of channel
	 * @return The value of channel
	 */
	public String getChannel() {
		return channel;
	}

	/**
	 * Get the value of message
	 * @return The value of message
	 */
	public String getMessage() {
		return message;
	}


	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof ChannelTopic))
			return false;
		if (!super.equals(obj))
			return false;
		ChannelTopic thing = (ChannelTopic)obj;
		if ( true
 && channel.equals(thing.channel) && message.equals(thing.message))
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("ChannelTopic(");
		out.append(super.toString());
		out.append(", channel = " + channel);
		out.append(", message = " + message);
		return out.toString();
	}

	/**
	 * Get the reply context in which this event resides
	 * @return The context
	 */
	public String getContext() {
		return getChannel();
	}

}
