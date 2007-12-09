/*
 * PluginLoader.java
 *
 * Created on June 13, 2005, 1:25 PM
 */
package uk.co.uwcs.choob;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.Event;
import uk.co.uwcs.choob.support.events.Message;

/**
 * Root class of a plugin manager
 * @author bucko
 */
public final class ChoobDistributingPluginManager extends ChoobPluginManager
{
	public ChoobDistributingPluginManager()
	{
		super();
	}

	protected Object createPlugin(String pluginName, URL fromLocation) throws ChoobException
	{
		throw new ChoobException("Cannot load plugins here");
	}

	// Should never be called
	protected void destroyPlugin(String pluginName)
	{}

	/**
	 * Attempts to call a method in the plugin, triggered by a line from IRC.
	 * @param command Command to call.
	 * @param ev Message object from IRC.
	 */
	public ChoobTask commandTask(final String plugin, final String command, final Message ev)
	{
		ChoobPluginManager man;
		ChoobTask task = null;
		synchronized(pluginMap)
		{
			man = pluginMap.get(plugin.toLowerCase());
		}
		if (man != null)
			task = man.commandTask(plugin, command, ev);

		if (task == null)
		{
			// Suggest a task instead?

			task = new ChoobTask(null, "commandTask:suggestions")
			{
				public void run()
				{
					String[] commands;
					final String notFoundPrefix = "Command " + plugin + "." + command + " not found";
					try
					{
						commands = mods.plugin.getPluginCommands(plugin);
						if (commands.length == 0)
							irc.sendContextReply(ev, notFoundPrefix + ", the plugin doesn't have any commands in it!");
						else if (commands.length == 1)
							irc.sendContextReply(ev, notFoundPrefix + ", you must have meant " + plugin + "." + commands[0] + "?");
						else
						{
							StringBuilder sb = new StringBuilder(notFoundPrefix).append(", did you mean one of ")
									// Plugin's/pluginnames'
									.append(plugin).append("'").append(plugin.substring(plugin.length()-1, plugin.length()).equalsIgnoreCase("s") ? "" : "s")
									.append(" other commands; ");
							for (int i=0; i<commands.length - 1; i++)
								if (i == commands.length - 2)
									sb.append(commands[i]).append(" or ");
								else
									sb.append(commands[i]).append(", ");
							
							irc.sendContextReply(ev, sb.append(commands[commands.length - 1]).append("?").toString());
						}
					}
					catch (ChoobNoSuchPluginException e)
					{
						irc.sendContextReply(ev, notFoundPrefix + ", the plugin doesn't exist or isn't loaded.");
					}
				}
			};
		}
		return task;
	}

	/**
	 * Run an interval on the given plugin
	 */
	public ChoobTask intervalTask(String pluginName, Object param)
	{
		ChoobPluginManager man;
		synchronized(pluginMap)
		{
			man = pluginMap.get(pluginName.toLowerCase());
		}
		if (man != null)
			return man.intervalTask(pluginName, param);
		return null;
	}

	/**
	 * Perform any event handling on the given Event.
	 * @param ev Event to pass along.
	 */
	public List<ChoobTask> eventTasks(Event ev)
	{
		ChoobPluginManager[] mans = new ChoobPluginManager[0];
		synchronized(pluginManagers)
		{
			mans = pluginManagers.toArray(mans);
		}
		List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		for(int i=0; i<mans.length; i++)
			if (!(mans[i] instanceof ChoobDistributingPluginManager))
				tasks.addAll(mans[i].eventTasks(ev));
		return tasks;
	}

	/**
	 * Run any filters on the given Message.
	 * @param ev Message to pass along
	 */
	public List<ChoobTask> filterTasks(Message ev)
	{
		ChoobPluginManager[] mans = new ChoobPluginManager[0];
		synchronized(pluginManagers)
		{
			mans = pluginManagers.toArray(mans);
		}
		List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		for(int i=0; i<mans.length; i++)
			if (!(mans[i] instanceof ChoobDistributingPluginManager))
				tasks.addAll(mans[i].filterTasks(ev));
		return tasks;
	}

	/**
	 * Attempt to perform an API call on a contained plugin.
	 * @param APIName The name of the API call.
	 * @param params The parameters to pass.
	 */
	public Object doAPI(String pluginName, String APIName, Object... params) throws ChoobNoSuchCallException
	{
		ChoobPluginManager man;
		synchronized(pluginMap)
		{
			man = pluginMap.get(pluginName.toLowerCase());
		}
		if (man != null)
			return man.doAPI(pluginName, APIName, params);
		throw new ChoobNoSuchPluginException(pluginName, "api: " + APIName);
	}

	/**
	 * Attempt to perform an generic call on a contained plugin.
	 * @param prefix The prefix (ie call type) of the call.
	 * @param genericName The name of the call.
	 * @param params Params to pass.
	 */
	public Object doGeneric(String pluginName, String prefix, String genericName, Object... params) throws ChoobNoSuchCallException
	{
		ChoobPluginManager man;
		synchronized(pluginMap)
		{
			man = pluginMap.get(pluginName.toLowerCase());
		}
		if (man != null)
			return man.doGeneric(pluginName, prefix, genericName, params);
		throw new ChoobNoSuchPluginException(pluginName, "generic: " + prefix + ":" + genericName);
	}
}

