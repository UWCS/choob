/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class Message extends IRCEvent implements MessageEvent, ContextEvent, UserEvent, AimedEvent
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
	 * target
	 */
	private String target;


	/**
	 * Construct a new Message
	 */
	public Message(String methodName, String message, String nick, String login, String hostname, String target)
	{
		super(methodName);
		this.message = message;
		this.nick = nick;
		this.login = login;
		this.hostname = hostname;
		this.target = target;

	}

	/**
	 * Synthesize a new Message from an old one.
	 */
	public Message(Message old)
	{
		super(old);
		this.message = old.message;
		this.nick = old.nick;
		this.login = old.login;
		this.hostname = old.hostname;
		this.target = old.target;

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

	/**
	 * Get the value of target
	 * @returns The value of target
	 */
	public String getTarget() {
		return target;
	}


	/**
	 * Get the reply context in which this event resides
	 * @returns The context
	 */
	public String getContext() {
		return getNick();
	}

}
