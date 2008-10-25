/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class ChannelPart extends IRCEvent implements ChannelEvent, ContextEvent, UserEvent
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
	 * Get the reply context in which this event resides
	 * @return The context
	 */
	public String getContext() {
		return getChannel();
	}


	/**
	 * Construct a new ChannelPart.
	 */
	public ChannelPart(final String methodName, final long millis, final int random, final String channel, final String nick, final String login, final String hostname)
	{
		super(methodName, millis, random);
		this.channel = channel;
		this.nick = nick;
		this.login = login;
		this.hostname = hostname;
	}

	/**
	 * Synthesize a new ChannelPart from an old one.
	 */
	public ChannelPart(final ChannelPart old)
	{
		super(old);
		this.channel = old.channel;
		this.nick = old.nick;
		this.login = old.login;
		this.hostname = old.hostname;
	}

	/**
	 * Synthesize a new ChannelPart from this one.
	 * @return The new ChannelPart object.
	 */
	@Override
	public Event cloneEvent()
	{
		return new ChannelPart(this);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof ChannelPart))
			return false;
		if ( !super.equals(obj) )
			return false;
		final ChannelPart thing = (ChannelPart)obj;
		if ( true && channel.equals(thing.channel) && nick.equals(thing.nick) && login.equals(thing.login) && hostname.equals(thing.hostname) )
			return true;
		return false;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("ChannelPart(");
		out.append(super.toString());
		out.append(", channel = " + channel);
		out.append(", nick = " + nick);
		out.append(", login = " + login);
		out.append(", hostname = " + hostname);
		out.append(")");
		return out.toString();
	}

}
