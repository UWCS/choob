/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;
import uk.co.uwcs.choob.support.events.*;

public class ChannelInvite extends IRCEvent implements ChannelEvent, UserEvent, AimedEvent
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
	 * nick
	 */
	private final String nick;

	/**
	 * Get the value of nick
	 * @return The value of nick
	 */
	public String getNick() {
		 return nick;
	}

	/**
	 * login
	 */
	private final String login;

	/**
	 * Get the value of login
	 * @return The value of login
	 */
	public String getLogin() {
		 return login;
	}

	/**
	 * hostname
	 */
	private final String hostname;

	/**
	 * Get the value of hostname
	 * @return The value of hostname
	 */
	public String getHostname() {
		 return hostname;
	}

	/**
	 * target
	 */
	private final String target;

	/**
	 * Get the value of target
	 * @return The value of target
	 */
	public String getTarget() {
		 return target;
	}


	/**
	 * Construct a new ChannelInvite.
	 */
	public ChannelInvite(String methodName, long millis, int random, String channel, String nick, String login, String hostname, String target)
	{
		super(methodName, millis, random);
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
	 * @return The new ChannelInvite object.
	 */
	public Event cloneEvent()
	{
		return new ChannelInvite(this);
	}

	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof ChannelInvite))
			return false;
		if ( !super.equals(obj) )
			return false;
		ChannelInvite thing = (ChannelInvite)obj;
		if ( true && channel.equals(thing.channel) && nick.equals(thing.nick) && login.equals(thing.login) && hostname.equals(thing.hostname) && target.equals(thing.target) )
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("ChannelInvite(");
		out.append(super.toString());
		out.append(", channel = " + channel);
		out.append(", nick = " + nick);
		out.append(", login = " + login);
		out.append(", hostname = " + hostname);
		out.append(", target = " + target);
		out.append(")");
		return out.toString();
	}

}
