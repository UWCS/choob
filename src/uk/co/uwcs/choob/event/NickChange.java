/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.event;

public class NickChange extends IRCEvent implements UserEvent, NickChangeEvent {
	/**
	 * nick
	 */
	private final String nick;

	/**
	 * Get the value of nick
	 * 
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
	 * 
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
	 * 
	 * @return The value of hostname
	 */
	public String getHostname() {
		return hostname;
	}

	/**
	 * newNick
	 */
	private final String newNick;

	/**
	 * Get the value of newNick
	 * 
	 * @return The value of newNick
	 */
	public String getNewNick() {
		return newNick;
	}

	/**
	 * Construct a new NickChange.
	 */
	public NickChange(String methodName, long millis, int random, String nick,
			String login, String hostname, String newNick) {
		super(methodName, millis, random);
		this.nick = nick;
		this.login = login;
		this.hostname = hostname;
		this.newNick = newNick;
	}

	/**
	 * Synthesize a new NickChange from an old one.
	 */
	public NickChange(NickChange old) {
		super(old);
		this.nick = old.nick;
		this.login = old.login;
		this.hostname = old.hostname;
		this.newNick = old.newNick;
	}

	/**
	 * Synthesize a new NickChange from this one.
	 * 
	 * @return The new NickChange object.
	 */
	public Event cloneEvent() {
		return new NickChange(this);
	}

	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof NickChange))
			return false;
		if (!super.equals(obj))
			return false;
		NickChange thing = (NickChange) obj;
		if (true && nick.equals(thing.nick) && login.equals(thing.login)
				&& hostname.equals(thing.hostname)
				&& newNick.equals(thing.newNick))
			return true;
		return false;
	}

	public String toString() {
		StringBuffer out = new StringBuffer("NickChange(");
		out.append(super.toString());
		out.append(", nick = " + nick);
		out.append(", login = " + login);
		out.append(", hostname = " + hostname);
		out.append(", newNick = " + newNick);
		out.append(")");
		return out.toString();
	}

}
