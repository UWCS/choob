/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package org.uwcs.choob.support;

public class ChoobNoSuchCallException extends ChoobException
{
	private String call;
	private String plugin;
	public ChoobNoSuchCallException(String pluginName)
	{
		super("The plugin " + pluginName + " did not exist!");
		this.call = null;
		this.plugin = pluginName;
	}
	public ChoobNoSuchCallException(String pluginName, String call)
	{
		super("The plugin call " + call + " in plugin " + pluginName + " did not exist!");
		this.call = call;
		this.plugin = pluginName;
	}
	public String getCall()
	{
		return call;
	}
	public String getPluginName()
	{
		return plugin;
	}
}
