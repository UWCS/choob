/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class NickChange extends IRCEvent implements UserEvent, NickChangeEvent
{
	/**
	 * nick
	 */
	private final String nick;

	/**
	 * Get the value of nick
	 * @return The value of nick
	 */
	@Override public String getNick() {
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
	@Override public String getLogin() {
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
	@Override public String getHostname() {
		 return hostname;
	}

	/**
	 * newNick
	 */
	private final String newNick;

	/**
	 * Get the value of newNick
	 * @return The value of newNick
	 */
	@Override public String getNewNick() {
		 return newNick;
	}


	/**
	 * Construct a new NickChange.
	 */
	public NickChange(final String methodName, final long millis, final int random, final String nick, final String login, final String hostname, final String newNick)
	{
		super(methodName, millis, random);
		this.nick = nick;
		this.login = login;
		this.hostname = hostname;
		this.newNick = newNick;
	}

	/**
	 * Synthesize a new NickChange from an old one.
	 */
	public NickChange(final NickChange old)
	{
		super(old);
		this.nick = old.nick;
		this.login = old.login;
		this.hostname = old.hostname;
		this.newNick = old.newNick;
	}

	/**
	 * Synthesize a new NickChange from this one.
	 * @return The new NickChange object.
	 */
	@Override
	public Event cloneEvent()
	{
		return new NickChange(this);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof NickChange))
			return false;
		if ( !super.equals(obj) )
			return false;
		final NickChange thing = (NickChange)obj;
		if ( true && nick.equals(thing.nick) && login.equals(thing.login) && hostname.equals(thing.hostname) && newNick.equals(thing.newNick) )
			return true;
		return false;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("NickChange(");
		out.append(super.toString());
		out.append(", nick = " + nick);
		out.append(", login = " + login);
		out.append(", hostname = " + hostname);
		out.append(", newNick = " + newNick);
		out.append(")");
		return out.toString();
	}

}
