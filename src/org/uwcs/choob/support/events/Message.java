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
	 * target
	 */
	private final String target;


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
	public Message(Message old, String message)
	{
		super(old);
		this.message = message;
		this.nick = old.nick;
		this.login = old.login;
		this.hostname = old.hostname;
		this.target = old.target;

	}

	/**
	 * Synthesize a new Message from this one.
	 * @return The new Message object.
	 */
	public IRCEvent cloneEvent(String message) {
		return new Message(this, message);
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
		return getNick();
	}

}
