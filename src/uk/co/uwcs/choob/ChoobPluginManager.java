/*
 * PluginLoader.java
 *
 * Created on June 13, 2005, 1:25 PM
 */
package uk.co.uwcs.choob;

import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import uk.co.uwcs.choob.modules.*;
import java.util.*;
import bsh.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.reflect.*;
import java.util.regex.*;
import java.security.*;

/**
 * Root class of a plugin manager
 * @author bucko
 */
public abstract class ChoobPluginManager
{
	static Modules mods;
	static IRCInterface irc;
	static Map<String,ChoobPluginManager> pluginMap;
	static Map<String,List<String>> commands;
	static List<ChoobPluginManager> pluginManagers;
	static SpellDictionaryChoob phoneticCommands;

	// Ensure derivative classes have permissions...
	public ChoobPluginManager()
	{
		AccessController.checkPermission(new ChoobPermission("root"));
	}

	public final static void initialise(Modules modules, IRCInterface irc)
	{
		if (mods != null)
			return;
		mods = modules;
		ChoobPluginManager.irc = irc;
		pluginManagers = new LinkedList<ChoobPluginManager>();
		pluginMap = new HashMap<String,ChoobPluginManager>();
		commands = new HashMap<String,List<String>>();
		File transFile = new File("lib/en_phonet.dat");
		try
		{
			phoneticCommands = new SpellDictionaryChoob(transFile);
		}
		catch (IOException e)
		{
			System.err.println("Could not load phonetics file: " + transFile);
			throw new RuntimeException("Couldn't load phonetics file", e);
		}
	}

	protected abstract Object createPlugin(String pluginName, URL fromLocation) throws ChoobException;
	protected abstract void destroyPlugin(String pluginName);

	/**
	 * (Re)loads a plugin from an URL and a plugin name. Note that in the case
	 * of reloading, the old plugin will be disposed of AFTER the new one is
	 * loaded.
	 * @param pluginName Class name of plugin.
	 * @param fromLocation URL from which to get the plugin's contents.
	 * @return true if the plugin was reloaded
	 * @throws Exception Thrown if there's a syntactical error in the plugin's source.
	 */
	public final boolean loadPlugin(String pluginName, URL fromLocation) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("plugin.load."+pluginName));

		// Make sure we're ready to add commands.
		if (commands.get(pluginName.toLowerCase()) == null)
			commands.put(pluginName.toLowerCase(), new ArrayList<String>());

		Object pluginObj = createPlugin(pluginName, fromLocation);

		// Now plugin is loaded with no problems. Install it.

		// XXX Possible problem with double loading here. Shouldn't matter,
		// though.

		ChoobPluginManager man;
		synchronized(pluginMap)
		{
			man = pluginMap.remove(pluginName.toLowerCase());
			pluginMap.put(pluginName.toLowerCase(), this);
		}
		synchronized(pluginManagers)
		{
			if (!pluginManagers.contains(this))
				pluginManagers.add(this);
		}
		if (man != null && man != this)
			man.destroyPlugin(pluginName);

		// If man existed, so did the plugin.
		if (man != null)
			return true;
		else
			return false;
	}

	public final void unloadPlugin(String pluginName) throws ChoobNoSuchPluginException
	{
		AccessController.checkPermission(new ChoobPermission("plugin.unload." + pluginName));

		ChoobPluginManager man;
		synchronized(pluginMap)
		{
			man = pluginMap.remove(pluginName.toLowerCase());
		}
		if (man != null)
			man.destroyPlugin(pluginName);
		else
			throw new ChoobNoSuchPluginException(pluginName, "UNLOAD");
	}

	/**
	 * Get a list of plugins.
	 */
	public final String[] plugins()
	{
		synchronized(pluginMap)
		{
			Set<String> keys = pluginMap.keySet();
			String[] ret = new String[keys.size()];
			return (String[])keys.toArray(ret);
		}
	}

	/**
	 * Get a list of commands in a plugin.
	 */
	public final String[] commands(String pluginName)
	{
		synchronized(commands)
		{
			List<String> coms = commands.get(pluginName.toLowerCase());
			if (coms == null)
				return null;
			String[] ret = new String[coms.size()];
			return (String[])coms.toArray(ret);
		}
	}

	/**
	 * Adds a command to the internal database.
	 */
	public final void addCommand(String pluginName, String commandName)
	{
		synchronized(phoneticCommands)
		{
			if (pluginName != null)
				phoneticCommands.addWord((pluginName + "." + commandName).toLowerCase());
			else
				phoneticCommands.removeWord(commandName.toLowerCase());
		}
		synchronized(commands)
		{
			if (pluginName != null)
				commands.get(pluginName.toLowerCase()).add(commandName);
			else
				commands.get("").add(commandName);
		}
	}

	/**
	 * Removes a command from the internal database.
	 */
	public final void removeCommand(String pluginName, String commandName)
	{
		synchronized(phoneticCommands)
		{
			if (pluginName != null)
				phoneticCommands.removeWord((pluginName + "." + commandName).toLowerCase());
			else
				phoneticCommands.removeWord(commandName.toLowerCase());
		}
		synchronized(commands)
		{
			if (pluginName != null)
				commands.get(pluginName.toLowerCase()).remove(commandName);
			else
				commands.get("").remove(commandName);
		}
	}

	/**
	 * Remove a command from the internal database. Use the two parameter
	 * version in preference to this!
	 */
	public final void removeCommand(String commandName)
	{
		Matcher ma = Pattern.compile("(\\w+)\\.(\\w+)").matcher(commandName);
		if (ma.matches())
			removeCommand(ma.group(1), ma.group(2));
		else
			removeCommand(null, commandName);
	}

	/**
	 * Add a command to the internal database. Use the two parameter
	 * version in preference to this!
	 */
	public final void addCommand(String commandName)
	{
		Matcher ma = Pattern.compile("(\\w+)\\.(\\w+)").matcher(commandName);
		if (ma.matches())
			addCommand(ma.group(1), ma.group(2));
		else
			addCommand(null, commandName);
	}

	public final static ProtectionDomain getProtectionDomain( String pluginName )
	{
		return mods.security.getProtectionDomain( pluginName );
	}

	// TODO make these return ChoobTask[], and implement a spawnCommand
	// etc. method to queue the tasks.

	/**
	 * Attempts to call a method in the plugin, triggered by a line from IRC.
	 * @param command Command to call.
	 * @param ev Message object from IRC.
	 */
	abstract public ChoobTask commandTask(String plugin, String command, Message ev);

	/**
	 * Run an interval on the given plugin
	 */
	abstract public ChoobTask intervalTask(String pluginName, Object param);

	/**
	 * Perform any event handling on the given Event.
	 * @param ev Event to pass along.
	 */
	abstract public List<ChoobTask> eventTasks(Event ev);

	/**
	 * Run any filters on the given Message.
	 * @param ev Message to pass along.
	 */
	abstract public List<ChoobTask> filterTasks(Message ev);

	/**
	 * Attempt to perform an API call on a contained plugin.
	 * @param APIName The name of the API call.
	 * @param params Params to pass through.
	 * @throws ChoobNoSuchCallException when the call didn't exist.
	 * @throws ChoobInvocationError when the call threw an exception.
	 */
	abstract public Object doAPI(String pluginName, String APIName, Object... params) throws ChoobNoSuchCallException;

	/**
	 * Attempt to perform an API call on a contained plugin.
	 * @param prefix The prefix (ie. type) of call.
	 * @param genericName The name of the call.
	 * @param params Params to pass through.
	 * @throws ChoobNoSuchCallException when the call didn't exist.
	 * @throws ChoobInvocationError when the call threw an exception.
	 */
	abstract public Object doGeneric(String pluginName, String prefix, String genericName, Object... params) throws ChoobNoSuchCallException;
}

