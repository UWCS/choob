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
	private String channel;

	/**
	 * message
	 */
	private String message;


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
	public ChannelTopic(ChannelTopic old)
	{
		super(old);
		this.channel = old.channel;
		this.message = old.message;

	}

	/**
	 * Get the value of channel
	 * @returns The value of channel
	 */
	public String getChannel() {
		return channel;
	}

	/**
	 * Get the value of message
	 * @returns The value of message
	 */
	public String getMessage() {
		return message;
	}


	/**
	 * Get the reply context in which this event resides
	 * @returns The context
	 */
	public String getContext() {
		return getChannel();
	}

}
