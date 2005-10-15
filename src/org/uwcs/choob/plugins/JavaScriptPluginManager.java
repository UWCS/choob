/*
 * Created on October 8, 2005, 9:33 PM
 */
package org.uwcs.choob.plugins;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import org.uwcs.choob.*;
import org.uwcs.choob.support.events.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.modules.*;
import org.mozilla.javascript.*;
import org.mozilla.javascript.regexp.*;

/*
 * Deals with all the magic for JavaScript plugins. Woo.
 * @author silver
 */
public class JavaScriptPluginManager extends ChoobPluginManager {
	/*
	 * The plugin map tracks which plugin instances have which commands, and
	 * keeps a command name --> function map in particular.
	 */
	private JavaScriptPluginMap pluginMap;
	// The size of the reading buffer when loading JS files.
	private static final int READER_CHUNK = 1024;
	// For passing to plugin constructors.
	private final Modules mods;
	private final IRCInterface irc;
	
	private final int CALL_UNKNOWN     = 0;
	private final int CALL_WANT_TASK   = 1;
	private final int CALL_WANT_RESULT = 2;
	
	public JavaScriptPluginManager(Modules mods, IRCInterface irc) {
		System.out.println("JavaScriptPluginManager.<ctor>");
		this.mods = mods;
		this.irc = irc;
		this.pluginMap = new JavaScriptPluginMap();
	}
	
	/*
	 * Utility method for JS scripts, so they can print debug information out
	 * easily. Signature stolen from Mozilla/Firefox.
	 */
	public static void dump(String text) {
		System.out.print("JS dump: " + text);
	}
	
	/*
	 * Utility method for JS scripts, so they can print debug information out
	 * easily.
	 */
	public static void dumpln(String text) {
		System.out.println("JS dump: " + text);
	}
	
	protected Object createPlugin(String pluginName, URL fromLocation) throws ChoobException {
		System.out.println("JavaScriptPluginManager.createPlugin");
		
		String code = "";
		URLConnection con;
		try {
			// First thing's first; we must connect to the identified resource.
			con = fromLocation.openConnection();
		} catch(IOException e) {
			throw new ChoobException("Unable to open a connection to the source location <" + fromLocation + ">.");
		}
		try {
			/*
			 * This lot reads the resource in in chunks, using a buffer, and
			 * simply keeps the code in a local variable (once it has be
			 * evaluated in a plugin instances, it is no longer needed).
			 */
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
			throw new ChoobException("Unable to fetch the source from <" + fromLocation + ">.");
		}
		
		// Create the new plugin instance.
		JavaScriptPlugin plug = new JavaScriptPlugin(this, pluginName, code, mods, irc);
		
		// Update bot's overall command list, for spell-check-based suggestions.
		String[] newCommands = new String[0];
		String[] oldCommands = new String[0];
		synchronized(pluginMap)
		{
			List<String> commands;
			
			// Get list of commands for plugin before setting up new one.
			commands = pluginMap.getCommands(pluginName);
			if (commands != null)
				oldCommands = (String[])commands.toArray(oldCommands);
			
			// Clear the old instance's map data and load the new plugin map.
			pluginMap.unloadPluginMap(pluginName);
			pluginMap.loadPluginMap(pluginName, plug);
			
			// Get list of commands for newly loaded plugin.
			commands = pluginMap.getCommands(pluginName);
			if (commands != null)
				newCommands = (String[])commands.toArray(newCommands);
		}
		
		// Update bot's command list now.
		for (int i = 0; i < oldCommands.length; i++)
			removeCommand(oldCommands[i]);
		for (int i = 0; i < newCommands.length; i++)
			addCommand(newCommands[i]);
		
		return plug;
	}
	
	protected void destroyPlugin(String pluginName) throws ChoobException {
		System.out.println("JavaScriptPluginManager.destroyPlugin");
		// FIXME: Implement this! //
	}
	
	public ChoobTask commandTask(String pluginName, String command, Message ev) {
		// Call a command! Look it up, and then call if something was found.
		JavaScriptPluginMethod method = pluginMap.getCommand(pluginName + "." + command);
		if (method != null) {
			return callCommand(method, ev);
		}
		return null;
	}
	
	public ChoobTask intervalTask(String pluginName, Object param) {
		System.out.println("JavaScriptPluginManager.intervalTask");
		// FIXME: Implement this! //
		return null;
	}
	
	public List<ChoobTask> eventTasks(IRCEvent ev) {
		System.out.println("JavaScriptPluginManager.eventTasks");
		// FIXME: Implement this! //
		List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		return tasks;
	}
	
	public List<ChoobTask> filterTasks(Message ev) {
		List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		List<JavaScriptPluginMethod> methods = pluginMap.getFilter(ev.getMessage());
		if (methods != null) {
			for (JavaScriptPluginMethod method: methods) {
				tasks.add(callCommand(method, ev));
			}
		}
		return tasks;
	}
	
	public Object doGeneric(String pluginName, String prefix, String genericName, Object... params) throws ChoobException {
		String fullName = pluginName + "." + prefix + ":" + genericName;
		System.out.println("JavaScriptPluginManager.doGeneric(" + fullName + ")");
		
		JavaScriptPluginMethod method = pluginMap.getGeneric(fullName);
		if (method != null) {
			return callMethod(method, params, CALL_WANT_RESULT);
		}
		return null;
	}
	
	public Object doAPI(String pluginName, String APIName, final Object... params) throws ChoobException {
		System.out.println("JavaScriptPluginManager.doAPI");
		return doGeneric(pluginName, "api", APIName, params);
	}
	
	private ChoobTask callCommand(JavaScriptPluginMethod method, Object param)
	{
		Object[] params = { param, mods, irc };
		
		return (ChoobTask)callMethod(method, params, CALL_WANT_TASK);
	}
	
	private Object callMethod(final JavaScriptPluginMethod method, final Object[] params, int result)
	{
		final JavaScriptPlugin plugin = method.getPlugin();
		
		if (result == CALL_WANT_TASK) {
			String pluginName = plugin.getName();
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
		if (result == CALL_WANT_RESULT) {
			Context cx = Context.enter();
			try {
				Scriptable scope = plugin.getScope();
				Scriptable inst = plugin.getInstance();
				Function function = method.getFunction();
				
				return function.call(cx, scope, inst, params);
			} finally {
				cx.exit();
			}
		}
		return null;
	}
}

final class JavaScriptPluginMap {
	// List of plugins.
	private final Map<String,Object> plugins;
	
	// List of commands for each plugin.
	private final Map<String,List<String>> pluginCommands;
	// List of filters for each plugin.
	private final Map<String,List<NativeRegExp>> pluginFilters;
	// List of generics for each plugin.
	private final Map<String,List<String>> pluginGenerics;
	
	// List of function for each command.
	private final Map<String,JavaScriptPluginMethod> commands;
	// List of function for each filter.
	private final Map<NativeRegExp,JavaScriptPluginMethod> filters;
	// List of function for each generic.
	private final Map<String,JavaScriptPluginMethod> generics;
	
	public JavaScriptPluginMap() {
		plugins = new HashMap<String,Object>();
		pluginCommands = new HashMap<String,List<String>>();
		pluginFilters  = new HashMap<String,List<NativeRegExp>>();
		pluginGenerics = new HashMap<String,List<String>>();
		commands = new HashMap<String,JavaScriptPluginMethod>();
		filters  = new HashMap<NativeRegExp,JavaScriptPluginMethod>();
		generics = new HashMap<String,JavaScriptPluginMethod>();
	}
	
	synchronized void loadPluginMap(String pluginName, JavaScriptPlugin pluginObj) {
		System.out.println("JavaScriptPluginMap.loadPluginMap(" + pluginName + ")");
		String lname = pluginName.toLowerCase();
		
		// Set up maps...
		plugins.put(lname, pluginObj);
		List<String> commandNames = new LinkedList<String>();
		pluginCommands.put(lname, commandNames);
		List<NativeRegExp> filterNames = new LinkedList<NativeRegExp>();
		pluginFilters.put(lname, filterNames);
		List<String> genericNames = new LinkedList<String>();
		pluginGenerics.put(lname, genericNames);
		
		Scriptable inst = pluginObj.getInstance();
		while (inst != null) {
			Object[] propList = inst.getIds();
			String propString;
			for (Object prop: propList) {
				if (prop instanceof String) {
					propString = (String)prop;
					if (propString.startsWith("command")) {
						// Looks like a command definition.
						Object propVal = inst.get(propString, inst);
						if (!(propVal instanceof Function)) {
							System.err.println("  Command-like property that is not a function: " + propString);
							continue;
						}
						// It's a function, yay!
						Function func = (Function)propVal;
						JavaScriptPluginMethod method = new JavaScriptPluginMethod(pluginObj, propString, func);
						
						String commandName = lname + "." + propString.substring(7).toLowerCase();
						
						commandNames.add(commandName);
						commands.put(commandName, method);
						
						System.out.println("  Added command: " + commandName);
						
					} else if (propString.startsWith("filter")) {
						// Looks like a filter definition.
						Object propVal = inst.get(propString, inst);
						if (!(propVal instanceof Function)) {
							System.err.println("  Filter-like property that is not a function: " + propString);
							continue;
						}
						// It's a function, yay!
						Function func = (Function)propVal;
						
						Object regexpVal = func.get("regexp", func);
						if (regexpVal == Scriptable.NOT_FOUND) {
							System.err.println("  Filter function (" + propString + ") missing 'regexp' property.");
							continue;
						}
						if (!(regexpVal instanceof NativeRegExp)) {
							System.err.println("  Filter function (" + propString + ") property 'regexp' is not a Regular Expression: " + regexpVal.getClass().getName());
							continue;
						}
						
						JavaScriptPluginMethod method = new JavaScriptPluginMethod(pluginObj, propString, func);
						
						String filterName = lname + "." + propString.substring(6).toLowerCase();
						NativeRegExp filterPattern = (NativeRegExp)regexpVal;
						
						filterNames.add(filterPattern);
						filters.put(filterPattern, method);
						
						System.out.println("  Added filter: " + filterName);
						
					} else {
						Matcher matcher = Pattern.compile("([a-z]+)([A-Z].+)?").matcher(propString);
						if (matcher.matches()) {
							// Looks like a generic definition.
							Object propVal = inst.get(propString, inst);
							if (!(propVal instanceof Function)) {
								System.err.println("  Generic-like property that is not a function: " + propString);
								continue;
							}
							// It's a function, yay!
							Function func = (Function)propVal;
							JavaScriptPluginMethod method = new JavaScriptPluginMethod(pluginObj, propString, func);
							
							String prefix = matcher.group(1);
							String gName = propString.substring(prefix.length()).toLowerCase();
							String fullName = lname + "." + prefix + ":" + gName;
							
							genericNames.add(fullName);
							generics.put(fullName, method);
							
							System.out.println("  Added generic: " + fullName);
							
						} else {
							System.err.println("  Unknown property: " + propString);
						}
					}
				} else {
					System.out.println("  Found property of type: " + prop.getClass().getName());
				}
			}
			inst = inst.getPrototype();
		}
	}
	
	synchronized void unloadPluginMap(String pluginName) {
		System.out.println("JavaScriptPluginMap.unloadPluginMap(" + pluginName + ")");
		String lname = pluginName.toLowerCase();
		
		if (plugins.get(lname) == null) {
			return;
		}
		
		plugins.remove(lname);
		for (String command: getCommands(pluginName)) {
			commands.remove(command);
			System.out.println("  Removed command: " + command);
		}
		pluginCommands.remove(lname);
	}
	
	synchronized JavaScriptPluginMethod getCommand(String commandName) {
		return commands.get(commandName.toLowerCase());
	}
	
	synchronized List<String> getCommands(String pluginName) {
		return pluginCommands.get(pluginName.toLowerCase());
	}
	
	synchronized List<JavaScriptPluginMethod> getFilter(String message) {
		List<JavaScriptPluginMethod> rv = new LinkedList<JavaScriptPluginMethod>();
		Iterator<NativeRegExp> regexps = filters.keySet().iterator();
		while (regexps.hasNext()) {
			NativeRegExp regexp = regexps.next();
			JavaScriptPluginMethod method = filters.get(regexp);
			JavaScriptPlugin plugin = method.getPlugin();
			Scriptable scope = plugin.getScope();
			Object[] args = { message };
			
			Context cx = Context.enter();
			try {
				Object ret = regexp.call(cx, scope, null, args);
				if (ret != null) {
					rv.add(method);
				}
			} finally {
				cx.exit();
			}
		}
		return rv;
	}
	
	synchronized JavaScriptPluginMethod getGeneric(String genericName) {
		return generics.get(genericName.toLowerCase());
	}
}

/*
 * This class represents a single function in a plugin that can be called from
 * the outside. It keeps track of the plugin instance, the function name, and
 * the actual function so it can be identified and called with the right
 * scope (in JS, the call scope must be preserved, and this is handled by the
 * plugin object here).
 */
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
				throw new ChoobException("Constructor property '" + pluginName + "' for JavaScript plugin not found.");
			}
			if (!(ctor instanceof Function)) {
				throw new ChoobException("Constructor property '" + pluginName + "' for JavaScript plugin is not a function.");
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
