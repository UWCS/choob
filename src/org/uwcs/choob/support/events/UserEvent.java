/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public interface UserEvent
{
	/**
	 * Get the value of nick
	 * @returns The value of nick
	 */
	public String getNick();

	/**
	 * Get the value of login
	 * @returns The value of login
	 */
	public String getLogin();

	/**
	 * Get the value of hostname
	 * @returns The value of hostname
	 */
	public String getHostname();


}
