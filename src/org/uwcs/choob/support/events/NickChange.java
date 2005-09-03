/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class NickChange extends IRCEvent implements UserEvent, NickChangeEvent
{
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
	 * newNick
	 */
	private final String newNick;


	/**
	 * Construct a new NickChange
	 */
	public NickChange(String methodName, String nick, String login, String hostname, String newNick)
	{
		super(methodName);
		this.nick = nick;
		this.login = login;
		this.hostname = hostname;
		this.newNick = newNick;

	}

	/**
	 * Synthesize a new NickChange from an old one.
	 */
	public NickChange(NickChange old)
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
	public IRCEvent cloneEvent() {
		return new NickChange(this);
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
	 * Get the value of newNick
	 * @return The value of newNick
	 */
	public String getNewNick() {
		return newNick;
	}



}
