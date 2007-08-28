/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public interface UserEvent {
	/**
	 * Get the value of nick
	 * 
	 * @return The value of nick
	 */
	public String getNick();

	/**
	 * Get the value of login
	 * 
	 * @return The value of login
	 */
	public String getLogin();

	/**
	 * Get the value of hostname
	 * 
	 * @return The value of hostname
	 */
	public String getHostname();

}