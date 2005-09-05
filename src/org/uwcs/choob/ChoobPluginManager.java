/*
 * PluginLoader.java
 *
 * Created on June 13, 2005, 1:25 PM
 */
package org.uwcs.choob;

import org.uwcs.choob.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import org.uwcs.choob.modules.*;
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
	static Map<String,ChoobPluginManager> pluginMap;
	static List<ChoobPluginManager> pluginManagers;

	// Ensure derivative classes have permissions...
	public ChoobPluginManager()
	{
		SecurityManager sm = System.getSecurityManager();
		if (sm != null)
			sm.checkPermission(new ChoobPermission("pluginmanager"));
	}

	public final static void initialise(Modules modules)
	{
		if (mods != null)
			return;
		mods = modules;
		pluginManagers = new LinkedList<ChoobPluginManager>();
		pluginMap = new HashMap<String,ChoobPluginManager>();
	}

	protected abstract Object createPlugin(String pluginName, URL fromLocation) throws ChoobException;
	protected abstract void destroyPlugin(String pluginName) throws ChoobException;

	/**
	 * (Re)loads a plugin from an URL and a plugin name. Note that in the case
	 * of reloading, the old plugin will be disposed of AFTER the new one is
	 * loaded.
	 * @param srcContent String comprising of the plugin's source.
	 * @param pluginName Class name of plugin.
	 * @throws Exception Thrown if there's a syntactical error in the plugin's source.
	 */
	public final void loadPlugin(String pluginName, URL fromLocation) throws ChoobException
	{
		SecurityManager sm = System.getSecurityManager();
		if (sm != null)
			sm.checkPermission(new ChoobPermission("plugin.load."+pluginName));

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
		if (man != null)
			man.destroyPlugin(pluginName);
	}

	public final void unloadPlugin(String pluginName) throws ChoobException
	{
		SecurityManager sm = System.getSecurityManager();
		if (sm != null)
			sm.checkPermission(new ChoobPermission("plugin.unload."+pluginName));

		ChoobPluginManager man;
		synchronized(pluginMap)
		{
			man = pluginMap.remove(pluginName.toLowerCase());
		}
		if (man != null)
			man.destroyPlugin(pluginName);
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
	 * Perform any event handling on the given IRCEvent.
	 * @param ev IRCEvent to pass along.
	 */
	abstract public List<ChoobTask> eventTasks(IRCEvent ev);

	/**
	 * Run any filters on the given Message.
	 * @param ev
	 */
	abstract public List<ChoobTask> filterTasks(Message ev);

	/**
	 * Attempt to perform an API call on a contained plugin.
	 * @param APIName
	 * @param params
	 */
	abstract public Object doAPI(String pluginName, String APIName, Object... params) throws ChoobException;
}

