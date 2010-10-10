package uk.co.uwcs.choob;

import java.security.AccessController;
import java.security.Permission;
import java.util.Stack;

import uk.co.uwcs.choob.support.ChoobPermission;

/**
 * Simple base class for tasks that can be queued into Choob.
 * @author bucko
 */

public class ChoobThread extends Thread
{
	private static final Permission perm = new ChoobPermission("root");

	private String pluginName;
	private final Stack<String> pluginStack;

	public ChoobThread(final Runnable r, final String name)
	{
		super(r, name);
		pluginName = null;
		pluginStack = new Stack<String>();
	}


	public static ChoobThread runningThread()
	{
		final Thread executing = Thread.currentThread();
		if (!(executing instanceof ChoobThread))
			return null;
		return (ChoobThread)executing;
	}

	public static void pushPluginStatic(final String pluginName)
	{
		final ChoobThread thread = runningThread();
		if (thread == null) return;

		thread.pushPlugin(pluginName);
	}

	public static void popPluginStatic()
	{
		final ChoobThread thread = runningThread();
		if (thread == null) return;

		thread.popPlugin();
	}

	public static void clearPluginsStatic()
	{
		final ChoobThread thread = runningThread();
		if (thread == null) return;

		thread.clearPlugins();
	}

	public static String getPluginStack()
	{
		String rv = "?";
		try {
			rv = Thread.currentThread().toString();
			int i = 0;
			for (String s = ChoobThread.getPluginName(0); s != null; s = ChoobThread.getPluginName(++i))
			{
				rv += ", " + s;
			}
		} catch (final Exception e) {
		}
		return rv;
	}

	public static synchronized String getPluginName(final int i)
	{
		final ChoobThread thread = runningThread();
		if (thread == null) return null;

		if (i == 0)
			// Slightly quicker.
			return thread.pluginName;
		else if (i < thread.pluginStack.size())
			return thread.pluginStack.get(thread.pluginStack.size() - (i + 1));
		else
			return null;
	}

	public synchronized void pushPlugin(final String mpluginName)
	{
		AccessController.checkPermission(perm);
		this.pluginName = mpluginName;
		pluginStack.push(mpluginName);
	}

	public synchronized void popPlugin()
	{
		AccessController.checkPermission(perm);
		this.pluginName = pluginStack.pop();
	}

	public synchronized void clearPlugins()
	{
		AccessController.checkPermission(perm);
		pluginStack.clear();
	}
}
