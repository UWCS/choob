/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;

public class ChoobInvocationError extends ChoobError
{
	private static final long serialVersionUID = -8918372305516669806L;
	private final String call;
	private final String plugin;
	public ChoobInvocationError(final String pluginName, final String call, final Throwable e)
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
