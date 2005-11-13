/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package org.uwcs.choob.support;

public class ChoobInvocationError extends ChoobError
{
	private String call;
	private String plugin;
	public ChoobInvocationError(String pluginName, String call, Throwable e)
	{
		super("The plugin call " + call + " in plugin " + pluginName + " threw an exception: " + e, e);
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
