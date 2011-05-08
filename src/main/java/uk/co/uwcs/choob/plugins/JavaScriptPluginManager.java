/*
 * Created on October 8, 2005, 9:33 PM
 */
package uk.co.uwcs.choob.plugins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.regexp.NativeRegExp;

import uk.co.uwcs.choob.ChoobPluginManager;
import uk.co.uwcs.choob.ChoobPluginManagerState;
import uk.co.uwcs.choob.ChoobTask;
import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobError;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.ChoobInvocationError;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.JSUtils;
import uk.co.uwcs.choob.support.events.Event;
import uk.co.uwcs.choob.support.events.Message;

/*
 * Deals with all the magic for JavaScript plugins. Woo.
 * @author silver
 */
public final class JavaScriptPluginManager extends ChoobPluginManager {
	/*
	 * The plugin map tracks which plugin instances have which commands, and
	 * keeps a command name --> function map in particular.
	 */
	private final JavaScriptPluginMap pluginMap;
	// For passing to plugin constructors.
	private final Modules mods;
	private final IRCInterface irc;

	private final int CALL_WANT_TASK   = 1;
	private final int CALL_WANT_RESULT = 2;

	public JavaScriptPluginManager(final Modules mods, final IRCInterface irc, ChoobPluginManagerState state) {
		super(mods, state);
		this.mods = mods;
		this.irc = irc;
		this.pluginMap = new JavaScriptPluginMap();
	}

	/*
	 * Utility method for JS scripts, so they can print debug information out
	 * easily. Signature stolen from Mozilla/Firefox.
	 */
	public static void dump(final String text) {
		System.out.print("JS dump: " + text);
	}

	/*
	 * Utility method for JS scripts, so they can print debug information out
	 * easily.
	 */
	public static void dumpln(final String text) {
		System.out.println("JS dump: " + text);
	}

	@Override
	protected Object createPlugin(final String pluginName, final URL fromLocation) throws ChoobException {
		String code = "";
		URLConnection con;
		try {
			// First thing's first; we must connect to the identified resource.
			con = fromLocation.openConnection();
		} catch(final IOException e) {
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
			final InputStream stream = con.getInputStream();
			final InputStreamReader streamReader = new InputStreamReader(stream);
			final BufferedReader bufferedStreamReader = new BufferedReader(streamReader);

			String line;
			while ((line = bufferedStreamReader.readLine()) != null) {
				code += line + "\n";
			}
		} catch(final IOException e) {
			e.printStackTrace();
			throw new ChoobException("Unable to fetch the source from <" + fromLocation + ">.");
		}

		// Create the new plugin instance.
		final JavaScriptPlugin plug = new JavaScriptPlugin(this, pluginName, code, mods, irc);

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
		for (final String oldCommand : oldCommands)
			removeCommand(oldCommand);
		for (final String newCommand : newCommands)
			addCommand(newCommand);

		return plug;
	}

	@Override
	protected void destroyPlugin(final String pluginName) {
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
		for (final String oldCommand : oldCommands)
			removeCommand(oldCommand);
	}

	@Override
	public ChoobTask commandTask(final String pluginName, final String command, final Message ev) {
		// Call a command! Look it up, and then call if something was found.
		final JavaScriptPluginMethod method = pluginMap.getCommand(pluginName + "." + command);
		if (method != null) {
			return callCommand(method, ev);
		}
		return null;
	}

	@Override
	public ChoobTask intervalTask(final String pluginName, final Object param) {
		// Call the interval callback function.
		final JavaScriptPluginMethod method = pluginMap.getInterval(pluginName);
		if (method != null) {
			return callCommand(method, param);
		}
		return null;
	}

	@Override
	public List<ChoobTask> eventTasks(final Event ev) {
		// Call the event hook functions.
		final List<ChoobTask> events = new LinkedList<ChoobTask>();
		final List<JavaScriptPluginMethod> methods = pluginMap.getEvent(ev.getMethodName());
		if (methods != null) {
			for (final JavaScriptPluginMethod method: methods) {
				events.add(callCommand(method, ev));
			}
		}
		return events;
	}

	@Override
	public List<ChoobTask> filterTasks(final Message ev) {
		// Call the filter hook functions.
		final List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		final List<JavaScriptPluginMethod> methods = pluginMap.getFilter(ev.getMessage());
		if (methods != null) {
			for (final JavaScriptPluginMethod method: methods) {
				tasks.add(callCommand(method, ev));
			}
		}
		return tasks;
	}

	@Override
	public Object doGeneric(final String pluginName, final String prefix, final String genericName, final Object... params) throws ChoobNoSuchCallException {
		// Call a psecific type of generic, by constructing the right name.
		final String fullName = pluginName + "." + prefix + ":" + genericName;

		final JavaScriptPluginExport method = pluginMap.getGeneric(fullName);
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

	@Override
	public Object doAPI(final String pluginName, final String APIName, final Object... params) throws ChoobNoSuchCallException {
		return doGeneric(pluginName, "api", APIName, params);
	}

	private ChoobTask callCommand(final JavaScriptPluginMethod method, final Object param) {
		final Object[] params = { param, mods, irc };

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

		final ProtectionDomain[] domain = new ProtectionDomain[] {
				mods.security.getContextProtectionDomain(),
				mods.security.getProtectionDomain(pluginName)
			};
		final AccessControlContext accessContext = new AccessControlContext(domain);
		final PrivilegedExceptionAction<Object> action = new PrivilegedExceptionAction<Object>() {
			@Override public Object run() throws Exception {
				final Context cx = Context.enter();
				try {
					final Scriptable scope = plugin.getScope();
					final Scriptable inst = plugin.getInstance();

					if (export instanceof JavaScriptPluginMethod) {
						final JavaScriptPluginMethod method = (JavaScriptPluginMethod)export;
						final Function function = method.getFunction();

						return JSUtils.mapJSToJava(function.call(cx, scope, inst, params));
					}
					if (export instanceof JavaScriptPluginProperty) {
						final JavaScriptPluginProperty prop = (JavaScriptPluginProperty)export;
						return JSUtils.mapJSToJava(prop.getValue());
					}
					throw new ChoobError("Unknown export type for " + export.getName() + ".");

				} catch (final RhinoException e) {
					if (params.length > 0 && params[0] instanceof Message) {
						irc.sendContextReply((Message)params[0], e.details() + " Line " + e.lineNumber() + ", col " + e.columnNumber() + " of " + e.sourceName() + ".");
					} else {
						System.err.println("Exception calling export " + export.getName() + ":");
					}
					e.printStackTrace();
					throw e;

				} catch (final Exception e) {
					if (params.length > 0 && params[0] instanceof Message) {
						mods.plugin.exceptionReply((Message)params[0], e, pluginName);
					} else {
						System.err.println("Exception calling export " + export.getName() + ":");
					}
					e.printStackTrace();
					throw e;

				} finally {
					Context.exit();
				}
			}
		};

		if (result == CALL_WANT_TASK) {
			return new ChoobTask(pluginName, fullName) {
				@Override
				public void run() {
					try {
						AccessController.doPrivileged(action, accessContext);
					} catch (final Exception e) {
						throw new ChoobInvocationError(pluginName, fullName, e);
					}
				}
			};
		}
		if (result == CALL_WANT_RESULT) {
			try {
				return AccessController.doPrivileged(action, accessContext);
			} catch (final Exception e) {
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

	synchronized void loadPluginMap(final String pluginName, final JavaScriptPlugin pluginObj) {
		final String lname = pluginName.toLowerCase();

		//System.out.println("Loading " + pluginName + ":");
		//System.out.println("  TYPE      NAME");

		plugins.put(lname, pluginObj);

		int count = 0;
		Scriptable inst = pluginObj.getInstance();
		while (inst != null) {
			final Object[] propList = inst.getIds();
			String propString;
			for (final Object prop: propList) {
				if (prop instanceof String) {
					propString = (String)prop;
					if (propString.startsWith("command")) {
						// Looks like a command definition.
						final Object propVal = inst.get(propString, inst);
						if (!(propVal instanceof Function)) {
							System.err.println("  WARNING: Command-like property that is not a function: " + propString);
							continue;
						}
						// It's a function, yay!
						final Function func = (Function)propVal;
						final JavaScriptPluginMethod method = new JavaScriptPluginMethod(pluginObj, propString, func);

						final String commandName = lname + "." + propString.substring(7).toLowerCase();
						commands.put(commandName, method);
						count++;
						//System.out.println("  Command   " + commandName);

						// Check for command help.
						final Object helpVal = func.get("help", func);
						if (helpVal == Scriptable.NOT_FOUND) {
							continue;
						}

						JavaScriptPluginExport helpExport;
						if (helpVal instanceof Function) {
							// It's a function, yay!
							final Function helpFunc = (Function)helpVal;
							helpExport = new JavaScriptPluginMethod(pluginObj, propString + ".help", helpFunc);
						} else {
							helpExport = new JavaScriptPluginProperty(pluginObj, propString + ".help");
						}

						final String fullName = lname + ".help:" + propString.toLowerCase();
						generics.put(fullName, helpExport);
						count++;
						//System.out.println("  Generic   " + fullName);

					} else if (propString.startsWith("on")) {
						// Looks like an event handler definition.
						final Object propVal = inst.get(propString, inst);
						if (!(propVal instanceof Function)) {
							System.err.println("  WARNING: Event-like property that is not a function: " + propString);
							continue;
						}
						// It's a function, yay!
						final Function func = (Function)propVal;
						final JavaScriptPluginMethod method = new JavaScriptPluginMethod(pluginObj, propString, func);

						final String eventName = propString.toLowerCase();
						if (events.get(eventName) == null) {
							events.put(eventName, new LinkedList<JavaScriptPluginMethod>());
						}
						events.get(eventName).add(method);
						count++;
						//System.out.println("  Event     " + eventName + " (" + pluginName + ")");

					} else if (propString.startsWith("filter")) {
						// Looks like a filter definition.
						final Object propVal = inst.get(propString, inst);
						if (!(propVal instanceof Function)) {
							System.err.println("  WARNING: Filter-like property that is not a function: " + propString);
							continue;
						}
						// It's a function, yay!
						final Function func = (Function)propVal;

						final Object regexpVal = func.get("regexp", func);
						if (regexpVal == Scriptable.NOT_FOUND) {
							System.err.println("  WARNING: Filter function (" + propString + ") missing 'regexp' property.");
							continue;
						}
						if (!(regexpVal instanceof NativeRegExp)) {
							System.err.println("  WARNING: Filter function (" + propString + ") property 'regexp' is not a Regular Expression: " + regexpVal.getClass().getName());
							continue;
						}

						final JavaScriptPluginMethod method = new JavaScriptPluginMethod(pluginObj, propString, func);

						final NativeRegExp filterPattern = (NativeRegExp)regexpVal;
						filters.put(filterPattern, method);
						count++;
						//System.out.println("  Filter    " + filterPattern + " (" + pluginName + ")");

					} else if (propString.equals("interval")) {
						// Looks like an interval callback.
						final Object propVal = inst.get(propString, inst);
						if (!(propVal instanceof Function)) {
							System.err.println("  WARNING: Interval-like property that is not a function: " + propString);
							continue;
						}
						// It's a function, yay!
						final Function func = (Function)propVal;

						final JavaScriptPluginMethod method = new JavaScriptPluginMethod(pluginObj, propString, func);
						intervals.put(lname, method);
						count++;
						//System.out.println("  Interval  " + lname);

					} else {
						final Matcher matcher = Pattern.compile("([a-z]+)([A-Z].+)?").matcher(propString);
						if (matcher.matches()) {
							// Looks like a generic definition.
							final Object propVal = inst.get(propString, inst);

							JavaScriptPluginExport export;
							if (propVal instanceof Function) {
								// It's a function, yay!
								final Function func = (Function)propVal;
								export = new JavaScriptPluginMethod(pluginObj, propString, func);
							} else {
								export = new JavaScriptPluginProperty(pluginObj, propString);
							}

							final String prefix = matcher.group(1);
							final String gName = propString.substring(prefix.length()).toLowerCase();
							final String fullName = lname + "." + prefix + ":" + gName;
							generics.put(fullName, export);
							count++;
							//System.out.println("  Generic   " + fullName);
						}
					}
				}
			}
			inst = inst.getPrototype();
		}
		//System.out.println("Done (" + count + " items added).");
	}

	synchronized void unloadPluginMap(final String pluginName) {
		final String lname = pluginName.toLowerCase();

		if (plugins.get(lname) == null) {
			return;
		}
		final JavaScriptPlugin pluginObj = plugins.get(lname);

		//System.out.println("Unloading " + pluginName + ":");
		//System.out.println("  TYPE      NAME");

		int count = 0;
		// Commands
		final List<String> commandsToRemove = new LinkedList<String>();
		for (final String command: commands.keySet()) {
			if (commands.get(command).getPlugin() == pluginObj) {
				commandsToRemove.add(command);
			}
		}
		for (final String command: commandsToRemove) {
			//System.out.println("  Command   " + command);
			count++;
			commands.remove(command);
		}
		// Events
		for (final String event: events.keySet()) {
			final List<JavaScriptPluginMethod> eventHooksToRemove = new LinkedList<JavaScriptPluginMethod>();
			for (final JavaScriptPluginMethod method: events.get(event)) {
				if (method.getPlugin() == pluginObj) {
					eventHooksToRemove.add(method);
				}
			}
			for (final JavaScriptPluginMethod method: eventHooksToRemove) {
				//System.out.println("  Event     " + event + " (" + method.getPlugin().getName() + ")");
				count++;
				events.get(event).remove(method);
			}
		}
		// Filters
		final List<NativeRegExp> filtersToRemove = new LinkedList<NativeRegExp>();
		for (final NativeRegExp filter: filters.keySet()) {
			if (filters.get(filter).getPlugin() == pluginObj) {
				filtersToRemove.add(filter);
			}
		}
		for (final NativeRegExp filter: filtersToRemove) {
			//System.out.println("  Filter    " + filter + " (" + filters.get(filter).getPlugin().getName() + ")");
			count++;
			filters.remove(filter);
		}
		// Generics
		final List<String> genericsToRemove = new LinkedList<String>();
		for (final String generic: generics.keySet()) {
			if (generics.get(generic).getPlugin() == pluginObj) {
				genericsToRemove.add(generic);
			}
		}
		for (final String generic: genericsToRemove) {
			//System.out.println("  Generic   " + generic);
			count++;
			generics.remove(generic);
		}
		// Intervals
		if (intervals.get(lname) != null) {
			//System.out.println("  Interval  " + lname);
			count++;
			intervals.remove(lname);
		}
		plugins.remove(lname);

		//System.out.println("Done (" + count + " items removed).");
	}

	synchronized List<String> getCommands(final String pluginName) {
		final JavaScriptPlugin pluginObj = plugins.get(pluginName.toLowerCase());
		final List<String> rv = new LinkedList<String>();

		for (final String command: commands.keySet()) {
			if (commands.get(command).getPlugin() == pluginObj) {
				rv.add(command);
			}
		}

		return rv;
	}

	synchronized JavaScriptPluginMethod getCommand(final String commandName) {
		return commands.get(commandName.toLowerCase());
	}

	synchronized List<JavaScriptPluginMethod> getEvent(final String eventName) {
		final List<JavaScriptPluginMethod> event = events.get(eventName.toLowerCase());
		if (event == null) {
			return null;
		}

		return new LinkedList<JavaScriptPluginMethod>(event);
	}

	synchronized List<JavaScriptPluginMethod> getFilter(final String message) {
		final List<JavaScriptPluginMethod> rv = new LinkedList<JavaScriptPluginMethod>();
		final Iterator<NativeRegExp> regexps = filters.keySet().iterator();
		while (regexps.hasNext()) {
			final NativeRegExp regexp = regexps.next();
			final JavaScriptPluginMethod method = filters.get(regexp);
			final JavaScriptPlugin plugin = method.getPlugin();
			final Scriptable scope = plugin.getScope();
			final Object[] args = { message };

			final Context cx = Context.enter();
			try {
				final Object ret = regexp.call(cx, scope, null, args);
				if (ret != null) {
					rv.add(method);
				}
			} finally {
				Context.exit();
			}
		}
		return rv;
	}

	synchronized JavaScriptPluginExport getGeneric(final String genericName) {
		return generics.get(genericName.toLowerCase());
	}

	synchronized JavaScriptPluginMethod getInterval(final String pluginName) {
		return intervals.get(pluginName.toLowerCase());
	}
}

class JavaScriptPluginExport {
	private final JavaScriptPlugin plugin;
	private final String name;

	public JavaScriptPluginExport(final JavaScriptPlugin plugin, final String name) {
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
	private final Function function;

	public JavaScriptPluginMethod(final JavaScriptPlugin plugin, final String name, final Function function) {
		super(plugin, name);
		this.function = function;
	}

	public Function getFunction() {
		return function;
	}
}

final class JavaScriptPluginProperty extends JavaScriptPluginExport {
	public JavaScriptPluginProperty(final JavaScriptPlugin plugin, final String name) {
		super(plugin, name);
	}

	public Object getValue() throws NoSuchFieldException {
		final String[] parts = getName().split("\\.");
		Scriptable obj = getPlugin().getInstance();

		for (final String part : parts)
		{
			obj = getObjectProp(obj, part);
		}
		return obj;
	}

	private static Scriptable getObjectProp(final Scriptable obj, final String prop) throws NoSuchFieldException {
		final Object val = JSUtils.getProperty(obj, prop);
		if (!(val instanceof Scriptable)) {
			throw new NoSuchFieldException(prop);
		}
		return (Scriptable)val;
	}
}

final class JavaScriptPlugin {
	private final String pluginName;
	private Scriptable scope;
	private Scriptable inst;

	public JavaScriptPlugin(final JavaScriptPluginManager plugMan, final String pluginName, final String code, final Modules mods, final IRCInterface irc) throws ChoobException {
		this.pluginName = pluginName;

		final Context cx = Context.enter();
		try {
			scope = cx.initStandardObjects();
			// Set up dump() and dumpln() functions.
			try {
				final int flags = ScriptableObject.READONLY | ScriptableObject.DONTENUM | ScriptableObject.PERMANENT;
				ScriptableObject.defineProperty(scope, "__jsplugman_pluginName", pluginName, flags);
				ScriptableObject.defineProperty(scope, "dump", new FunctionObject("dump", JavaScriptPluginManager.class.getMethod("dump", String.class), scope), flags);
				ScriptableObject.defineProperty(scope, "dumpln", new FunctionObject("dumpln", JavaScriptPluginManager.class.getMethod("dumpln", String.class), scope), flags);
			} catch(final NoSuchMethodException e) {
				System.err.println("Method not found: " + e);
				// Ignore for now.
			}

			// Pull in script.
			cx.evaluateString(scope, code, pluginName, 1, null);
			final Object ctor = scope.get(pluginName, scope);
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

			final ProtectionDomain accessDomain = mods.security.getProtectionDomain(pluginName);
			final AccessControlContext accessContext = new AccessControlContext(new ProtectionDomain[] { accessDomain });
			try {
				inst = AccessController.doPrivileged(new PrivilegedExceptionAction<Scriptable>() {
						@Override public Scriptable run() {
							return cxF.newObject(scopeF, pluginNameF, args);
						}
					}, accessContext);
			} catch (final PrivilegedActionException e) {
				throw (ChoobException)e.getCause();
			}

		} catch (final EvaluatorException e) {
			throw new ChoobException(e.details() + " at line " + e.lineNumber() + ", col " + e.columnNumber() + " of " + e.sourceName() + ".", e);
		} catch (final RhinoException e) {
			throw new ChoobException(e.details() + " Line " + e.lineNumber() + ", col " + e.columnNumber() + " of " + e.sourceName() + ".", e);

		} finally {
			Context.exit();
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
