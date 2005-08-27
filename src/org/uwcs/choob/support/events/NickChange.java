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
	 * newNick
	 */
	private String newNick;


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
	 * Get the value of newNick
	 * @returns The value of newNick
	 */
	public String getNewNick() {
		return newNick;
	}



}
