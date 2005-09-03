/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class ChannelAction extends Message implements ChannelEvent
{
	/**
	 * channel
	 */
	private final String channel;


	/**
	 * Construct a new ChannelAction
	 */
	public ChannelAction(String methodName, String message, String nick, String login, String hostname, String target, String channel)
	{
		super(methodName, message, nick, login, hostname, target);
		this.channel = channel;

	}

	/**
	 * Synthesize a new ChannelAction from an old one.
	 */
	public ChannelAction(ChannelAction old, String message)
	{
		super(old, message);
		this.channel = old.channel;

	}

	/**
	 * Synthesize a new ChannelAction from this one.
	 * @return The new ChannelAction object.
	 */
	public IRCEvent cloneEvent(String message) {
		return new ChannelAction(this, message);
	}

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

}
