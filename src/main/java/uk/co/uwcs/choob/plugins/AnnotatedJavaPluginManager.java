package uk.co.uwcs.choob.plugins;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import uk.co.uwcs.choob.ChoobCommand;
import uk.co.uwcs.choob.ChoobPluginManager;
import uk.co.uwcs.choob.ChoobPluginManagerState;
import uk.co.uwcs.choob.ChoobTask;
import uk.co.uwcs.choob.ChoobUtil;
import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobCommandWrapper;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.CommandUsageException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.NoSuchCommandException;
import uk.co.uwcs.choob.support.NoSuchPluginException;
import uk.co.uwcs.choob.support.events.Event;
import uk.co.uwcs.choob.support.events.Message;

/**
 * Loads plugins written in java with annotations.
 * @author benji
 */
public class AnnotatedJavaPluginManager extends ChoobPluginManager
{

	private static final String PLUGIN_DIR = HaxSunPluginManager.PLUGIN_DIR;

	private final Modules mods;
	private final IRCInterface irc;
	private final PluginHolder plugins;

	public AnnotatedJavaPluginManager(final Modules mods, final IRCInterface irc,
			final ChoobPluginManagerState state) throws ChoobException
	{
		super(mods, state);
		this.mods = mods;
		this.irc = irc;
		this.plugins = new PluginHolder();
	}

	@Override
	protected Object createPlugin(final String pluginName, final URL fromLocation) throws ChoobException
	{
		//Create the directory to store the plugin in.
		File targetLocation = new File(PLUGIN_DIR + File.separator + pluginName + File.separator);
		targetLocation.mkdirs();
		try
		{
			File pluginFile = downloadTo(fromLocation, new File(targetLocation.getCanonicalPath() + File.separator + pluginName + ".java"));



			JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
			StandardJavaFileManager sjfm = jc.getStandardFileManager(null, null, null);
			Iterable<? extends JavaFileObject> fileObjects = sjfm.getJavaFileObjects(pluginFile);
			jc.getTask(null, sjfm, null, null, null, fileObjects).call();
			sjfm.close();

			ClassLoader loader = new HaxSunPluginClassLoader(pluginName, targetLocation.toString(), getProtectionDomain(pluginName));
			try
			{
				return loadPlugin(loader.loadClass(pluginName/*"plugins." + pluginName + "." + pluginName*/), pluginName);
			} catch (ClassNotFoundException e)
			{
				throw new ChoobException("Could not find plugin class for " + pluginName + ": " + e);
			}

		} catch (IOException e)
		{
			throw new ChoobException("Problem downloading plugin." + e.getMessage(), e);
		}
	}

	private Object loadPlugin(final Class<?> newClass, final String pluginName) throws ChoobException
	{
		Object plugin = null;

		Constructor<?> c[] = newClass.getConstructors();

		for (int i = 0; i < c.length; i++)
			try {
				Class<?>[] t = c[i].getParameterTypes();
				Object[] arg = new Object[t.length];

				for (int j=0; j<t.length; j++)
					if (t[j] == IRCInterface.class)
						arg[j]=irc;
					else
						if (t[j] == Modules.class)
							arg[j]=mods;
						else
							throw new ChoobException("Unknown parameter in constructor.");
				plugin = c[i].newInstance(arg);
				break;
			}
			catch (IllegalAccessException e)
			{
				throw new ChoobException("Plugin " + pluginName + " had no constructor (this error shouldn't occour, something serious is wrong): " + e);
			}
			catch (InvocationTargetException e)
			{
				throw new ChoobException("Plugin " + pluginName + "'s constructor threw an exception: " + e.getCause(), e.getCause());
			}
			catch (InstantiationException e)
			{
				throw new ChoobException("Plugin " + pluginName + "'s constructor threw an exception: " + e.getCause(), e.getCause());
			}


		for (Field field : newClass.getDeclaredFields())
		{
			if (field.isAnnotationPresent(ChoobUtil.class))
			{
				try
				{
					if (field.getType().equals(IRCInterface.class))
					{
						field.setAccessible(true);
						field.set(plugin, irc);
					} else if (field.getType().equals(Modules.class))
					{
						field.setAccessible(true);
						field.set(plugin, mods);
					} else
					{
						throw new ChoobException("Unknown type of resource to inject " + field.getType());
					}
				} catch (IllegalArgumentException ex)
				{
					System.err.println("IllegalArgumentException");
					System.err.println(ex.getMessage());
					ex.printStackTrace();
					throw new ChoobException("Unable to inject " + field.getType() + ".", ex.getCause());
				} catch (IllegalAccessException ex)
				{
					System.err.println("IllegalAccessException");
					System.err.println(ex.getMessage());
					ex.printStackTrace();
					throw new ChoobException("Unable to inject " + field.getType() + ".", ex.getCause());
				}
			}
		}
		plugins.addPlugin(pluginName, plugin);
		return plugin;
	}

	private File downloadTo(final URL url, final File target) throws IOException
	{
		URLConnection site = url.openConnection();
		BufferedInputStream is = new BufferedInputStream(site.getInputStream());
		target.delete();
		FileOutputStream out = new FileOutputStream(target);
		byte[] buffer = new byte[1024];
		while (true)
		{
			int nextByte = is.read(buffer);
			if (nextByte < 0)
			{
				break;
			}
			out.write(buffer, 0, nextByte);
		}
		out.close();
		return target;
	}

	@Override
	protected void destroyPlugin(final String pluginName)
	{
		plugins.removePlugin(pluginName);
	}

	@Override
	public ChoobTask commandTask(final String pluginName, final String command, final Message ev)
	{
		try
		{
			final ChoobCommandWrapper mpe = plugins.getCommand(pluginName.toLowerCase(), command.toLowerCase());
			final Object plugin = plugins.getPlugin(pluginName.toLowerCase());
			return new ChoobTask(pluginName.toLowerCase())
			{
				@Override
				public void run()
				{
					try
					{
						String result = mpe.call(plugin, ev);
						if (result != null && result.length() > 0)
							irc.sendContextMessage(ev, result);
					} catch (CommandUsageException e)
					{
						irc.sendContextReply(ev, "Usage: " + pluginName + "." + command + " " + e.getMessage());
					} catch (ChoobException e)
					{
						mods.plugin.exceptionReply(ev, e.getCause(), pluginName);
					}
				}
			};
		} catch (NoSuchCommandException e)
		{
			return null;
		} catch (NoSuchPluginException e)
		{
			return null;
		}
	}

	@Override
	public ChoobTask intervalTask(String pluginName, Object param)
	{
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public List<ChoobTask> eventTasks(Event ev)
	{
		return new ArrayList<ChoobTask>();//throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public List<ChoobTask> filterTasks(Message ev)
	{
		return Arrays.asList(); //throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Object doAPI(String pluginName, String APIName, Object... params) throws ChoobNoSuchCallException
	{
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Object doGeneric(String pluginName, String prefix, String genericName, Object... params) throws ChoobNoSuchCallException
	{
		throw new UnsupportedOperationException("Not supported yet.");
	}
}


class PluginHolder
{
	private HashMap<String,Object> plugins = new HashMap<String, Object>();
	private HashMap<PluginCommand,ChoobCommandWrapper> commands = new HashMap<PluginCommand,ChoobCommandWrapper>();

	public void addPlugin(final String pluginName, final Object plugin)
	{
		plugins.put(pluginName.toLowerCase(),plugin);

		Class<?> pluginClass = plugin.getClass();
		Method[] meths = pluginClass.getMethods();
		for(Method meth: meths)
		{
			if (meth.getDeclaringClass() != pluginClass)
				continue;

			if (meth.isAnnotationPresent(ChoobCommand.class))
			{
				commands.put(new PluginCommand(pluginName,meth.getAnnotation(ChoobCommand.class).name()), new ChoobCommandWrapper(meth));
			}
		}
	}

	public ChoobCommandWrapper getCommand(final String pluginName, final String commandName) throws NoSuchCommandException
	{
		ChoobCommandWrapper wrapper = commands.get(new PluginCommand(pluginName, commandName));
		Iterator<PluginCommand> iterator = commands.keySet().iterator();
		iterator.next();
		PluginCommand next = iterator.next();
		commands.get(next);
		commands.get(new PluginCommand("annotatedchoobtest","greet"));
		next.equals(new PluginCommand("annotatedchoobtest","greet"));

		if (wrapper != null)
			return wrapper;
		else throw new NoSuchCommandException(commandName);
	}

	public Object getPlugin(final String pluginName) throws NoSuchPluginException
	{
		Object o = plugins.get(pluginName.toLowerCase());
		if (o != null)
			return o;
		else
			throw new NoSuchPluginException(pluginName);
	}

	public void removePlugin(final String pluginName)
	{
		plugins.remove(pluginName);
		for (PluginCommand pcmd : commands.keySet())
		{
			if (pcmd.getPluginName().equals(pluginName))
				commands.remove(pcmd);
		}
	}
}


class PluginCommand
{
	private final String pluginName;
	private final String commandName;
	public PluginCommand(final String pluginName, final String commandName)
	{
		this.pluginName = pluginName;
		this.commandName = commandName;
	}

	public String getPluginName()
	{
		return pluginName;
	}

	public String getCommandName()
	{
		return commandName;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj == null)
		{
			return false;
		}
		if (getClass() != obj.getClass())
		{
			return false;
		}
		final PluginCommand other = (PluginCommand) obj;
		return this.pluginName.equalsIgnoreCase(other.pluginName) && this.commandName.equalsIgnoreCase(other.commandName);
	}

	@Override
	public int hashCode()
	{
		int hash = 5;
		hash = 67 * hash + (this.pluginName != null ? this.pluginName.toLowerCase().hashCode() : 0);
		hash = 67 * hash + (this.commandName != null ? this.commandName.toLowerCase().hashCode() : 0);
		return hash;
	}




}