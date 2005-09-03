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
	public ChannelTopic(String methodName, String channel, String message)
	{
		super(methodName);
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


	/**
	 * Get the reply context in which this event resides
	 * @return The context
	 */
	public String getContext() {
		return getChannel();
	}

}
