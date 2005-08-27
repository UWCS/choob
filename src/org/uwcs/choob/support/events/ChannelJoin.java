/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class ChannelJoin extends IRCEvent implements ChannelEvent, ContextEvent, UserEvent
{
	/**
	 * channel
	 */
	private final String channel;

	/**
	 * nick
	 */
	private final String nick;

	/**
	 * login
	 */
	private final String login;

	/**
	 * hostname
	 */
	private final String hostname;


	/**
	 * Construct a new ChannelJoin
	 */
	public ChannelJoin(String methodName, String channel, String nick, String login, String hostname)
	{
		super(methodName);
		this.channel = channel;
		this.nick = nick;
		this.login = login;
		this.hostname = hostname;

	}

	/**
	 * Synthesize a new ChannelJoin from an old one.
	 */
	public ChannelJoin(ChannelJoin old)
	{
		super(old);
		this.channel = old.channel;
		this.nick = old.nick;
		this.login = old.login;
		this.hostname = old.hostname;

	}

	/**
	 * Synthesize a new ChannelJoin from this one.
	 * @returns The new ChannelJoin object.
	 */
	public IRCEvent cloneEvent() {
		return new ChannelJoin(this);
	}

	/**
	 * Get the value of channel
	 * @returns The value of channel
	 */
	public String getChannel() {
		return channel;
	}

	/**
	 * Get the value of nick
	 * @returns The value of nick
	 */
	public String getNick() {
		return nick;
	}

	/**
	 * Get the value of login
	 * @returns The value of login
	 */
	public String getLogin() {
		return login;
	}

	/**
	 * Get the value of hostname
	 * @returns The value of hostname
	 */
	public String getHostname() {
		return hostname;
	}


	/**
	 * Get the reply context in which this event resides
	 * @returns The context
	 */
	public String getContext() {
		return getChannel();
	}

}
