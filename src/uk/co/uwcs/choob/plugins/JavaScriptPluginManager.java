/*
 * Created on October 8, 2005, 9:33 PM
 */
package uk.co.uwcs.choob.plugins;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.regex.*;
import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.support.events.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.modules.*;
import org.mozilla.javascript.*;
import org.mozilla.javascript.regexp.*;

/*
 * Deals with all the magic for JavaScript plugins. Woo.
 * @author silver
 */
public final class JavaScriptPluginManager extends ChoobPluginManager {
	/*
	 * The plugin map tracks which plugin instances have which commands, and
	 * keeps a command name --> function map in particular.
	 */
	private JavaScriptPluginMap pluginMap;
	// For passing to plugin constructors.
	private final Modules mods;
	private final IRCInterface irc;

	private final int CALL_WANT_TASK   = 1;
	private final int CALL_WANT_RESULT = 2;

	public JavaScriptPluginManager(Modules mods, IRCInterface irc) {
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
		String code = "";
		URLConnection con;
		try {
			// First thing's first; we must connect to the identified resource.
			con = fromLocation.openConnection();
		} catch(IOException e) {
			e.printStackTrace();
			throw new ChoobException("Unable to open a connection to the source location <" + fromLocation + ">.");
		}
		try {
			/*
			 * This lot reads the resource in in lines, using a buffer, and
			 * simply keeps the code in a local variable (once it has be
			 * evaluated in a plugin instances, it is no longer needed).
			 */
			con.connect();
			InputStream stream = con.getInputStream();
			InputStreamReader streamReader = new InputStreamReader(stream);
			BufferedReader bufferedStreamReader = new BufferedReader(streamReader);

			String line;
			while ((line = bufferedStreamReader.readLine()) != null) {
				code += line + "\n";
			}
		} catch(IOException e) {
			e.printStackTrace();
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
				oldCommands = commands.toArray(oldCommands);

			// Clear the old instance's map data and load the new plugin map.
			pluginMap.unloadPluginMap(pluginName);
			pluginMap.loadPluginMap(pluginName, plug);

			// Get list of commands for newly loaded plugin.
			commands = pluginMap.getCommands(pluginName);
			if (commands != null)
				newCommands = commands.toArray(newCommands);
		}

		// Update bot's command list now.
		for (int i = 0; i < oldCommands.length; i++)
			removeCommand(oldCommands[i]);
		for (int i = 0; i < newCommands.length; i++)
			addCommand(newCommands[i]);

		return plug;
	}

	protected void destroyPlugin(String pluginName) {
		// Update bot's overall command list, for spell-check-based suggestions.
		String[] oldCommands = new String[0];
		synchronized(pluginMap)
		{
			List<String> commands;

			// Get list of commands for plugin before setting up new one.
			commands = pluginMap.getCommands(pluginName);
			if (commands != null)
				oldCommands = commands.toArray(oldCommands);

			// Clear the old instance's map data.
			pluginMap.unloadPluginMap(pluginName);
		}

		// Update bot's command list now.
		for (int i = 0; i < oldCommands.length; i++)
			removeCommand(oldCommands[i]);
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
		// Call the interval callback function.
		JavaScriptPluginMethod method = pluginMap.getInterval(pluginName);
		if (method != null) {
			return callCommand(method, param);
		}
		return null;
	}

	public List<ChoobTask> eventTasks(Event ev) {
		// Call the event hook functions.
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
		// Call the filter hook functions.
		List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		List<JavaScriptPluginMethod> methods = pluginMap.getFilter(ev.getMessage());
		if (methods != null) {
			for (JavaScriptPluginMethod method: methods) {
				tasks.add(callCommand(method, ev));
			}
		}
		return tasks;
	}

	public Object doGeneric(String pluginName, String prefix, String genericName, Object... params) throws ChoobNoSuchCallException {
		// Call a psecific type of generic, by constructing the right name.
		String fullName = pluginName + "." + prefix + ":" + genericName;

		JavaScriptPluginExport method = pluginMap.getGeneric(fullName);
		if (method == null) {
			/*
			 * This is for compatibility with the Help plugin, which assumes
			 * that doGeneric will throw this exact exception if it is not
			 * found, contrary to all the other methods which just return null.
			 */
			throw new ChoobNoSuchCallException(pluginName, fullName);
		}
		return callMethod(method, params, CALL_WANT_RESULT);
	}

	public Object doAPI(String pluginName, String APIName, final Object... params) throws ChoobNoSuchCallException {
		return doGeneric(pluginName, "api", APIName, params);
	}

	private ChoobTask callCommand(JavaScriptPluginMethod method, Object param) {
		Object[] params = { param, mods, irc };

		return (ChoobTask)callMethod(method, params, CALL_WANT_TASK);
	}

	/*
	 * This calls any export a plugin has, be it a method or a property, and
	 * can return one of two things: the result, or a ChoobTask that when run
	 * does the call and gives the result.
	 *
	 * It does all the work inside a protection domain, which identifies the
	 * plugin that is being called, so that the code can correctly identify it.
	 */
	private Object callMethod(final JavaScriptPluginExport export, final Object[] params, final int result) {
		final JavaScriptPlugin plugin = export.getPlugin();
		final String pluginName = plugin.getName();
		final String fullName = pluginName + "." + export.getName();

		ProtectionDomain accessDomain = mods.security.getProtectionDomain(pluginName);
		final AccessControlContext accessContext = new AccessControlContext(new ProtectionDomain[] { accessDomain });
		final PrivilegedExceptionAction<Object> action = new PrivilegedExceptionAction<Object>() {
			public Object run() throws Exception {
				Context cx = Context.enter();
				try {
					Scriptable scope = plugin.getScope();
					Scriptable inst = plugin.getInstance();

					if (export instanceof JavaScriptPluginMethod) {
						JavaScriptPluginMethod method = (JavaScriptPluginMethod)export;
						Function function = method.getFunction();

						return JSUtils.mapJSToJava(function.call(cx, scope, inst, params));
					}
					if (export instanceof JavaScriptPluginProperty) {
						JavaScriptPluginProperty prop = (JavaScriptPluginProperty)export;
						return JSUtils.mapJSToJava(prop.getValue());
					}
					throw new ChoobError("Unknown export type for " + export.getName() + ".");

				} catch (RhinoException e) {
					if ((params.length > 0) && (params[0] instanceof Message)) {
						irc.sendContextReply((Message)params[0], e.details() + " Line " + e.lineNumber() + ", col " + e.columnNumber() + " of " + e.sourceName() + ".");
					} else {
						System.err.println("Exception calling export " + export.getName() + ":");
					}
					e.printStackTrace();
					throw e;

				} catch (Exception e) {
					if ((params.length > 0) && (params[0] instanceof Message)) {
						mods.plugin.exceptionReply((Message)params[0], e, pluginName);
					} else {
						System.err.println("Exception calling export " + export.getName() + ":");
					}
					e.printStackTrace();
					throw e;

				} finally {
					cx.exit();
				}
			}
		};

		if (result == CALL_WANT_TASK) {
			return new ChoobTask(pluginName, fullName) {
				public void run() {
					try {
						AccessController.doPrivileged(action, accessContext);
					} catch (Exception e) {
						throw new ChoobInvocationError(pluginName, fullName, e);
					}
				}
			};
		}
		if (result == CALL_WANT_RESULT) {
			try {
				return AccessController.doPrivileged(action, accessContext);
			} catch (Exception e) {
				throw new ChoobInvocationError(pluginName, fullName, e);
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
	private final Map<String,JavaScriptPluginExport> generics;
	// List of function for each interval callback.
	private final Map<String,JavaScriptPluginMethod> intervals;

	public JavaScriptPluginMap() {
		plugins   = new HashMap<String,JavaScriptPlugin>();

		commands  = new HashMap<String,JavaScriptPluginMethod>();
		events    = new HashMap<String,List<JavaScriptPluginMethod>>();
		filters   = new HashMap<NativeRegExp,JavaScriptPluginMethod>();
		generics  = new HashMap<String,JavaScriptPluginExport>();
		intervals = new HashMap<String,JavaScriptPluginMethod>();
	}

	synchronized void loadPluginMap(String pluginName, JavaScriptPlugin pluginObj) {
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

						// Check for command help.
						Object helpVal = func.get("help", func);
						if (helpVal == Scriptable.NOT_FOUND) {
							continue;
						}

						JavaScriptPluginExport helpExport;
						if (helpVal instanceof Function) {
							// It's a function, yay!
							Function helpFunc = (Function)helpVal;
							helpExport = new JavaScriptPluginMethod(pluginObj, propString + ".help", helpFunc);
						} else {
							helpExport = new JavaScriptPluginProperty(pluginObj, propString + ".help");
						}

						String fullName = lname + ".help:" + propString.toLowerCase();
						generics.put(fullName, helpExport);
						count++;
						System.out.println("  Generic   " + fullName);

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

							JavaScriptPluginExport export;
							if (propVal instanceof Function) {
								// It's a function, yay!
								Function func = (Function)propVal;
								export = new JavaScriptPluginMethod(pluginObj, propString, func);
							} else {
								export = new JavaScriptPluginProperty(pluginObj, propString);
							}

							String prefix = matcher.group(1);
							String gName = propString.substring(prefix.length()).toLowerCase();
							String fullName = lname + "." + prefix + ":" + gName;
							generics.put(fullName, export);
							count++;
							System.out.println("  Generic   " + fullName);
						}
					}
				}
			}
			inst = inst.getPrototype();
		}
		System.out.println("Done (" + count + " items added).");
	}

	synchronized void unloadPluginMap(String pluginName) {
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

	synchronized JavaScriptPluginExport getGeneric(String genericName) {
		return generics.get(genericName.toLowerCase());
	}

	synchronized JavaScriptPluginMethod getInterval(String pluginName) {
		return intervals.get(pluginName.toLowerCase());
	}
}

class JavaScriptPluginExport {
	private JavaScriptPlugin plugin;
	private String name;

	public JavaScriptPluginExport(JavaScriptPlugin plugin, String name) {
		this.plugin = plugin;
		this.name = name;
	}

	public JavaScriptPlugin getPlugin() {
		return plugin;
	}

	public String getName() {
		return name;
	}
}

/*
 * This class represents a single function in a plugin that can be called from
 * the outside. It keeps track of the plugin instance, the function name, and
 * the actual function so it can be identified and called with the right
 * scope (in JS, the call scope must be preserved, and this is handled by the
 * plugin object here).
 */
final class JavaScriptPluginMethod extends JavaScriptPluginExport {
	private Function function;

	public JavaScriptPluginMethod(JavaScriptPlugin plugin, String name, Function function) {
		super(plugin, name);
		this.function = function;
	}

	public Function getFunction() {
		return function;
	}
}

final class JavaScriptPluginProperty extends JavaScriptPluginExport {
	public JavaScriptPluginProperty(JavaScriptPlugin plugin, String name) {
		super(plugin, name);
	}

	public Object getValue() throws NoSuchFieldException {
		String[] parts = getName().split("\\.");
		Scriptable obj = getPlugin().getInstance();

		for (int i = 0; i < parts.length; i++) {
			obj = getObjectProp(obj, parts[i]);
		}
		return obj;
	}

	private static Scriptable getObjectProp(Scriptable obj, String prop) throws NoSuchFieldException {
		Object val = JSUtils.getProperty(obj, prop);
		if (!(val instanceof Scriptable)) {
			throw new NoSuchFieldException(prop);
		}
		return (Scriptable)val;
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
				int flags = ScriptableObject.READONLY | ScriptableObject.DONTENUM | ScriptableObject.PERMANENT;
				ScriptableObject.defineProperty(scope, "__jsplugman_pluginName", pluginName, flags);
				ScriptableObject.defineProperty(scope, "dump", new FunctionObject("dump", JavaScriptPluginManager.class.getMethod("dump", String.class), scope), flags);
				ScriptableObject.defineProperty(scope, "dumpln", new FunctionObject("dumpln", JavaScriptPluginManager.class.getMethod("dumpln", String.class), scope), flags);
			} catch(NoSuchMethodException e) {
				System.err.println("Method not found: " + e);
				// Ignore for now.
			}

			// Pull in script.
			cx.evaluateString(scope, code, pluginName, 1, null);
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
				inst = AccessController.doPrivileged(new PrivilegedExceptionAction<Scriptable>() {
						public Scriptable run() {
							return cxF.newObject(scopeF, pluginNameF, args);
						}
					}, accessContext);
			} catch (PrivilegedActionException e) {
				throw (ChoobException)(e.getCause());
			}

		} catch (EvaluatorException e) {
			throw new ChoobException(e.details() + " at line " + e.lineNumber() + ", col " + e.columnNumber() + " of " + e.sourceName() + ".");

		} catch (RhinoException e) {
			throw new ChoobException(e.details() + " Line " + e.lineNumber() + ", col " + e.columnNumber() + " of " + e.sourceName() + ".");

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
