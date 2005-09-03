/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class QuitEvent extends IRCEvent implements MessageEvent, UserEvent
{
	/**
	 * message
	 */
	private final String message;

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
	 * Construct a new QuitEvent
	 */
	public QuitEvent(String methodName, String message, String nick, String login, String hostname)
	{
		super(methodName);
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
	public IRCEvent cloneEvent(String message) {
		return new QuitEvent(this, message);
	}

	/**
	 * Get the value of message
	 * @return The value of message
	 */
	public String getMessage() {
		return message;
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



}
