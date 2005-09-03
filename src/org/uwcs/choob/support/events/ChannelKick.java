/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class ChannelKick extends IRCEvent implements ChannelEvent, ContextEvent, UserEvent, AimedEvent
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
	 * Construct a new ChannelKick
	 */
	public ChannelKick(String methodName, String channel, String nick, String login, String hostname, String target)
	{
		super(methodName);
		this.channel = channel;
		this.nick = nick;
		this.login = login;
		this.hostname = hostname;
		this.target = target;

	}

	/**
	 * Synthesize a new ChannelKick from an old one.
	 */
	public ChannelKick(ChannelKick old)
	{
		super(old);
		this.channel = old.channel;
		this.nick = old.nick;
		this.login = old.login;
		this.hostname = old.hostname;
		this.target = old.target;

	}

	/**
	 * Synthesize a new ChannelKick from this one.
	 * @return The new ChannelKick object.
	 */
	public IRCEvent cloneEvent() {
		return new ChannelKick(this);
	}

	/**
	 * Get the value of channel
	 * @return The value of channel
	 */
	public String getChannel() {
		return channel;
	}

	/**
	 * Get the value of nick
	 * @return The value of nick
	 */
	public String getNick() {
		return nick;
	}

	/**
	 * Get the value of login
	 * @return The value of login
	 */
	public String getLogin() {
		return login;
	}

	/**
	 * Get the value of hostname
	 * @return The value of hostname
	 */
	public String getHostname() {
		return hostname;
	}

	/**
	 * Get the value of target
	 * @return The value of target
	 */
	public String getTarget() {
		return target;
	}


	/**
	 * Get the reply context in which this event resides
	 * @return The context
	 */
	public String getContext() {
		return getChannel();
	}

}
