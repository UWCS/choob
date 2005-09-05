package org.uwcs.choob;

/**
 * Simple base class for tasks that can be queued into Choob.
 * @author bucko
 */

public class ChoobTask implements Runnable
{
	private final String pluginName;
	public ChoobTask(String pluginName)
	{
		this.pluginName = pluginName;
	}

	public String getPluginName()
	{
		return pluginName;
	}

	public void run()
	{
		// Need to implement this...
	}
}
