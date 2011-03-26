/*
 * PluginLoader.java
 *
 * Created on June 13, 2005, 1:25 PM
 */
package uk.co.uwcs.choob;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.ChoobNoSuchPluginException;
import uk.co.uwcs.choob.support.events.Event;
import uk.co.uwcs.choob.support.events.Message;

/**
 * Root class of a plugin manager
 * @author bucko
 */
public final class ChoobDistributingPluginManager extends ChoobPluginManager
{
	public ChoobDistributingPluginManager(final Modules mods, final ChoobPluginManagerState state)
	{
		super(mods, state);
	}

	@Override
	protected Object createPlugin(final String pluginName, final URL fromLocation) throws ChoobException
	{
		throw new ChoobException("Cannot load plugins here");
	}

	// Should never be called
	@Override
	protected void destroyPlugin(final String pluginName)
	{}

	/**
	 * Attempts to call a method in the plugin, triggered by a line from IRC.
	 * @param command Command to call.
	 * @param ev Message object from IRC.
	 */
	@Override
	public ChoobTask commandTask(final String plugin, final String command, final Message ev)
	{
		ChoobPluginManager man;
		ChoobTask task = null;
		synchronized(state.pluginMap)
		{
			man = state.pluginMap.get(plugin.toLowerCase());
		}
		if (man != null)
			task = man.commandTask(plugin, command, ev);

		if (task == null)
		{
			// Suggest a task instead?

			task = new ChoobTask(null, "commandTask:suggestions")
			{
				@Override
				public void run()
				{
					String[] lcommands;
					final String notFoundPrefix = "Command " + plugin + "." + command + " not found";
					try
					{
						lcommands = mods.plugin.getPluginCommands(plugin);
						if (lcommands.length == 0)
							state.irc.sendContextReply(ev, notFoundPrefix + ", the plugin doesn't have any commands in it!");
						else if (lcommands.length == 1)
							state.irc.sendContextReply(ev, notFoundPrefix + ", you must have meant " + plugin + "." + lcommands[0] + "?");
						else
						{
							final StringBuilder sb = new StringBuilder(notFoundPrefix).append(", did you mean one of ")
									// Plugin's/pluginnames'
									.append(plugin).append("'").append(plugin.substring(plugin.length()-1, plugin.length()).equalsIgnoreCase("s") ? "" : "s")
									.append(" other commands; ");
							for (int i=0; i<lcommands.length - 1; i++)
								if (i == lcommands.length - 2)
									sb.append(lcommands[i]).append(" or ");
								else
									sb.append(lcommands[i]).append(", ");

							state.irc.sendContextReply(ev, sb.append(lcommands[lcommands.length - 1]).append("?").toString());
						}
					}
					catch (final ChoobNoSuchPluginException e)
					{
						state.irc.sendContextReply(ev, notFoundPrefix + ", the plugin doesn't exist or isn't loaded.");
					}
				}
			};
		}
		return task;
	}

	/**
	 * Run an interval on the given plugin
	 */
	@Override
	public ChoobTask intervalTask(final String pluginName, final Object param)
	{
		ChoobPluginManager man;
		synchronized(state.pluginMap)
		{
			man = state.pluginMap.get(pluginName.toLowerCase());
		}
		if (man != null)
			return man.intervalTask(pluginName, param);
		return null;
	}

	/**
	 * Perform any event handling on the given Event.
	 * @param ev Event to pass along.
	 */
	@Override
	public List<ChoobTask> eventTasks(final Event ev)
	{
		ChoobPluginManager[] mans = new ChoobPluginManager[0];
		synchronized(state.pluginManagers)
		{
			mans = state.pluginManagers.toArray(mans);
		}
		final List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		for(int i=0; i<mans.length; i++)
			if (!(mans[i] instanceof ChoobDistributingPluginManager))
				tasks.addAll(mans[i].eventTasks(ev));
		return tasks;
	}

	/**
	 * Run any filters on the given Message.
	 * @param ev Message to pass along
	 */
	@Override
	public List<ChoobTask> filterTasks(final Message ev)
	{
		ChoobPluginManager[] mans = new ChoobPluginManager[0];
		synchronized(state.pluginManagers)
		{
			mans = state.pluginManagers.toArray(mans);
		}
		final List<ChoobTask> tasks = new LinkedList<ChoobTask>();
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
	@Override
	public Object doAPI(final String pluginName, final String APIName, final Object... params) throws ChoobNoSuchCallException
	{
		ChoobPluginManager man;
		synchronized(state.pluginMap)
		{
			man = state.pluginMap.get(pluginName.toLowerCase());
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
	@Override
	public Object doGeneric(final String pluginName, final String prefix, final String genericName, final Object... params) throws ChoobNoSuchCallException
	{
		ChoobPluginManager man;
		synchronized(state.pluginMap)
		{
			man = state.pluginMap.get(pluginName.toLowerCase());
		}
		if (man != null)
			return man.doGeneric(pluginName, prefix, genericName, params);
		throw new ChoobNoSuchPluginException(pluginName, "generic: " + prefix + ":" + genericName);
	}
}

