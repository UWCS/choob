package uk.co.uwcs.choob;

/**
 * Simple base class for tasks that can be queued into Choob.
 * @author bucko
 */

public class ChoobTask implements Runnable
{
	private final String systemFunction;
	private final String pluginName;
	
	public ChoobTask(String pluginName)
	{
		this.pluginName = pluginName;
		this.systemFunction = null;
	}
	
	public ChoobTask(String pluginName, String systemFunction)
	{
		this.pluginName = pluginName;
		this.systemFunction = systemFunction;
	}
	
	public String getPluginName()
	{
		return pluginName;
	}
	
	public String getSystemFunction()
	{
		return systemFunction;
	}
	
	public void run()
	{
		// Need to implement this...
	}
}
