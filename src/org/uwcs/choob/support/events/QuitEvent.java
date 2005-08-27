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
	private String message;

	/**
	 * nick
	 */
	private String nick;

	/**
	 * login
	 */
	private String login;

	/**
	 * hostname
	 */
	private String hostname;


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
	public QuitEvent(QuitEvent old)
	{
		super(old);
		this.message = old.message;
		this.nick = old.nick;
		this.login = old.login;
		this.hostname = old.hostname;

	}

	/**
	 * Get the value of message
	 * @returns The value of message
	 */
	public String getMessage() {
		return message;
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



}
