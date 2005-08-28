/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class ChannelMessage extends Message implements ChannelEvent, CommandEvent
{
	/**
	 * channel
	 */
	private final String channel;


	/**
	 * Construct a new ChannelMessage
	 */
	public ChannelMessage(String methodName, String message, String nick, String login, String hostname, String target, String channel)
	{
		super(methodName, message, nick, login, hostname, target);
		this.channel = channel;

	}

	/**
	 * Synthesize a new ChannelMessage from an old one.
	 */
	public ChannelMessage(ChannelMessage old, String message)
	{
		super(old, message);
		this.channel = old.channel;

	}

	/**
	 * Synthesize a new ChannelMessage from this one.
	 * @returns The new ChannelMessage object.
	 */
	public IRCEvent cloneEvent(String message) {
		return new ChannelMessage(this, message);
	}

	/**
	 * Get the value of channel
	 * @returns The value of channel
	 */
	public String getChannel() {
		return channel;
	}


	/**
	 * Get the reply context in which this event resides
	 * @returns The context
	 */
	public String getContext() {
		return getChannel();
	}

}
