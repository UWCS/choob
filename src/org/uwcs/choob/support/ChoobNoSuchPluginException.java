/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package org.uwcs.choob.support;

public class ChoobNoSuchPluginException extends ChoobNoSuchCallException
{
	String plugin;
	public ChoobNoSuchPluginException(String plugin)
	{
		super("The plugin " + plugin + " was not loaded!");
		this.plugin = plugin;
	}

	public ChoobNoSuchPluginException(String plugin, String call)
	{
		super("The plugin " + plugin + " was not loaded, trying to call " + call + "!");
		this.plugin = plugin;
		this.call=call;
	}

	public String getPlugin()
	{
		return plugin;
	}
}
