package uk.co.uwcs.choob;

import uk.co.uwcs.choob.support.ChoobPermission;
import java.security.Permission;
import java.security.AccessController;
import java.util.Stack;

/**
 * Simple base class for tasks that can be queued into Choob.
 * 
 * @author bucko
 */

public class ChoobThread extends Thread {
	private static final Permission perm = new ChoobPermission("root");

	private String pluginName;

	private Stack<String> pluginStack;

	public ChoobThread(Runnable r, String name) {
		super(r, name);
		pluginName = null;
		pluginStack = new Stack<String>();
	}

	public static ChoobThread runningThread() {
		Thread executing = Thread.currentThread();
		if (!(executing instanceof ChoobThread))
			throw new RuntimeException(
					"ChoobThread static calls accessed from outside a ChoobThread ("
							+ executing.getName() + ")!");
		return (ChoobThread) executing;
	}

	public static void pushPluginStatic(String pluginName) {
		ChoobThread thread = runningThread();
		if (thread == null)
			return;

		thread.pushPlugin(pluginName);
	}

	public static void popPluginStatic() {
		ChoobThread thread = runningThread();
		if (thread == null)
			return;

		thread.popPlugin();
	}

	public static void clearPluginsStatic() {
		ChoobThread thread = runningThread();
		if (thread == null)
			return;

		thread.clearPlugins();
	}

	public static String getPluginStack() {
		String rv = "?";
		try {
			rv = ChoobThread.currentThread().toString();
			int i = 0;
			for (String s = ChoobThread.getPluginName(0); s != null; s = ChoobThread
					.getPluginName(++i)) {
				rv += ", " + s;
			}
		} catch (Exception e) {
		}
		return rv;
	}

	public static synchronized String getPluginName(int i) {
		ChoobThread thread = runningThread();
		if (thread == null)
			return null;

		if (i == 0)
			// Slightly quicker.
			return thread.pluginName;
		else if (i < thread.pluginStack.size())
			return thread.pluginStack.get(thread.pluginStack.size() - (i + 1));
		else
			return null;
	}

	public synchronized void pushPlugin(String pluginName) {
		AccessController.checkPermission(perm);
		this.pluginName = pluginName;
		pluginStack.push(pluginName);
	}

	public synchronized void popPlugin() {
		AccessController.checkPermission(perm);
		this.pluginName = pluginStack.pop();
	}

	public synchronized void clearPlugins() {
		AccessController.checkPermission(perm);
		pluginStack.clear();
	}
}
