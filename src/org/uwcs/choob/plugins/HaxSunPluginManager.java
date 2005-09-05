/**
 * Haxy plugin loader that compiles with the Sun javac API and stuff.
 * @author bucko
 */

package org.uwcs.choob.plugins;

import java.io.*;
import java.net.*;
import java.security.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;
import org.uwcs.choob.*;
import org.uwcs.choob.support.events.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.modules.*;

public class HaxSunPluginManager extends ChoobPluginManager
{
	private final Modules mods;
	private final IRCInterface irc;
	private final URL toolsPath;
	private final Method compileMethod;

	private final static String prefix = "pluginData";
	private final ChoobPluginMap allPlugins;

	public HaxSunPluginManager(Modules mods, IRCInterface irc) throws ChoobException
	{
		this.toolsPath = getToolsPath();
		this.mods = mods;
		this.irc = irc;
		this.allPlugins = new ChoobPluginMap();
		try
		{
			URLClassLoader toolsCL = new URLClassLoader(new URL[] { toolsPath });
			Class javac = toolsCL.loadClass("com.sun.tools.javac.Main");
			this.compileMethod = javac.getMethod("compile", String[].class, PrintWriter.class);
		}
		catch (ClassNotFoundException e)
		{
			throw new ChoobException("Compiler class not found: " + e);
		}
		catch (NoSuchMethodException e)
		{
			throw new ChoobException("Compiler method not found: " + e);
		}
	}

	private URL getToolsPath()
	{
		String libPath = System.getProperty("sun.boot.library.path");
		char fSep = File.separatorChar;
		String toolsString = libPath + fSep + ".." + fSep + ".." + fSep + ".." + fSep + "lib" + fSep + "tools.jar";
		File toolsFile = new File(toolsString);
		if (!toolsFile.exists())
		{
			toolsString = libPath + fSep + ".." + fSep + ".." + fSep + "lib" + fSep + "tools.jar";
			toolsFile = new File(toolsString);
			if (!toolsFile.exists())
			{
				System.err.println("File does not exist: " + toolsFile);
				throw new RuntimeException("Choob must currently be run by a working JDK (not just JRE).");
			}
		}
		try
		{
			return toolsFile.toURI().toURL();
		}
		catch (MalformedURLException e)
		{
			throw new RuntimeException("Internal error: Cannot find URL for tools.jar: " + e);
		}
	}

	private String compile(final String fileName, final String classPath) throws ChoobException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final PrintWriter output = new PrintWriter(baos);
		int ret;
		try
		{
			ret = (Integer)AccessController.doPrivileged(new PrivilegedExceptionAction() {
				public Object run() throws ChoobException {
					try
					{
						return (Integer)compileMethod.invoke(null, new String[] { "-d", classPath, fileName }, output);
					}
					catch (IllegalAccessException e)
					{
						throw new ChoobException("Could not invoke compiler method: " + e);
					}
					catch (InvocationTargetException e)
					{
						throw new ChoobException("Could not invoke compiler method: " + e);
					}
				}
			});
		}
		catch (PrivilegedActionException e)
		{
			throw (ChoobException)(e.getCause());
		}
		System.out.println("Compiler output: " + baos.toString());
		if (ret == 0)
			return baos.toString(); // success
		else
			throw new ChoobException("Compile failed: " + baos.toString());
	}

	protected Object createPlugin(String pluginName, URL source) throws ChoobException
	{
		String classPath = prefix + File.separator + pluginName + File.separator;
		File javaFile = null;
		if (source != null)
		{
			File javaDir = new File(classPath);
			String javaFileName = classPath + pluginName + ".java";
			javaFile = new File(javaFileName);
			URLConnection sourceConn;
			URLConnection localConn;
			try
			{
				sourceConn = source.openConnection();
				localConn = javaFile.toURI().toURL().openConnection();
			}
			catch (IOException e)
			{
				throw new ChoobException("Problem opening connection: " + e);
			}
			if (sourceConn.getLastModified() > localConn.getLastModified() || sourceConn.getLastModified() == 0)
			{
				// needs updating
				OutputStream out = null;
				InputStream in = null;
				boolean success = false;
				try
				{
					javaDir.mkdirs();
					out = new FileOutputStream(javaFile);
					in = sourceConn.getInputStream();
					int amount;
					byte[] buf = new byte[65536];
					while ((amount = in.available()) > 0)
					{
						if (amount > 65536)
							amount = 65536;
						amount = in.read(buf, 0, amount);
						out.write(buf, 0, amount);
					}
					compile(javaFileName, classPath);
					javaFile.setLastModified( sourceConn.getLastModified() );
					success = true;
				}
				catch (IOException e)
				{
					throw new ChoobException("Failed to set up for compiler: " + e);
				}
				finally
				{
					if (!success)
					{
						try { if (in != null) in.close(); } catch (IOException e) {}
						try { if (out != null) out.close(); } catch (IOException e) {}
						javaFile.delete();
					}
				}
			}
		}
		ClassLoader loader = new ChoobPluginClassLoader(pluginName, classPath, getProtectionDomain(pluginName));
		try
		{
			return instantiatePlugin(loader.loadClass(pluginName), pluginName);
		}
		catch (ClassNotFoundException e)
		{
			if (javaFile != null)
				javaFile.delete();
			throw new ChoobException("Could not find plugin class for " + pluginName + ": " + e);
		}
	}

	protected Object instantiatePlugin(Class newClass, String pluginName) throws ChoobException
	{
		Object pluginObj;
		try {
			// Locate a suitable constructor.
			// TODO maybe we should have one with IRCInterface etc. parameters?
			Constructor cons = newClass.getConstructor();
			pluginObj = cons.newInstance();
		}
		catch (NoSuchMethodException e)
		{
			throw new ChoobException("Plugin " + newClass.getName() + " had no constructor: " + e);
		}
		catch (IllegalAccessException e)
		{
			throw new ChoobException("Plugin " + newClass.getName() + " had no constructor: " + e);
		}
		catch (InvocationTargetException e)
		{
			throw new ChoobException("Plugin " + newClass.getName() + "'s constructor threw an exception: " + e.getCause(), e.getCause());
		}
		catch (InstantiationException e)
		{
			throw new ChoobException("Plugin " + newClass.getName() + "'s constructor threw an exception: " + e.getCause(), e.getCause());
		}

		try
		{
			// XXX Isn't this a bit pointless? newInstance() calls the
			// (redefinable) constructor anyway...
			Method meth = newClass.getMethod("create");
			meth.invoke(pluginObj);

		}
		catch (NoSuchMethodException e)
		{
			// This is nonfatal and in fact to be expected!
		}
		catch (IllegalAccessException e)
		{
			// So is this.
		}
		catch (InvocationTargetException e)
		{
			// This isn't.
			throw new ChoobException("Plugin " + newClass.getName() + "'s create() threw an exception: " + e.getCause(), e.getCause());
		}

		synchronized(allPlugins)
		{
			allPlugins.resetPlugin(pluginName, pluginObj);
		}

		return pluginObj;
	}

	protected void destroyPlugin(String pluginName) throws ChoobException
	{
		// Cleanup is actually pretty easy.
		synchronized(allPlugins)
		{
			allPlugins.resetPlugin(pluginName, null);
		}
	}

	/**
	 * Go through the horror of method resolution.
	 *
	 * @param plugin The plugin to use.
	 * @param methodName The name of the method to resolve.
	 * @param args The arguments to resolve the method onto.
	 */
	private Method javaHorrorMethodResolve(List<Method> methods, Object[] args)
	{
		// Do any of them have the right signature?
		List<Method> filtered = new LinkedList<Method>();
		Iterator<Method> it = methods.iterator();
		while(it.hasNext())
		{
			Method method = it.next();
			Class[] types = method.getParameterTypes();
			int paramlength = types.length;
			if (paramlength != args.length)
				continue;

			boolean okness = true;
			for(int j=0; j<paramlength; j++)
			{
				if (!types[j].isInstance(args[j]))
				{
					okness = false;
					break;
				}
			}
			filtered.add(method);
		}

		// Right, have a bunch of applicable methods.
		// TODO: Technically should pick most specific.

		if (filtered.size() == 0)
			return null;

		return filtered.get(0);
	}

	private ChoobTask callCommand(final Method meth, Object param)
	{
		String pluginName = meth.getDeclaringClass().getName();
		final Object plugin = allPlugins.getPluginObj(pluginName);
		Object[] params;
		if (meth.getParameterTypes().length == 1)
			params = new Object[] { param };
		else
			params = new Object[] { param, mods, irc };

		final Object[] params2 = params;
		return new ChoobTask(pluginName) {
			public void run() {
				try
				{
					meth.invoke(plugin, params2);
				}
				catch (InvocationTargetException e)
				{
					System.err.println("Exception invoking method " + meth);
					e.printStackTrace();
				}
				catch (IllegalAccessException e)
				{
					System.err.println("Could not access method " + meth);
					e.printStackTrace();
				}
			}
		};
	}

	public ChoobTask commandTask(String pluginName, String command, Message ev)
	{
		Method meth = allPlugins.getCommand(pluginName + "." + command);
		if(meth != null)
			return callCommand(meth, ev);
		return null;
	}

	public List<ChoobTask> eventTasks(IRCEvent ev)
	{
		List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		List<Method> meths = allPlugins.getEvent(ev.getMethodName());
		if(meths != null)
			for(Method meth: meths)
			{
				tasks.add(callCommand(meth, ev));
			}
		return tasks;
	}

	public List<ChoobTask> filterTasks(Message ev)
	{
		List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		List<Method> meths = allPlugins.getFilter(ev.getMessage());
		if(meths != null)
			for(Method meth: meths)
			{
				tasks.add(callCommand(meth, ev));
			}
		return tasks;
	}

	public ChoobTask intervalTask(String pluginName, Object param)
	{
		Method meth = allPlugins.getInterval(pluginName);
		if(meth != null)
			return callCommand(meth, param);
		return null;
	}

	public Object doAPI(String pluginName, String APIName, Object... params) throws ChoobException
	{
		String sig = getAPISignature(pluginName + "." + APIName, params);
		Method meth = allPlugins.getAPI(sig);
		if (meth == null)
		{
			// OK, not cached. But maybe it's still there...
			List<Method> meths = allPlugins.getAllAPI(APIName);
			meth = javaHorrorMethodResolve(meths, params);
			if (meth != null)
				allPlugins.setAPI(sig, meth);
			else
				throw new ChoobException("Couldn't find a method matching " + sig);
		}
		Object plugin = allPlugins.getPluginObj(meth);
		try
		{
			return meth.invoke(plugin, params);
		}
		catch (InvocationTargetException e)
		{
			throw new ChoobException("Exception invoking method " + meth + ": " + e.getCause(), e.getCause());
		}
		catch (IllegalAccessException e)
		{
			throw new ChoobException("Could not access method " + meth + ": " + e);
		}
	}

	static boolean checkCommandSignature(Method meth)
	{
		Class[] params = meth.getParameterTypes();
		if (params.length == 1)
		{
			System.out.println("1 param");
			if (!Message.class.isAssignableFrom(params[0]))
				return false;
		}
		else if (params.length == 3)
		{
			System.out.println("3 params: " + params[0]);
			if (!Message.class.isAssignableFrom(params[0]))
				return false;
			System.out.println("1 pass: " + params[1]);
			if (!params[1].isAssignableFrom(Modules.class))
				return false;
			System.out.println("2 pass: " + params[2]);
			if (!params[2].isAssignableFrom(IRCInterface.class))
				return false;
			System.out.println("3 pass");
		}
		else
			return false;

		return true;
	}

	static boolean checkEventSignature(Method meth)
	{
		Class[] params = meth.getParameterTypes();
		if (params.length == 1)
		{
			// XXX could be better
			if (!IRCEvent.class.isAssignableFrom(params[0]))
				return false;
		}
		else if (params.length == 3)
		{
			if (!IRCEvent.class.isAssignableFrom(params[0]))
				return false;
			if (!params[1].isAssignableFrom(Modules.class))
				return false;
			if (!params[2].isAssignableFrom(IRCInterface.class))
				return false;
		}
		else
			return false;

		return true;
	}

	static boolean checkIntervalSignature(Method meth)
	{
		Class[] params = meth.getParameterTypes();
		if (params.length == 1)
		{
			// OK
		}
		else if (params.length == 3)
		{
			if (!params[1].isAssignableFrom(Modules.class))
				return false;
			if (!params[2].isAssignableFrom(IRCInterface.class))
				return false;
		}
		else
			return false;

		return true;
	}

	static String getAPISignature(String APIName, Object... args)
	{
		StringBuffer buf = new StringBuffer(APIName + "(");
		for(int i=0; i<args.length; i++)
		{
			buf.append(args[i].getClass().getName());
			if (i < args.length - 1)
				buf.append(",");
		}
		buf.append(")");

		return buf.toString();
	}
}

/**
 * Caches all sorts of plugin info
 */
final class ChoobPluginMap
{
	private final Map<String,Object> plugins;
	private final Map<String,List<String>> pluginCommands;
	private final Map<String,List<String>> pluginApiCallSigs;
	private final Map<String,List<String>> pluginApiCalls;
	private final Map<String,List<Pattern>> pluginFilters;
	private final Map<String,List<Method>> pluginEvents;
	private final Map<String,Method> pluginInterval;
	private final Map<String,Method> commands;
	private final Map<String,Method> apiCallSigs;
	private final Map<String,List<Method>> apiCalls;
	private final Map<Pattern,List<Method>> filters;
	private final Map<String,List<Method>> events;
	ChoobPluginMap()
	{
		plugins = new HashMap<String,Object>();
		pluginCommands = new HashMap<String,List<String>>();
		pluginApiCalls = new HashMap<String,List<String>>();
		pluginApiCallSigs = new HashMap<String,List<String>>();
		pluginFilters = new HashMap<String,List<Pattern>>();
		pluginEvents = new HashMap<String,List<Method>>();
		pluginInterval = new HashMap<String,Method>();
		commands = new HashMap<String,Method>();
		apiCallSigs = new HashMap<String,Method>();
		apiCalls = new HashMap<String,List<Method>>();
		filters = new HashMap<Pattern,List<Method>>();
		events = new HashMap<String,List<Method>>();
	}

	synchronized void resetPlugin(String pluginName, Object pluginObj)
	{
		String lname = pluginName.toLowerCase();
		if (plugins.get(lname) != null)
		{
			// Must clear out old values
			Iterator<String> it;
			it = pluginCommands.get(lname).iterator();
			while (it.hasNext())
				commands.remove(it.next());
			it = pluginApiCalls.get(lname).iterator();
			while (it.hasNext())
				apiCalls.remove(it.next());
			it = pluginApiCallSigs.get(lname).iterator();
			while (it.hasNext())
				apiCallSigs.remove(it.next());
			Iterator<Pattern> it3 = pluginFilters.get(lname).iterator();
			while (it3.hasNext())
			{
				Iterator<Method> it2 = filters.get(it3.next()).iterator();
				while(it2.hasNext())
				{
					if (it2.next().getDeclaringClass().getName().compareToIgnoreCase(pluginName) == 0)
						it2.remove();
				}
			}
			Iterator<Method> it2 = pluginEvents.get(lname).iterator();
			while (it2.hasNext())
			{
				Method m = it2.next();
				events.get(m.getName()).remove(m);
			}
		}

		if (pluginObj == null)
		{
			plugins.remove(lname);
			pluginCommands.remove(lname);
			pluginApiCalls.remove(lname);
			pluginApiCallSigs.remove(lname);
			pluginEvents.remove(lname);
			pluginInterval.remove(lname);
			return;
		}

		plugins.put(lname, pluginObj);
		// OK, now load in new values...
		List<String> coms = new LinkedList<String>();
		pluginCommands.put(lname, coms);
		List<String> apis = new LinkedList<String>();
		pluginApiCalls.put(lname, apis);
		List<String> apiss = new LinkedList<String>();
		pluginApiCallSigs.put(lname, apiss);
		List<Pattern> fils = new LinkedList<Pattern>();
		pluginFilters.put(lname, fils);
		List<Method> evs = new LinkedList<Method>();
		pluginEvents.put(lname, evs);

		Class pluginClass = pluginObj.getClass();
		Method[] meths = pluginClass.getMethods();
		System.out.println("Loading methods...");
		for(Method meth: meths)
		{
			System.out.println("Method: " + meth);
			String name = meth.getName();
			if (name.length() > 7 && name.substring(0, 7).equals("command"))
			{
				String commandName = lname + "." + name.substring(7).toLowerCase();
				System.out.println("Is command: " + commandName);
				// Command
				if (HaxSunPluginManager.checkCommandSignature(meth))
				{
					System.out.println("Signature matches!");
					coms.add(commandName);
					commands.put(commandName, meth);
				}
			}
			else if (name.length() > 3 && name.substring(0, 3).equals("api"))
			{
				String apiName = lname + "." + name.substring(3).toLowerCase();
				if (HaxSunPluginManager.checkCommandSignature(meth))
				{
					apis.add(apiName);
					if (apiCalls.get(apiName) == null)
						apiCalls.put(apiName, new LinkedList<Method>());
					apiCalls.get(apiName).add(meth);
				}
			}
			else if (name.length() > 6 && name.substring(0, 6).equals("filter"))
			{
				String filter;
				try
				{
					filter = (String)pluginClass.getField(name + "Regex").get(pluginObj);
				}
				catch (NoSuchFieldException e)
				{
					System.err.println("Plugin " + pluginName + " had filter " + name + " with no regex.");
					continue;
				}
				catch (ClassCastException e)
				{
					System.err.println("Plugin " + pluginName + " had filter " + name + " with no non-String regex.");
					continue;
				}
				catch (IllegalAccessException e)
				{
					System.err.println("Plugin " + pluginName + " had non-public filter " + name + ".");
					continue;
				}
				Pattern pattern;
				try
				{
					pattern = Pattern.compile(filter);
				}
				catch (PatternSyntaxException e)
				{
					System.err.println("Plugin " + pluginName + " had invalid filter " + filter + ": " + e);
					continue;
				}
				if (HaxSunPluginManager.checkCommandSignature(meth))
				{
					fils.add(pattern);
					if (filters.get(pattern) == null)
						filters.put(pattern, new LinkedList<Method>());
					filters.get(pattern).add(meth);
				}
			}
			else if (name.length() > 2 && name.substring(0, 2).equals("on"))
			{
				if (HaxSunPluginManager.checkEventSignature(meth))
				{
					evs.add(meth);
					if (events.get(name) == null)
						events.put(name, new LinkedList<Method>());
					events.get(name).add(meth);
				}
			}
			else if (name.equals("interval"))
			{
				if (HaxSunPluginManager.checkIntervalSignature(meth))
				{
					pluginInterval.put(lname, meth);
				}
			} // Ignore anything else
		}
	}

	synchronized Object getPluginObj(String pluginName)
	{
		return plugins.get(pluginName.toLowerCase());
	}

	synchronized Object getPluginObj(Method meth)
	{
		return getPluginObj(meth.getDeclaringClass().getName().toLowerCase());
	}

	synchronized Method getCommand(String commandName)
	{
		return commands.get(commandName.toLowerCase());
	}

	synchronized Method getAPI(String apiName)
	{
		return apiCallSigs.get(apiName.toLowerCase());
	}

	synchronized void setAPI(String apiName, Method meth)
	{
		pluginApiCallSigs.get(meth.getDeclaringClass().getName().toLowerCase()).add(apiName.toLowerCase());
		apiCallSigs.put(apiName.toLowerCase(), meth);
	}

	synchronized List<Method> getAllAPI(String apiName)
	{
		return apiCalls.get(apiName.toLowerCase());
	}

	synchronized List<Method> getFilter(String text)
	{
		List<Method> ret = new LinkedList<Method>();
		Iterator<Pattern> pats = filters.keySet().iterator();
		while(pats.hasNext())
		{
			Pattern pat = pats.next();
			if (pat.matcher(text).matches())
				ret.addAll(filters.get(pat));
		}
		return ret;
	}

	synchronized List<Method> getEvent(String eventName)
	{
		List<Method> handlers = events.get(eventName);
		if (handlers != null)
			return (List<Method>)((LinkedList<Method>)handlers).clone();
		return null;
	}

	synchronized Method getInterval(String pluginName)
	{
		return pluginInterval.get(pluginName.toLowerCase());
	}
}
