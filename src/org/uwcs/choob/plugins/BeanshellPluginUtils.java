/*
 * PluginLoader.java
 *
 * Created on June 13, 2005, 1:25 PM
 */
package org.uwcs.choob.plugins;

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

/**
 * Set of utilities that are used to load/interogate Beanshell plugins.
 * @author sadiq
 */
public class BeanshellPluginUtils
{
	/**
	 * Creates a plugin from a given URL and plugin name.
	 * @param URL URL to plugin's source.
	 * @param pluginName Class name of plugin.
	 * @throws Exception Thrown if there's a syntactical error in the plugin's source.
	 * @return Returns an instance of the new plugin.
	 */
	public static Object createBeanshellPlugin(String srcContent, String pluginName) throws Exception
	{
		Class coreClass;
		Interpreter i;

		i = new Interpreter();

		try
		{
			System.out.println(i.eval(srcContent));

			String classname = pluginName;

			Class newPlugin = i.getNameSpace().getClass(classname);

			if( newPlugin != null )
			{
				Object newPluginObject = newPlugin.newInstance();

				return newPluginObject;
			}
			else
			{
				throw new Exception("Could not load new plugin.");
			}
		}
		catch( bsh.EvalError e )
		{
			throw new Exception("Beanshell: Could not compile plugin: " + e.getMessage(), e);
		}
		catch( Exception e )
		{
			throw new Exception("Exception: Could not compile plugin: " + e.getMessage(), e);
		}
	}

	/**
	 * Calls a given command*, filter*, interval* method in the plugin.
	 * @param plugin Plugin to call.
	 * @param func Function to call.
	 * @param con Context from IRC.
	 * @param mods Group of modules available.
	 */
	static private void callFunc(Object plugin, String func, IRCEvent ev, Modules mods, IRCInterface irc)
	{
		Class coreClass = plugin.getClass();
		System.out.println(ev.getClass().toString());

		try
		{
			if( coreClass != null )
			{
				try
				{
					// XXX
					Class evType;
					if (ev instanceof Message) {
						evType = Message.class;
					} else {
						evType = ev.getClass();
					}

					System.out.println("The method \"" + func + "\" (" + evType.toString() + ", " + Modules.class.toString() + ", " + IRCInterface.class.toString() + ") in the plugin " + plugin.toString() + "..");
					Method tempMethod = coreClass.getDeclaredMethod(func, new Class[] { evType, Modules.class, IRCInterface.class });

					Object[] objectArray = new Object[3];

					objectArray[0] = ev;
					objectArray[1] = mods;
					objectArray[2] = irc;

					tempMethod.invoke(plugin,objectArray);
					System.out.println("Got called!");

				}
				catch (NoSuchMethodException e)
				{
					System.out.println("Oh noes, method not found!");
					//omgwtfhaxCallCache.put(identifier, null);
					//System.out.println(identifier + " will be ignored from now on.");
				}
			}
		}
		catch( Exception e )
		{
			// Cause can apparently be null in some situations.
			if( e.getCause() != null && e.getCause().getClass() == SecurityException.class )
			{
				//irc.sendContextMessage(ev, "Security exception: " + e.getCause());
			}
			System.out.println("Exception in calling plugin function: " + e);
			e.printStackTrace();
			// What exactly do we do here? We _know_ we'return going to get these.
			// Suggest just passing on the exception --bucko
		}
	}

	/**
	 * Calls the create() / destroy() methods in a plugin.
	 * @param plugin Plugin to call.
	 * @param func Function to call.
	 */
	static private void callSpecialFunc(Object plugin, String func, Modules mods)
	{
		Class coreClass = plugin.getClass();
		try
		{
			if( coreClass != null )
			{
				Method tempMethod = coreClass.getDeclaredMethod(func,new Class[]
				{ Modules.class });

				Object[] objectArray = new Object[1];

				objectArray[0] = mods;

				tempMethod.invoke(plugin,objectArray);
			}
		}
		catch( NoSuchMethodException e )
		{
			// Here we just shrug our shoulders and go 'meh' in a Skumby-esque fashion
			// If people don't want to provide a create/destroy method, we can't force
			// them.
		}
		catch( Exception e )
		{
			System.out.println("Exception in calling plugin function: " + e);
			e.printStackTrace();
		}
	}

	/**
	 * Call the destroy() method in a plugin.
	 * @param plugin
	 */
	static public void callPluginDestroy(Object plugin, Modules mods)
	{
		callSpecialFunc(plugin, "destroy", mods);
	}

	/**
	 * Call the create() method in a plugin.
	 * @param plugin
	 */
	static public void callPluginCreate(Object plugin, Modules mods)
	{
		callSpecialFunc(plugin, "create", mods);
	}

	/**
	 * Attempts to call a method in the plugin, triggered by a line from IRC.
	 * @param plugin
	 * @param command Command to call.
	 * @param con Context from IRC.
	 * @param mods Group of modules.
	 */
	static public void doCommand(Object plugin, String command, Message ev, Modules mods, IRCInterface irc)
	{
		System.out.println("Calling method command" + command);
		callFunc(plugin, "command" + command,ev,mods,irc);
	}

	/**
	 * Attempts to call a method in the plugin, triggered by an event from IRC.
	 * @param plugin
	 * @param eventname Event method name to call.
	 * @param con Context from IRC.
	 * @param mods Group of modules.
	 */
	static public void doEvent(Object plugin, String eventname, IRCEvent ev, Modules mods, IRCInterface irc)
	{
		System.out.println("Calling method " + eventname);
		callFunc(plugin, eventname, ev,mods,irc);
	}

	/**
	 *
	 * @param plugin
	 * @param filter
	 * @param con
	 * @param mods
	 * @param irc
	 */
	static public void doFilter(Object plugin, String filter, Message ev, Modules mods, IRCInterface irc)
	{
		callFunc(plugin, "filter" + filter,ev,mods, irc);
	}

	/**
	 *
	 * @param plugin
	 * @param APIName
	 * @param params
	 */

	static public Object doAPI(Object plugin, String APIName, Object... params)
	{
		Class coreClass = plugin.getClass();
		try
		{
			if( coreClass != null )
			{
				// What class is everything?
				Class[] classes = new Class[params.length];
				for(int i=0; i<params.length; i++)
					classes[i] = params[i].getClass();

				Method tempMethod = coreClass.getDeclaredMethod("api"+APIName,classes);

				return tempMethod.invoke(plugin, params);
			}
		}
		catch( Exception e )
		{
			// Copypaste from above, at least for now.
			// Cause can apparently be null in some situations.
			if( e.getCause() != null && e.getCause().getClass() == SecurityException.class )
			{
				// Ooops! Can't do this, since we don't necessarily have a context object!
				//irc.sendContextMessage(con,"Security exception: " + e.getCause());
			}
			System.out.println("Exception in calling plugin function: " + e);
			e.printStackTrace();
			// What exactly do we do here? We _know_ we'return going to get these.
		}
		return null;
	}

	/**
	 *
	 * @param plugin
	 * @param interval
	 * @param con
	 * @param mods
	 */
	static public void doInterval(Object plugin, Object parameter, Modules mods, IRCInterface irc)
	{
		Class coreClass = plugin.getClass();

		try
		{
			if( coreClass != null )
			{
				Method tempMethod = coreClass.getDeclaredMethod("interval",new Class[]
				{ Object.class, Modules.class, IRCInterface.class });

				Object[] objectArray = new Object[3];

				objectArray[0] = parameter;
				objectArray[1] = mods;
				objectArray[2] = irc;

				tempMethod.invoke(plugin,objectArray);
			}
		}
		catch( Exception e )
		{
			// Cause can apparently be null in some situations.
			if( e.getCause() != null && e.getCause().getClass() == SecurityException.class )
			{
				System.out.println("Security exception: " + e.getCause());
			}
			System.out.println("Exception in calling plugin function: " + e);
			e.printStackTrace();
			e.printStackTrace();
			// What exactly do we do here? We _know_ we'return going to get these.
		}
	}

	/**
	 *
	 * @return
	 */
	static public List getExportedMethods(Object plugin, String prefix)
	{
		Class coreClass = plugin.getClass();

		Method[] methodList = coreClass.getDeclaredMethods();

		Pattern methodPattern = Pattern.compile("^" + prefix + "([A-Z][a-zA-Z]*)");

		ArrayList methods = new ArrayList();

		for( int c = 0; c < methodList.length ; c++ )
		{
			Method tempMethod = methodList[c];
			Matcher methodMatcher = methodPattern.matcher(tempMethod.getName());

			if( methodMatcher.matches() )
			{
				methods.add(methodMatcher.group(1));
			}
		}

		return methods;
	}

	/**
	 *
	 * @return
	 */
	static public List getFilters(Object plugin)
	{
		Class coreClass = plugin.getClass();

		List filterNames = getExportedMethods(plugin, "filter");

		List filters = new ArrayList();

		for( int c = 0; c < filterNames.size() ; c++ )
		{
			String filterName = (String)filterNames.get(c);

			String filterRegex = null;

			try
			{
				Field filterRegexField = coreClass.getDeclaredField("filter" + filterName + "Regex");

				filterRegex = (String)filterRegexField.get( plugin );
			}
			catch( Exception e )
			{
				System.out.println("No filter regex found for filter " + filterName + " in plugin " + coreClass.getName() );
			}

			filters.add( new Filter(filterName,filterRegex,coreClass.getName()));

			System.out.println("Found filter name: " + filterName + " with regex " + filterRegex);
		}

		return filters;
	}

}
