/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class ChannelInvite extends IRCEvent implements ChannelEvent, UserEvent, AimedEvent
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
	 * target
	 */
	private final String target;


	/**
	 * Construct a new ChannelInvite
	 */
	public ChannelInvite(String methodName, String channel, String nick, String login, String hostname, String target)
	{
		super(methodName);
		this.channel = channel;
		this.nick = nick;
		this.login = login;
		this.hostname = hostname;
		this.target = target;

	}

	/**
	 * Synthesize a new ChannelInvite from an old one.
	 */
	public ChannelInvite(ChannelInvite old)
	{
		super(old);
		this.channel = old.channel;
		this.nick = old.nick;
		this.login = old.login;
		this.hostname = old.hostname;
		this.target = old.target;

	}

	/**
	 * Synthesize a new ChannelInvite from this one.
	 * @returns The new ChannelInvite object.
	 */
	public IRCEvent cloneEvent() {
		return new ChannelInvite(this);
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
	 * Get the value of target
	 * @returns The value of target
	 */
	public String getTarget() {
		return target;
	}



}
