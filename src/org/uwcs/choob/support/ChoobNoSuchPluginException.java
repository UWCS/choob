/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package org.uwcs.choob.support;

public class ChoobNoSuchPluginException extends ChoobNoSuchCallException
{
	private String plugin;
	public ChoobNoSuchPluginException(String plugin)
	{
		super("The plugin " + plugin + "was not loaded!");
		this.plugin = plugin;
	}
	public String getPlugin()
	{
		return plugin;
	}
}
