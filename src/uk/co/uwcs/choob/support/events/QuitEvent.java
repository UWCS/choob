/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class QuitEvent extends IRCEvent implements MessageEvent, UserEvent
{
	/**
	 * message
	 */
	private final String message;

	/**
	 * Get the value of message
	 * @return The value of message
	 */
	public String getMessage() {
		 return message;
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
	 * Construct a new QuitEvent.
	 */
	public QuitEvent(String methodName, long millis,
			String message, String nick, String login, String hostname) {
		super(methodName, millis);
		this.message = message;
		this.nick = nick;
		this.login = login;
		this.hostname = hostname;
	}

	/**
	 * Synthesize a new QuitEvent from an old one.
	 */
	public QuitEvent(QuitEvent old, String message)
	{
		super(old);
		this.message = message;
		this.nick = old.nick;
		this.login = old.login;
		this.hostname = old.hostname;
	}

	/**
	 * Synthesize a new QuitEvent from this one.
	 * @return The new QuitEvent object.
	 */
	public Event cloneEvent(String message)
	{
		return new QuitEvent(this, message);
	}

	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof QuitEvent))
			return false;
		if ( !super.equals(obj) )
			return false;
		QuitEvent thing = (QuitEvent)obj;
		if ( true && message.equals(thing.message) && nick.equals(thing.nick) && login.equals(thing.login) && hostname.equals(thing.hostname) )
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("QuitEvent(");
		out.append(super.toString());
		out.append(", message = " + message);
		out.append(", nick = " + nick);
		out.append(", login = " + login);
		out.append(", hostname = " + hostname);
		out.append(")");
		return out.toString();
	}

}
