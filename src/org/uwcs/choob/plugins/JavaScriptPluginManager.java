/*
 * Created on October 8, 2005, 9:33 PM
 */
package org.uwcs.choob.plugins;

import java.io.*;
import java.net.*;
import java.util.*;
import org.uwcs.choob.*;
import org.uwcs.choob.support.events.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.modules.*;
import org.mozilla.javascript.*;

/*
 * Deals with all the magic for JavaScript plugins. Woo.
 * @author silver
 */
public class JavaScriptPluginManager extends ChoobPluginManager {
	private JavaScriptPluginMap pluginMap;
	private static final int READER_CHUNK = 1024;
	// For passing to plugin constructors.
	private final Modules mods;
	private final IRCInterface irc;
	
	public JavaScriptPluginManager(Modules mods, IRCInterface irc) {
		System.out.println("JavaScriptPluginManager.<ctor>");
		this.mods = mods;
		this.irc = irc;
		this.pluginMap = new JavaScriptPluginMap();
	}
	
	public static void dump(String text) {
		System.out.print("JS dump: " + text);
	}
	
	public static void dumpln(String text) {
		System.out.println("JS dump: " + text);
	}
	
	protected Object createPlugin(String pluginName, URL fromLocation) throws ChoobException {
		System.out.println("JavaScriptPluginManager.createPlugin");
		
		String code = "";
		URLConnection con;
		try {
			con = fromLocation.openConnection();
		} catch(IOException e) {
			throw new ChoobException("Unable to open a connection to the source for " + pluginName);
		}
		try {
			con.connect();
			InputStream stream = con.getInputStream();
			InputStreamReader streamReader = new InputStreamReader(stream);
			int read = 0;
			char[] chars = new char[READER_CHUNK];
			while (true) {
				int r = streamReader.read(chars, 0, READER_CHUNK);
				if (r == -1)
					break;
				for (int i = 0; i < r; i++)
					code += chars[i];
				read += r;
			}
			System.out.println("JavaScriptPluginManager.createPlugin: loaded " + read + " characters.");
		} catch(IOException e) {
			throw new ChoobException("Unable to fetch the source for " + pluginName);
		}
		
		JavaScriptPlugin plug = new JavaScriptPlugin(this, pluginName, code, mods, irc);
		pluginMap.loadPluginMap(pluginName, plug);
		return plug;
	}
	
	protected void destroyPlugin(String pluginName) throws ChoobException {
		System.out.println("JavaScriptPluginManager.destroyPlugin");
	}
	
	public ChoobTask commandTask(String pluginName, String command, Message ev) {
		System.out.println("JavaScriptPluginManager.commandTask(" + pluginName + ", " + command + ")");
		JavaScriptPluginMethod method = pluginMap.getCommand(pluginName + "." + command);
		if (method != null) {
			return callCommand(method, ev);
		}
		return null;
	}
	
	public ChoobTask intervalTask(String pluginName, Object param) {
		System.out.println("JavaScriptPluginManager.intervalTask");
		return null;
	}
	
	public List<ChoobTask> eventTasks(IRCEvent ev) {
		System.out.println("JavaScriptPluginManager.eventTasks");
		List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		return tasks;
	}
	
	public List<ChoobTask> filterTasks(Message ev) {
		System.out.println("JavaScriptPluginManager.filterTasks");
		List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		return tasks;
	}
	
	public Object doAPI(String pluginName, String APIName, Object... params) throws ChoobException {
		System.out.println("JavaScriptPluginManager.doAPI");
		return null;
	}
	
	public Object doGeneric(String pluginName, String prefix, String genericName, Object... params) throws ChoobException {
		System.out.println("JavaScriptPluginManager.doGeneric");
		return null;
	}
	
	private ChoobTask callCommand(final JavaScriptPluginMethod method, final Object param)
	{
		final JavaScriptPlugin plugin = method.getPlugin();
		String pluginName = plugin.getName();
		
		// Parameters for call.
		Object[] params = { param, mods, irc };
		
		final Object[] params2 = params;
		return new ChoobTask(pluginName) {
			public void run() {
				Context cx = Context.enter();
				try {
					Scriptable scope = plugin.getScope();
					Scriptable inst = plugin.getInstance();
					Function function = method.getFunction();
					
					function.call(cx, scope, inst, params2);
				} finally {
					cx.exit();
				}
			}
		};
	}
}

final class JavaScriptPluginMap {
	// List of plugins.
	private final Map<String,Object> plugins;
	
	// List of commands for each plugin.
	private final Map<String,List<String>> pluginCommands;
	
	// List of function for each command.
	private final Map<String,JavaScriptPluginMethod> commands;
	
	public JavaScriptPluginMap() {
		plugins = new HashMap<String,Object>();
		pluginCommands = new HashMap<String,List<String>>();
		commands = new HashMap<String,JavaScriptPluginMethod>();
	}
	
	synchronized void loadPluginMap(String pluginName, JavaScriptPlugin pluginObj) {
		System.out.println("JavaScriptPluginMap.loadPluginMap(" + pluginName + ")");
		String lname = pluginName.toLowerCase();
		
		// Set up maps...
		plugins.put(lname, pluginObj);
		List<String> commandNames = new LinkedList<String>();
		pluginCommands.put(lname, commandNames);
		
		Scriptable inst = pluginObj.getInstance();
		while (inst != null) {
			Object[] propList = inst.getIds();
			String propString;
			for (Object prop: propList) {
				if (prop instanceof String) {
					propString = (String)prop;
					if (propString.startsWith("command")) {
						Object propVal = inst.get(propString, inst);
						if (propVal instanceof Function) {
							JavaScriptPluginMethod method = new JavaScriptPluginMethod(pluginObj, propString, (Function)propVal);
							
							String commandName = lname + "." + propString.substring(7).toLowerCase();
							commandNames.add(commandName);
							commands.put(commandName, method);
							System.out.println("  Command: " + commandName);
						}
					}
				} else {
					System.out.println("  Found property of type: " + prop.getClass().getName());
				}
			}
			inst = inst.getPrototype();
		}
	}
	
	synchronized void unloadPluginMap(String pluginName, JavaScriptPlugin pluginObj) {
		System.out.println("JavaScriptPluginMap.unloadPluginMap(" + pluginName + ")");
		String lname = pluginName.toLowerCase();
		
		plugins.remove(lname);
		for (String command: getCommands(pluginName)) {
			commands.remove(command.getName());
		}
		pluginCommands.remove(lname);
	}
	
	synchronized JavaScriptPluginMethod getCommand(String commandName) {
		return commands.get(commandName.toLowerCase());
	}
	
	synchronized List<String> getCommands(String pluginName) {
		return pluginCommands.get(pluginName.toLowerCase());
	}
}

final class JavaScriptPluginMethod {
	private JavaScriptPlugin plugin;
	private String name;
	private Function function;
	
	public JavaScriptPluginMethod(JavaScriptPlugin plugin, String name, Function function) {
		this.plugin = plugin;
		this.name = name;
		this.function = function;
	}
	
	public JavaScriptPlugin getPlugin() {
		return plugin;
	}
	
	public String getName() {
		return name;
	}
	
	public Function getFunction() {
		return function;
	}
}

final class JavaScriptPlugin {
	private String pluginName;
	private Scriptable scope;
	private Scriptable inst;
	
	public JavaScriptPlugin(JavaScriptPluginManager plugMan, String pluginName, String code, Modules mods, IRCInterface irc) throws ChoobException {
		this.pluginName = pluginName;
		
		Context cx = Context.enter();
		try {
			scope = cx.initStandardObjects();
			// Set up dump() and dumpln() functions.
			try {
				scope.put("dump", scope, new FunctionObject("dump", JavaScriptPluginManager.class.getMethod("dump", String.class), scope));
				scope.put("dumpln", scope, new FunctionObject("dumpln", JavaScriptPluginManager.class.getMethod("dumpln", String.class), scope));
			} catch(NoSuchMethodException e) {
				System.err.println("Method not found: " + e);
				// Ignore for now.
			}
			
			// Pull in script.
			Object result = cx.evaluateString(scope, code, pluginName, 1, null);
			Object ctor = scope.get(pluginName, scope);
			if (ctor == Scriptable.NOT_FOUND) {
				throw new ChoobException("Constructor for JavaScript plugin " + pluginName + " not found.");
			}
			// Construct instance.
			Object args[] = { mods, irc };
			inst = cx.newObject(scope, pluginName, args);
			System.out.println("JavaScriptPlugin ctor result: " + cx.toString(inst));
		} finally {
			cx.exit();
		}
	}
	
	public String getName() {
		return pluginName;
	}
	
	public Scriptable getScope() {
		return scope;
	}
	
	public Scriptable getInstance() {
		return inst;
	}
}
