/*
 * Created on October 8, 2005, 9:33 PM
 */
package org.uwcs.choob.plugins;

import java.io.*;
import java.net.*;
import java.security.*;
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
		System.out.println("JavaScriptPluginManager.intervalTask(" + pluginName + ")");
		// Call the interval callback function.
		JavaScriptPluginMethod method = pluginMap.getInterval(pluginName);
		if (method != null) {
			return callCommand(method, param);
		}
		return null;
	}
	
	public List<ChoobTask> eventTasks(IRCEvent ev) {
		System.out.println("JavaScriptPluginManager.eventTasks(" + ev.getMethodName() + ")");
		List<ChoobTask> events = new LinkedList<ChoobTask>();
		List<JavaScriptPluginMethod> methods = pluginMap.getEvent(ev.getMethodName());
		if (methods != null) {
			for (JavaScriptPluginMethod method: methods) {
				events.add(callCommand(method, ev));
			}
		}
		return events;
	}
	
	public List<ChoobTask> filterTasks(Message ev) {
		System.out.println("JavaScriptPluginManager.filterTasks");
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
	
	private Object callMethod(final JavaScriptPluginMethod method, final Object[] params, final int result)
	{
		final JavaScriptPlugin plugin = method.getPlugin();
		final String pluginName = plugin.getName();
		
		ProtectionDomain accessDomain = mods.security.getProtectionDomain(pluginName);
		final AccessControlContext accessContext = new AccessControlContext(new ProtectionDomain[] { accessDomain });
		final PrivilegedExceptionAction action = new PrivilegedExceptionAction() {
			public Object run() throws ChoobException {
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
		};
		
		if (result == CALL_WANT_TASK) {
			final Object[] params2 = params;
			return new ChoobTask(pluginName) {
				public void run() {
					try {
						AccessController.doPrivileged(action, accessContext);
					} catch (PrivilegedActionException e) {
						//throw (ChoobException)(e.getCause());
					}
				}
			};
		}
		if (result == CALL_WANT_RESULT) {
			try {
				return AccessController.doPrivileged(action, accessContext);
			} catch (PrivilegedActionException e) {
				//throw (ChoobException)(e.getCause());
			}
		}
		return null;
	}
}

final class JavaScriptPluginMap {
	/* Naming for items loaded from plugins:
	 *
	 * TYPE      NAME IN PLUGIN  RELATION  NAME IN MAP
	 * Command   commandFoo      one       pluginname.foo
	 * Event     onFoo           many      onfoo
	 * Filter    filterFoo       ?         /regexp/
	 * Generic   otherFooBar     one       pluginname.other:foobar
	 * Interval  interval        one       pluginname
	 */
	
	// List of plugins.
	private final Map<String,JavaScriptPlugin> plugins;
	// List of function for each command.
	private final Map<String,JavaScriptPluginMethod> commands;
	// List of function for each event.
	private final Map<String,List<JavaScriptPluginMethod>> events;
	// List of function for each filter.
	private final Map<NativeRegExp,JavaScriptPluginMethod> filters;
	// List of function for each generic.
	private final Map<String,JavaScriptPluginMethod> generics;
	// List of function for each interval callback.
	private final Map<String,JavaScriptPluginMethod> intervals;
	
	public JavaScriptPluginMap() {
		plugins   = new HashMap<String,JavaScriptPlugin>();
		
		commands  = new HashMap<String,JavaScriptPluginMethod>();
		events    = new HashMap<String,List<JavaScriptPluginMethod>>();
		filters   = new HashMap<NativeRegExp,JavaScriptPluginMethod>();
		generics  = new HashMap<String,JavaScriptPluginMethod>();
		intervals = new HashMap<String,JavaScriptPluginMethod>();
	}
	
	synchronized void loadPluginMap(String pluginName, JavaScriptPlugin pluginObj) {
		System.out.println("JavaScriptPluginMap.loadPluginMap(" + pluginName + ")");
		String lname = pluginName.toLowerCase();
		
		System.out.println("Loading " + pluginName + ":");
		System.out.println("  TYPE      NAME");
		
		plugins.put(lname, pluginObj);
		
		int count = 0;
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
							System.err.println("  WARNING: Command-like property that is not a function: " + propString);
							continue;
						}
						// It's a function, yay!
						Function func = (Function)propVal;
						JavaScriptPluginMethod method = new JavaScriptPluginMethod(pluginObj, propString, func);
						
						String commandName = lname + "." + propString.substring(7).toLowerCase();
						commands.put(commandName, method);
						count++;
						System.out.println("  Command   " + commandName);
						
					} else if (propString.startsWith("on")) {
						// Looks like an event handler definition.
						Object propVal = inst.get(propString, inst);
						if (!(propVal instanceof Function)) {
							System.err.println("  WARNING: Event-like property that is not a function: " + propString);
							continue;
						}
						// It's a function, yay!
						Function func = (Function)propVal;
						JavaScriptPluginMethod method = new JavaScriptPluginMethod(pluginObj, propString, func);
						
						String eventName = propString.toLowerCase();
						if (events.get(eventName) == null) {
							events.put(eventName, new LinkedList<JavaScriptPluginMethod>());
						}
						events.get(eventName).add(method);
						count++;
						System.out.println("  Event     " + eventName + " (" + pluginName + ")");
						
					} else if (propString.startsWith("filter")) {
						// Looks like a filter definition.
						Object propVal = inst.get(propString, inst);
						if (!(propVal instanceof Function)) {
							System.err.println("  WARNING: Filter-like property that is not a function: " + propString);
							continue;
						}
						// It's a function, yay!
						Function func = (Function)propVal;
						
						Object regexpVal = func.get("regexp", func);
						if (regexpVal == Scriptable.NOT_FOUND) {
							System.err.println("  WARNING: Filter function (" + propString + ") missing 'regexp' property.");
							continue;
						}
						if (!(regexpVal instanceof NativeRegExp)) {
							System.err.println("  WARNING: Filter function (" + propString + ") property 'regexp' is not a Regular Expression: " + regexpVal.getClass().getName());
							continue;
						}
						
						JavaScriptPluginMethod method = new JavaScriptPluginMethod(pluginObj, propString, func);
						
						String filterName = lname + "." + propString.substring(6).toLowerCase();
						NativeRegExp filterPattern = (NativeRegExp)regexpVal;
						filters.put(filterPattern, method);
						count++;
						System.out.println("  Filter    " + filterPattern + " (" + pluginName + ")");
						
					} else if (propString.equals("interval")) {
						// Looks like an interval callback.
						Object propVal = inst.get(propString, inst);
						if (!(propVal instanceof Function)) {
							System.err.println("  WARNING: Interval-like property that is not a function: " + propString);
							continue;
						}
						// It's a function, yay!
						Function func = (Function)propVal;
						
						JavaScriptPluginMethod method = new JavaScriptPluginMethod(pluginObj, propString, func);
						intervals.put(lname, method);
						count++;
						System.out.println("  Interval  " + lname);
						
					} else {
						Matcher matcher = Pattern.compile("([a-z]+)([A-Z].+)?").matcher(propString);
						if (matcher.matches()) {
							// Looks like a generic definition.
							Object propVal = inst.get(propString, inst);
							if (!(propVal instanceof Function)) {
								System.err.println("  WARNING: Generic-like property that is not a function: " + propString);
								continue;
							}
							// It's a function, yay!
							Function func = (Function)propVal;
							JavaScriptPluginMethod method = new JavaScriptPluginMethod(pluginObj, propString, func);
							
							String prefix = matcher.group(1);
							String gName = propString.substring(prefix.length()).toLowerCase();
							String fullName = lname + "." + prefix + ":" + gName;
							generics.put(fullName, method);
							count++;
							System.out.println("  Generic   " + fullName);
							
						} else {
							System.err.println("  WARNING: Unknown property: " + propString);
						}
					}
				} else {
					System.out.println("  Found property of type: " + prop.getClass().getName());
				}
			}
			inst = inst.getPrototype();
		}
		System.out.println("Done (" + count + " items added).");
	}
	
	synchronized void unloadPluginMap(String pluginName) {
		System.out.println("JavaScriptPluginMap.unloadPluginMap(" + pluginName + ")");
		String lname = pluginName.toLowerCase();
		
		if (plugins.get(lname) == null) {
			return;
		}
		JavaScriptPlugin pluginObj = plugins.get(lname);
		
		System.out.println("Unloading " + pluginName + ":");
		System.out.println("  TYPE      NAME");
		
		int count = 0;
		// Commands
		List<String> commandsToRemove = new LinkedList<String>();
		for (String command: commands.keySet()) {
			if (commands.get(command).getPlugin() == pluginObj) {
				commandsToRemove.add(command);
			}
		}
		for (String command: commandsToRemove) {
			System.out.println("  Command   " + command);
			count++;
			commands.remove(command);
		}
		// Events
		for (String event: events.keySet()) {
			List<JavaScriptPluginMethod> eventHooksToRemove = new LinkedList<JavaScriptPluginMethod>();
			for (JavaScriptPluginMethod method: events.get(event)) {
				if (method.getPlugin() == pluginObj) {
					eventHooksToRemove.add(method);
				}
			}
			for (JavaScriptPluginMethod method: eventHooksToRemove) {
				System.out.println("  Event     " + event + " (" + method.getPlugin().getName() + ")");
				count++;
				events.get(event).remove(method);
			}
		}
		// Filters
		List<NativeRegExp> filtersToRemove = new LinkedList<NativeRegExp>();
		for (NativeRegExp filter: filters.keySet()) {
			if (filters.get(filter).getPlugin() == pluginObj) {
				filtersToRemove.add(filter);
			}
		}
		for (NativeRegExp filter: filtersToRemove) {
			System.out.println("  Filter    " + filter + " (" + filters.get(filter).getPlugin().getName() + ")");
			count++;
			filters.remove(filter);
		}
		// Generics
		List<String> genericsToRemove = new LinkedList<String>();
		for (String generic: generics.keySet()) {
			if (generics.get(generic).getPlugin() == pluginObj) {
				genericsToRemove.add(generic);
			}
		}
		for (String generic: genericsToRemove) {
			System.out.println("  Generic   " + generic);
			count++;
			generics.remove(generic);
		}
		// Intervals
		if (intervals.get(lname) != null) {
			System.out.println("  Interval  " + lname);
			count++;
			intervals.remove(lname);
		}
		plugins.remove(lname);
		
		System.out.println("Done (" + count + " items removed).");
		
		/*for (String command: pluginCommands.get(lname)) {
			commands.remove(command);
			System.out.println("  Command   " + command);
		}
		for (String event: pluginEvents.get(lname)) {
			events.remove(event);
			System.out.println("  Event     " + event);
		}
		for (NativeRegExp filter: pluginFilters.get(lname)) {
			filters.remove(filter);
			System.out.println("  Filter    " + filter);
		}
		for (String generic: pluginGenerics.get(lname)) {
			generics.remove(generic);
			System.out.println("  Generic   " + generic);
		}
		if (intervals.get(lname) != null) {
			intervals.remove(lname);
			System.out.println("  Interval  " + lname);
		}
		pluginCommands.remove(lname);
		pluginFilters.remove(lname);
		pluginGenerics.remove(lname);*/
	}
	
	synchronized List<String> getCommands(String pluginName) {
		JavaScriptPlugin pluginObj = plugins.get(pluginName.toLowerCase());
		List<String> rv = new LinkedList<String>();
		
		for (String command: commands.keySet()) {
			if (commands.get(command).getPlugin() == pluginObj) {
				rv.add(command);
			}
		}
		
		return rv;
	}
	
	synchronized JavaScriptPluginMethod getCommand(String commandName) {
		return commands.get(commandName.toLowerCase());
	}
	
	synchronized List<JavaScriptPluginMethod> getEvent(String eventName) {
		Object event = events.get(eventName.toLowerCase());
		if (event == null) {
			return null;
		}
		LinkedList<JavaScriptPluginMethod> list = (LinkedList<JavaScriptPluginMethod>)event;
		return (List<JavaScriptPluginMethod>)list.clone();
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
	
	synchronized JavaScriptPluginMethod getInterval(String pluginName) {
		return intervals.get(pluginName.toLowerCase());
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
			final Object args[] = { mods, irc };
			final Scriptable scopeF = scope;
			final String pluginNameF = pluginName;
			final Context cxF = cx;
			
			ProtectionDomain accessDomain = mods.security.getProtectionDomain(pluginName);
			AccessControlContext accessContext = new AccessControlContext(new ProtectionDomain[] { accessDomain });
			try {
				inst = (Scriptable)AccessController.doPrivileged(new PrivilegedExceptionAction() {
						public Object run() throws ChoobException {
							return cxF.newObject(scopeF, pluginNameF, args);
						}
					}, accessContext);
			} catch (PrivilegedActionException e) {
				throw (ChoobException)(e.getCause());
			}
			
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
