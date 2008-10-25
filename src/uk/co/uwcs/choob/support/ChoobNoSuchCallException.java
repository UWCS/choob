/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;

public class ChoobNoSuchCallException extends ChoobException
{
	private static final long serialVersionUID = 845675151915780032L;
	private final String call;
	private final String plugin;
	public ChoobNoSuchCallException(final String pluginName)
	{
		super("Tried to call some part of plugin " + pluginName + ", but the plugin isn't loaded!");
		this.call = null;
		this.plugin = pluginName;
	}
	public ChoobNoSuchCallException(final String pluginName, final String call)
	{
		super("Tried to call " + call + " in plugin " + pluginName + ", but the the call didn't exist!");
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
