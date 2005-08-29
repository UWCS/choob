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

		bsh.Capabilities.setAccessibility(false);

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
	 * Go through the horror of method reolution.
	 *
	 * @param plugin The plugin to use.
	 * @param methodName The name of the method to resolve.
	 * @param args The arguments to resolve the method onto.
	 */
	static private Object javaHorrorMethodCall(Object plugin, String methodName, Object[] args)
		throws NoSuchMethodException, InvocationTargetException, IllegalAccessException
	{
		Method[] methods = plugin.getClass().getDeclaredMethods();
		ArrayList filtered = new ArrayList();
		for(int i=0; i<methods.length; i++)
		{
			if (methods[i].getName().equals(methodName))
				filtered.add(methods[i]);
		}

		if (filtered.size() == 0)
			throw new NoSuchMethodException("No method named " + methodName + " in plugin " + plugin.getClass().getName());

		// Do any of them have the right signature?
		ArrayList secondPass = new ArrayList();
		for(int i=0; i<filtered.size(); i++)
		{
			Method method = (Method)filtered.get(i);
			Class[] types = method.getParameterTypes();
			int paramlength = types.length;
			if (paramlength != args.length)
				continue;

			boolean okness = true;
			for(int j=0; j<paramlength; j++)
			{
				if (!types[i].isInstance(args[i]))
				{
					okness = false;
					break;
				}
			}
			secondPass.add(method);
		}

		// Right, have a bunch of applicable methods.
		// TODO: Technically should pick most specific.

		if (secondPass.size() == 0)
			throw new NoSuchMethodException("No method named " + methodName + " matches signature in plugin " + plugin.getClass().getName());

		Method method = (Method)secondPass.get(0);

		// XXX
		// Oh, the increased horror of it all!
		// Not only does bsh set this true by default, it also sets it true
		// on random occasions!
		// I can't seem to figure out exactly what's being set to accessible
		// in the security manager so for now, I'm forbidding BeanShell to
		// try it on. :)
		//   -- bucko
		try
		{
			bsh.Capabilities.setAccessibility(false);
		}
		catch (Exception e) {}

		// OK, have all methods of the correct name...
		return method.invoke(plugin, args);
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
					javaHorrorMethodCall(plugin, func, new Object[] { ev, mods, irc });
				}
				catch (NoSuchMethodException e)
				{
					System.out.println("Oh noes, method " + func + " not found in " + plugin.getClass().getName() + "!");
					//omgwtfhaxCallCache.put(identifier, null);
					//System.out.println(identifier + " will be ignored from now on.");
				}
				catch (InvocationTargetException e)
				{
					// The horror!
					System.out.println("Exception in calling plugin function: " + e);
					e.printStackTrace();
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
			javaHorrorMethodCall(plugin, func, new Object[] { mods });
		}
		catch( NoSuchMethodException e )
		{
			// Here we just shrug our shoulders and go 'meh' in a Skumby-esque fashion
			// If people don't want to provide a create/destroy method, we can't force
			// them.
		}
		catch (InvocationTargetException e)
		{
			// The horror!
			System.out.println("Exception in calling plugin function: " + e);
			e.printStackTrace();
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
		try
		{
			return javaHorrorMethodCall(plugin, "api"+APIName, params);
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
