/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public interface PluginEvent
{
	/**
	 * Get the value of pluginName
	 * @return The value of pluginName
	 */
	public String getPluginName();

	/**
	 * Get the value of pluginStatus
	 * @return The value of pluginStatus
	 */
	public int getPluginStatus();

}