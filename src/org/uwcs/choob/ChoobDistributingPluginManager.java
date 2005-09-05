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
public class ChoobDistributingPluginManager extends ChoobPluginManager
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
	protected void destroyPlugin(String pluginName) throws ChoobException
	{}

	/**
	 * Attempts to call a method in the plugin, triggered by a line from IRC.
	 * @param command Command to call.
	 * @param ev Message object from IRC.
	 */
	public ChoobTask commandTask(String plugin, String command, Message ev)
	{
		ChoobPluginManager man;
		synchronized(pluginMap)
		{
			man = pluginMap.get(plugin);
		}
		if (man != null)
		{
			System.out.println("Shelling to " + man);
			return man.commandTask(plugin, command, ev);
		}
		System.out.println("No manager for " + plugin);
		return null;
	}

	/**
	 * Run an interval on the given plugin
	 */
	public ChoobTask intervalTask(String pluginName, Object param)
	{
		ChoobPluginManager man;
		synchronized(pluginMap)
		{
			man = pluginMap.get(pluginName);
		}
		if (man != null)
			return man.intervalTask(pluginName, param);
		return null;
	}

	/**
	 * Perform any event handling on the given IRCEvent.
	 * @param ev IRCEvent to pass along.
	 */
	public List<ChoobTask> eventTasks(IRCEvent ev)
	{
		ChoobPluginManager[] mans = new ChoobPluginManager[0];
		synchronized(pluginManagers)
		{
			mans = (ChoobPluginManager[])pluginManagers.toArray(mans);
		}
		List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		for(int i=0; i<mans.length; i++)
			if (!(mans[i] instanceof ChoobDistributingPluginManager))
				tasks.addAll(mans[i].eventTasks(ev));
		return tasks;
	}

	/**
	 * Run any filters on the given Message.
	 * @param ev
	 */
	public List<ChoobTask> filterTasks(Message ev)
	{
		ChoobPluginManager[] mans = new ChoobPluginManager[0];
		synchronized(pluginManagers)
		{
			mans = (ChoobPluginManager[])pluginManagers.toArray(mans);
		}
		List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		for(int i=0; i<mans.length; i++)
			if (!(mans[i] instanceof ChoobDistributingPluginManager))
				tasks.addAll(mans[i].filterTasks(ev));
		return tasks;
	}

	/**
	 * Attempt to perform an API call on a contained plugin.
	 * @param APIName
	 * @param params
	 */
	public Object doAPI(String pluginName, String APIName, Object... params) throws ChoobException
	{
		ChoobPluginManager man;
		synchronized(pluginMap)
		{
			man = pluginMap.get(pluginName);
		}
		if (man != null)
			return man.doAPI(pluginName, APIName, params);
		return null;
	}
}

