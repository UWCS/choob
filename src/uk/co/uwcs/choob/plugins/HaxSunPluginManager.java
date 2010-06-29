/*
 * Haxy plugin loader that compiles with the Sun javac API and stuff.
 * @author bucko
 */

package uk.co.uwcs.choob.plugins;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;

import uk.co.uwcs.choob.ChoobPluginManager;
import uk.co.uwcs.choob.ChoobTask;
import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobError;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.ChoobInvocationError;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.ChoobNoSuchPluginException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Event;
import uk.co.uwcs.choob.support.events.Message;

public final class HaxSunPluginManager extends ChoobPluginManager
{
	private final Modules mods;
	private final IRCInterface irc;

	private final JavaCompiler compiler;

	private final static String prefix = "pluginData";
	private final ChoobPluginMap allPlugins;

	public HaxSunPluginManager(final Modules mods, final IRCInterface irc)
			throws ChoobException {
		this.mods = mods;
		this.irc = irc;
		this.allPlugins = new ChoobPluginMap();
		this.compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new ChoobException("Compiler class was not found");
		}
	}

	private String compile(final String[] fileNames, final String classPath) throws ChoobException
	{
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final PrintWriter output = new PrintWriter(baos);

		final StandardJavaFileManager fileManager = compiler
				.getStandardFileManager(null, null, null);

		final File outputLocation = new File(classPath);
		final List<File> outputLocationList = new ArrayList<File>();
		outputLocationList.add(outputLocation);

		try {
			fileManager.setLocation(StandardLocation.CLASS_OUTPUT, outputLocationList);
		} catch (final IOException e) {
			throw new ChoobException(e);
		}
		final Iterable<? extends JavaFileObject> compilationUnit = fileManager
			.getJavaFileObjectsFromStrings(Arrays.asList(fileNames));
		final boolean success;

		try {
			success = AccessController
					.doPrivileged(new PrivilegedExceptionAction<Boolean>() {
						@Override public Boolean run() {
							return compiler.getTask(output, fileManager, null,
									null, null, compilationUnit).call();
						}
					});
		} catch (final PrivilegedActionException e) {
			throw new ChoobException(e);
		}

		try {
			fileManager.close();
		} catch (final IOException e) {
			throw new ChoobException(e);
		}

		final String baosts = baos.toString();

		if (success)
			return baosts; // success
		System.out.println(baosts);

		String excep="Compile failed.";

		try
		{
			final String url=(String)mods.plugin.callAPI("Http", "StoreString", baosts);
			excep="Compile failed, see: " + url + " for details.";
		}
		catch (final Exception e) {}
		throw new ChoobException(excep);
	}

	/** Entire contents in a String */
	private static String consume(final InputStream in) throws IOException
	{
		final int block = 1024 * 10;
		final StringBuilder data = new StringBuilder(block);
		final BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		try
		{
			char[] buf = new char[block];
			int numRead = 0;
			while ((numRead = reader.read(buf)) != -1)
				data.append(buf, 0, numRead);
		}
		finally
		{
			reader.close();
		}
		return data.toString();
	}

	/** Return the Java 5 CU for the string. */
	public static CompilationUnit asAST(final String c)
	{
		final ASTParser parser = ASTParser.newParser(AST.JLS3);
		parser.setSource(c.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}

	private String[] makeJavaFiles(final String pluginName, final String outDir, final InputStream in) throws IOException
	{
		final Document doc;
		CompilationUnit cu;

		// create an initial doc / cu pair from the inputstream
		{
			final String cus = consume(in);
			cu = asAST(cus);
			cu.recordModifications();
			doc = new Document(cus);
		}

		// Check all the top-level types are public:
		for (final TypeDeclaration type : types(cu))
			if (Modifier.PUBLIC != (type.getModifiers() & Modifier.PUBLIC))
			{
				// it's not public, the only other valid top-level modifier is default,
				// i.e. no modifier, so we can just add the public modifier.
				final Modifier publicModifier = type.getAST().newModifier(ModifierKeyword.PUBLIC_KEYWORD);
				modifiers(type).add(publicModifier);
			}

		// Rewrite the doc and the cu with the changes
		try {
			cu.rewrite(doc, null).apply(doc);
			cu = asAST(doc.get());
		} catch (BadLocationException e) {
			throw new RuntimeException(e);
		}

		// get the imports, and the line number on which they finish
		final String imps;
		final int importsEndLineNumber;
		{
			final List<ImportDeclaration> importList = imports(cu);
			if (importList.isEmpty())
			{
				imps = "";
				importsEndLineNumber = 0;
			}
			else
			{
				final ImportDeclaration lastImport = last(importList);
				final int startPosition = 0;
				final int endPosition = endPosition(lastImport);
				imps = get(doc, startPosition, endPosition - startPosition);
				importsEndLineNumber = cu.getLineNumber(endPosition); // could be <0.
			}
		}

		// generate the files
		final String packageLine = "package plugins." + pluginName + ";";
		final Set<String> fileNames = new HashSet<String>();

		for (final TypeDeclaration type : types(cu))
		{

			final int typeStart = cu.getExtendedStartPosition(type);
			final int typeLen = cu.getExtendedLength(type);
			final int startLineNumber = cu.getLineNumber(typeStart);
			final String className = type.getName().getIdentifier();
			final String fileName = outDir + className + ".java";
			fileNames.add(fileName);

			final PrintStream classOut = new PrintStream(new FileOutputStream(fileName));
			try
			{
				classOut.print(packageLine);
				classOut.print(imps);
				addSkippedLines(classOut, importsEndLineNumber, startLineNumber);

				classOut.print(get(doc, typeStart, typeLen));
			}
			finally
			{
				classOut.close();
			}
		}

		return fileNames.toArray(new String[fileNames.size()]);
	}

	private void addSkippedLines(final PrintStream classOut, final int importsEndLineNumber, final int startLineNumber) {
		if (startLineNumber > importsEndLineNumber)
			for(int i=0; i < startLineNumber - importsEndLineNumber; i++)
				classOut.print("\n");
	}

	private String get(final Document doc, final int startPosition, final int length) {
		try {
			return doc.get(startPosition, length);
		} catch (BadLocationException e) {
			throw new RuntimeException(e);
		}
	}

	private int endPosition(final ASTNode node) {
		return node.getStartPosition() + node.getLength();
	}

	private static <T> T last(final List<T> list) {
		return list.get(list.size() - 1);
	}

	@SuppressWarnings("unchecked")
	private List<IExtendedModifier> modifiers(final TypeDeclaration type) {
		return type.modifiers();
	}

	@SuppressWarnings("unchecked")
	private Iterable<TypeDeclaration> types(final CompilationUnit cu)
	{
		return cu.types();
	}

	@SuppressWarnings("unchecked")
	private List<ImportDeclaration> imports(final CompilationUnit cu)
	{
		return cu.imports();
	}


	@Override
	protected Object createPlugin(final String pluginName, final URL source) throws ChoobException
	{
		final String classPath = prefix + File.separator + pluginName + File.separator;
		if (source != null)
		{
			final File javaDir = new File(classPath);
			final String javaFileName = classPath + pluginName + ".java";
			final File javaFile = new File(javaFileName);
			URLConnection sourceConn;
			try
			{
				sourceConn = source.openConnection();
			}
			catch (final IOException e)
			{
				throw new ChoobException("Problem opening connection: " + e);
			}
			if (sourceConn.getLastModified() > javaFile.lastModified() || sourceConn.getLastModified() == 0)
			{
				// needs
				InputStream in = null;
				boolean success = false;
				try
				{
					javaDir.mkdirs();
					in = sourceConn.getInputStream();
					final String[] names = makeJavaFiles(pluginName, classPath, in);
					compile(names, classPath);
					// This should help to aleviate timezone differences.
					javaFile.setLastModified( sourceConn.getLastModified() );
					success = true;
				}
				catch (final IOException e)
				{
					throw new ChoobException("Failed to set up for compiler: " + e);
				}
				finally
				{
					try {
						if (in != null)
							in.close();
					} catch (final IOException e) {}

					if (!success)
						javaFile.delete();
				}
			}
		}

		final HaxSunPluginClassLoader loader = new HaxSunPluginClassLoader(pluginName, classPath, getProtectionDomain(pluginName));
		try
		{
			final String className = "plugins." + pluginName + "." + pluginName;
			final Class<?> clazz;
			clazz = loader.findClass(className);
//			clazz = Class.forName(className);
			return instantiatePlugin(clazz, pluginName);
		}
		catch (final ClassNotFoundException e)
		{
			throw new ChoobException("Could not find plugin class for " + pluginName + ": " + e, e);
		}
	}

	protected Object instantiatePlugin(final Class<?> newClass, final String pluginName) throws ChoobException
	{
		Object pluginObj=null;
		// Squiggly brackets are for the weak.

		final Constructor<?> c[] = newClass.getConstructors();

		for (final Constructor<?> element : c)
			try {
				final Class<?>[] t = element.getParameterTypes();
				final Object[] arg = new Object[t.length];

				for (int j=0; j<t.length; j++)
					if (t[j] == IRCInterface.class)
						arg[j]=irc;
					else
						if (t[j] == Modules.class)
							arg[j]=mods;
						else
							throw new ChoobException("Unknown parameter in constructor.");
				pluginObj = element.newInstance(arg);
				break;
			}
			catch (final IllegalAccessException e)
			{
				throw new ChoobException("Plugin " + pluginName + " had no constructor (this error shouldn't occour, something serious is wrong): " + e);
			}
			catch (final InvocationTargetException e)
			{
				throw new ChoobException("Plugin " + pluginName + "'s constructor threw an exception: " + e.getCause(), e.getCause());
			}
			catch (final InstantiationException e)
			{
				throw new ChoobException("Plugin " + pluginName + "'s constructor threw an exception: " + e.getCause(), e.getCause());
			}

		try
		{
			// XXX Isn't this a bit pointless? newInstance() calls the
			// (redefinable) constructor anyway...
			final Method meth = newClass.getMethod("create");
			meth.invoke(pluginObj);

		}
		catch (final NoSuchMethodException e)
		{
			// This is nonfatal and in fact to be expected!
		}
		catch (final IllegalAccessException e)
		{
			// So is this.
		}
		catch (final InvocationTargetException e)
		{
			// This isn't.
			throw new ChoobException("Plugin " + pluginName + "'s create() threw an exception: " + e.getCause(), e.getCause());
		}

		try
		{
			mods.security.addGroup("plugin." + pluginName.toLowerCase());
		}
		catch (final ChoobException e)
		{
			// TODO: Make a groupExists() or something so we don't need to squelch this
		}

		String[] newCommands = new String[0];
		String[] oldCommands = new String[0];
		synchronized(allPlugins)
		{
			List<String> coms = allPlugins.getCommands(pluginName);
			if (coms != null)
				oldCommands = coms.toArray(oldCommands);

			allPlugins.resetPlugin(pluginName, pluginObj);

			coms = allPlugins.getCommands(pluginName);
			if (coms != null)
				newCommands = coms.toArray(newCommands);
		}

		for (final String oldCommand : oldCommands)
			removeCommand(oldCommand);
		for (final String newCommand : newCommands)
			addCommand(newCommand);

		// Grant permissions to the plugin
		RequiresPermission perm = newClass.getAnnotation(RequiresPermission.class);
		if(perm != null) {
			grantPermission(perm, newClass);
		}
		RequiresPermissions perms = newClass.getAnnotation(RequiresPermissions.class);
		if(perms != null) {
			for (RequiresPermission permission : perms.value()) {
				grantPermission(permission, newClass);
			}
		}

		return pluginObj;
	}

	private void grantPermission(final RequiresPermission perm, final Class<?> newClass) {
		final String name = perm.permission();
		final String action = perm.action();
		final String group = "plugin." + newClass.getSimpleName();
		final Class<? extends Permission> pClass = perm.value();
		System.out.println(name+"+"+action);
		try {
			if("".equals(name)) {
				// empty constructor

			} else if ("".equals(action)) {
				// has a permission, but no action
				Constructor<? extends Permission> cons = pClass.getConstructor(String.class);
				mods.security.grantPermission(group, cons.newInstance(name));
			} else {
				// Has everything - ie the 2 Strings that choob accepts
				Constructor<? extends Permission> cons = pClass.getConstructor(String.class,String.class);
				mods.security.grantPermission(group, cons.newInstance(name,action));
			}
		// EAT THAT PAEDOS
		} catch (ChoobException e) {
			if(!e.getMessage().contains("already has permission")) {
				throw new RuntimeException(e);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void destroyPlugin(final String pluginName)
	{
		String[] oldCommands = new String[0];
		synchronized(allPlugins)
		{
			final List<String> coms = allPlugins.getCommands(pluginName);
			if (coms != null)
				oldCommands = coms.toArray(oldCommands);
		}

		// Cleanup is actually pretty easy.
		synchronized(allPlugins)
		{
			allPlugins.resetPlugin(pluginName, null);
		}

		for (final String oldCommand : oldCommands)
			removeCommand(oldCommand);
	}

	/**
	 * Go through the horror of method resolution. Fields are regarded as a
	 * method that takes no parameters.
	 *
	 * @param methods The list of things to search.
	 * @param args The arguments of the method we hope to resolve.
	 */
	private Member javaHorrorMethodResolve(final List<Member> methods, final Object[] args)
	{
		// Do any of them have the right signature?
		final List<Member> filtered = new LinkedList<Member>();
		final Iterator<Member> it = methods.iterator();
		while(it.hasNext())
		{
			final Member thing = it.next();
			if (thing instanceof Field)
			{
				if (args.length == 0)
					filtered.add(thing);
			}
			else
			{
				// Is a method
				final Method method = (Method) thing;
				final Class<?>[] types = method.getParameterTypes();
				final int paramlength = types.length;
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
				if (okness)
					filtered.add(method);
			}
		}

		// Right, have a bunch of applicable methods.
		// TODO: Technically should pick most specific.

		if (filtered.size() == 0)
			return null;

		return filtered.get(0);
	}

	private ChoobTask callCommand(final Method meth, final Object param)
	{
		final String pluginName = meth.getDeclaringClass().getSimpleName();
		final Object plugin = allPlugins.getPluginObj(pluginName);
		final Object[] params;
		if (meth.getParameterTypes().length == 1)
			params = new Object[] { param };
		else
			params = new Object[] { param, mods, irc };

		return new ChoobTask(pluginName) {
			@Override
			public void run() {
				try
				{
					if (isSimpleCommand(meth))
					{
						// intentional throw of ClassCastException.
						Message m = (Message) param;
						String res = (String)meth.invoke(plugin, mods.util.getParamString(m));
						irc.sendContextReply(m, res);
					}
					else
						meth.invoke(plugin, params);
				}
				catch (final InvocationTargetException e)
				{
					// Let the user know what happened.
					if (param instanceof Message)
					{
						final Throwable cause = e.getCause();

						mods.plugin.exceptionReply((Message)param, cause, pluginName);
					}
					System.err.println("Exception invoking method " + meth);
					e.getCause().printStackTrace();
				}
				catch (final IllegalAccessException e)
				{
					System.err.println("Could not access method " + meth);
					e.printStackTrace();
				}
			}
		};
	}

	@Override
	public ChoobTask commandTask(final String pluginName, final String command, final Message ev)
	{
		final Method meth = allPlugins.getCommand(pluginName + "." + command);
		if(meth != null)
			return callCommand(meth, ev);
		return null;
	}

	@Override
	public List<ChoobTask> eventTasks(final Event ev)
	{
		final List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		final List<Method> meths = allPlugins.getEvent(ev.getMethodName());
		if(meths != null)
			for(final Method meth: meths)
			{
				tasks.add(callCommand(meth, ev));
			}
		return tasks;
	}

	@Override
	public List<ChoobTask> filterTasks(final Message ev)
	{
		final List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		final List<Method> meths = allPlugins.getFilter(ev.getMessage());
		if(meths != null)
			for(final Method meth: meths)
			{
				tasks.add(callCommand(meth, ev));
			}
		return tasks;
	}

	@Override
	public ChoobTask intervalTask(final String pluginName, final Object param)
	{
		final Method meth = allPlugins.getInterval(pluginName);
		if(meth != null)
			return callCommand(meth, param);
		return null;
	}

	@Override
	public Object doGeneric(final String pluginName, final String prefix_, final String genName, final Object... params) throws ChoobNoSuchCallException
	{
		final Object plugin = allPlugins.getPluginObj(pluginName);
		final String fullName = pluginName + "." + prefix_ + ":" + genName;
		final String sig = getAPISignature(fullName, params);
		if (plugin == null)
			throw new ChoobNoSuchPluginException(pluginName, sig);

		Member meth = allPlugins.getGeneric(sig);
		if (meth == null)
		{
			// OK, not cached. But maybe it's still there...
			final List<Member> meths = allPlugins.getAllGeneric(fullName);

			if (meths != null)
				meth = javaHorrorMethodResolve(meths, params);

			if (meth != null)
				allPlugins.setGeneric(sig, meth);
			else
				throw new ChoobNoSuchCallException(pluginName, sig);
		}
		final Member meth2 = meth;
		try
		{
			return AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
				@Override public Object run() throws InvocationTargetException, IllegalAccessException {
					if (meth2 instanceof Method)
						return ((Method)meth2).invoke(plugin, params);
					return ((Field)meth2).get(plugin);
				}
			}, mods.security.getPluginContext() );
		}
		catch (final PrivilegedActionException pe)
		{
			final Throwable e = pe.getCause();
			if (e instanceof InvocationTargetException)
			{
				if (e.getCause() instanceof ChoobError)
					// Doesn't need wrapping...
					throw (ChoobError)e.getCause();
				throw new ChoobInvocationError(pluginName, fullName, e.getCause());
			}
			else if (e instanceof IllegalAccessException)
				throw new ChoobError("Could not access method " + fullName + ": " + e);
			else
				throw new ChoobError("Unknown error accessing method " + fullName + ": " + e);
		}
	}

	@Override
	public Object doAPI(final String pluginName, final String APIName, final Object... params) throws ChoobNoSuchCallException
	{
		return doGeneric(pluginName, "api", APIName, params);
	}

	public List<String> getSimpleCommands()
	{
		return allPlugins.getAllSimpleCommands();
	}

	// Helper methods for the class below.
	static boolean checkCommandSignature(final Method meth)
	{
		final Class<?>[] params = meth.getParameterTypes();
		if (params.length == 1)
		{
			if (isSimpleCommand(meth, params))
				return true;

			if (!Message.class.isAssignableFrom(params[0]))
				return false;
		}
		else if (params.length == 3)
		{
			if (!Message.class.isAssignableFrom(params[0]))
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

	static boolean isSimpleCommand(final Method meth)
	{
		final Class<?>[] parameterTypes = meth.getParameterTypes();
		return parameterTypes.length == 1 && isSimpleCommand(meth, parameterTypes);
	}

	private static boolean isSimpleCommand(final Method meth, final Class<?>[] params)
	{
		return String.class.isAssignableFrom(params[0]) &&
				String.class.isAssignableFrom(meth.getReturnType());
	}

	static boolean checkEventSignature(final Method meth)
	{
		final Class<?>[] params = meth.getParameterTypes();
		if (params.length == 1)
		{
			// XXX could be better
			if (!Event.class.isAssignableFrom(params[0]))
				return false;
		}
		else if (params.length == 3)
		{
			if (!Event.class.isAssignableFrom(params[0]))
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

	static boolean checkIntervalSignature(final Method meth)
	{
		final Class<?>[] params = meth.getParameterTypes();
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

	static boolean checkAPISignature(final Method meth)
	{
		return true;
	}

	static String getAPISignature(final String APIName, final Object... args)
	{
		final StringBuffer buf = new StringBuffer(APIName + "(");
		for(int i=0; i<args.length; i++)
		{
			if (args[i] != null)
				buf.append(args[i].getClass().getSimpleName());
			else
				buf.append("null");

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
	// Name -> Plugin object
	private final Map<String,Object> plugins;

	// These map a plugin name to the list of things in the later lists.
	private final Map<String,List<String>> pluginCommands;
//	private final Map<String,List<String>> pluginApiCallSigs;
//	private final Map<String,List<String>> pluginApiCalls;
	private final Map<String,List<String>> pluginGenCallSigs;
	private final Map<String,List<String>> pluginGenCalls;
	private final Map<String,List<Pattern>> pluginFilters;

	// This gives a method to search for inside the events list.
	private final Map<String,List<Method>> pluginEvents;

	// Only one interval per plugin.
	private final Map<String,Method> pluginInterval;

	private final Map<String,Method> commands; // plugin.commandname -> method
//	private final Map<String,Method> apiCallSigs;
//	private final Map<String,List<Method>> apiCalls;
	private final Map<String,Member> genCallSigs; // plugin.prefix:genericname(params) -> method
	private final Map<String,List<Member>> genCalls; // plugin.prefix:genericname -> list of possible methods
	private final Map<Pattern,List<Method>> filters; // pattern object -> method to call on match
	private final Map<String,List<Method>> events; // event name -> method list

	// Create an empty plugin map.
	ChoobPluginMap()
	{
		plugins = new HashMap<String,Object>();
		pluginCommands = new HashMap<String,List<String>>();
		//pluginApiCalls = new HashMap<String,List<String>>();
		//pluginApiCallSigs = new HashMap<String,List<String>>();
		pluginGenCalls = new HashMap<String,List<String>>();
		pluginGenCallSigs = new HashMap<String,List<String>>();
		pluginFilters = new HashMap<String,List<Pattern>>();
		pluginEvents = new HashMap<String,List<Method>>();
		pluginInterval = new HashMap<String,Method>();
		commands = new HashMap<String,Method>();
		//apiCallSigs = new HashMap<String,Method>();
		//apiCalls = new HashMap<String,List<Method>>();
		genCallSigs = new HashMap<String,Member>();
		genCalls = new HashMap<String,List<Member>>();
		filters = new HashMap<Pattern,List<Method>>();
		events = new HashMap<String,List<Method>>();
	}

	// Wipe out details for plugin <name>, and if pluginObj is not null, add new ones.
	synchronized void resetPlugin(final String pluginName, final Object pluginObj)
	{
		final String lname = pluginName.toLowerCase();
		if (plugins.get(lname) != null)
		{
			// Must clear out old values
			Iterator<String> it;
			it = pluginCommands.get(lname).iterator();
			while (it.hasNext())
				commands.remove(it.next());
			/*it = pluginApiCalls.get(lname).iterator();
			while (it.hasNext())
				apiCalls.remove(it.next());
			it = pluginApiCallSigs.get(lname).iterator();
			while (it.hasNext())
				apiCallSigs.remove(it.next());*/
			it = pluginGenCalls.get(lname).iterator();
			while (it.hasNext())
				genCalls.remove(it.next());
			it = pluginGenCallSigs.get(lname).iterator();
			while (it.hasNext())
				genCallSigs.remove(it.next());
			final Iterator<Pattern> it3 = pluginFilters.get(lname).iterator();
			while (it3.hasNext())
			{
				final Iterator<Method> it2 = filters.get(it3.next()).iterator();
				while(it2.hasNext())
				{
					if (it2.next().getDeclaringClass().getSimpleName().compareToIgnoreCase(pluginName) == 0)
						it2.remove();
				}
			}
			final Iterator<Method> it2 = pluginEvents.get(lname).iterator();
			while (it2.hasNext())
			{
				final Method m = it2.next();
				events.get(m.getName()).remove(m);
			}
		}

		if (pluginObj == null)
		{
			plugins.remove(lname);
			pluginCommands.remove(lname);
			//pluginApiCalls.remove(lname);
			//pluginApiCallSigs.remove(lname);
			pluginGenCalls.remove(lname);
			pluginGenCallSigs.remove(lname);
			pluginEvents.remove(lname);
			pluginInterval.remove(lname);
			return;
		}

		plugins.put(lname, pluginObj);
		// OK, now load in new values...
		final List<String> coms = new LinkedList<String>();
		pluginCommands.put(lname, coms);
		//List<String> apis = new LinkedList<String>();
		//pluginApiCalls.put(lname, apis);
		//List<String> apiss = new LinkedList<String>();
		//pluginApiCallSigs.put(lname, apiss);
		final List<String> gens = new LinkedList<String>();
		pluginGenCalls.put(lname, gens);
		final List<String> genss = new LinkedList<String>();
		pluginGenCallSigs.put(lname, genss);
		final List<Pattern> fils = new LinkedList<Pattern>();
		pluginFilters.put(lname, fils);
		final List<Method> evs = new LinkedList<Method>();
		pluginEvents.put(lname, evs);

		final Class<?> pluginClass = pluginObj.getClass();
		final Method[] meths = pluginClass.getMethods();
		for(final Method meth: meths)
		{
			// We don't want these. :)
			if (meth.getDeclaringClass() != pluginClass)
				continue;

			final String name = meth.getName();
			if (name.startsWith("command"))
			{
				final String actualName = name.substring(7).toLowerCase();
				final String commandName = lname + "." + actualName;
				// Command
				if (HaxSunPluginManager.checkCommandSignature(meth))
				{
					coms.add(commandName);
					commands.put(commandName, meth);

					if (HaxSunPluginManager.isSimpleCommand(meth))
					{
						final String fullName = lname + ".command:" + actualName;
						gens.add(fullName);
						if (genCalls.get(fullName) == null)
							genCalls.put(fullName, new LinkedList<Member>());
						genCalls.get(fullName).add(meth);
					}
				}
				else
				{
					System.err.println("Command " + commandName + " had invalid signature.");
				}
			}
/*			else if (name.startsWith("api"))
			{
				String apiName = lname + "." + name.substring(3).toLowerCase();
				if (HaxSunPluginManager.checkAPISignature(meth))
				{
					apis.add(apiName);
					if (apiCalls.get(apiName) == null)
						apiCalls.put(apiName, new LinkedList<Method>());
					apiCalls.get(apiName).add(meth);
				}
				else
				{
					System.err.println("API call " + apiName + " had invalid signature.");
				}
			} API == generic */
			else if (name.startsWith("filter"))
			{
				String filter;
				try
				{
					filter = (String)pluginClass.getField(name + "Regex").get(pluginObj);
				}
				catch (final NoSuchFieldException e)
				{
					System.err.println("Plugin " + pluginName + " had filter " + name + " with no regex.");
					continue;
				}
				catch (final ClassCastException e)
				{
					System.err.println("Plugin " + pluginName + " had filter " + name + " with no non-String regex.");
					continue;
				}
				catch (final IllegalAccessException e)
				{
					System.err.println("Plugin " + pluginName + " had non-public filter " + name + ".");
					continue;
				}
				Pattern pattern;
				try
				{
					pattern = Pattern.compile(filter, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
				}
				catch (final PatternSyntaxException e)
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
				else
				{
					System.err.println("Filter " + lname + "." + name + " had invalid signature.");
				}
			}
			else if (name.startsWith("on"))
			{
				if (HaxSunPluginManager.checkEventSignature(meth))
				{
					evs.add(meth);
					if (events.get(name) == null)
						events.put(name, new LinkedList<Method>());
					events.get(name).add(meth);
				}
				else
				{
					System.err.println("Event " + lname + "." + name + " had invalid signature.");
				}
			}
			else if (name.startsWith("interval"))
			{
				if (HaxSunPluginManager.checkIntervalSignature(meth))
				{
					pluginInterval.put(lname, meth);
				}
				else
				{
					System.err.println("Interval " + lname + "." + name + " had invalid signature.");
				}
			}
			else
			{
				// File it as a generic.
				final Matcher matcher = Pattern.compile("([a-z]+)([A-Z].+)?").matcher(name);
				if (matcher.matches())
				{
					// Is a real generic.
					final String prefix = matcher.group(1);
					final String gName = name.substring(prefix.length()).toLowerCase();
					final String fullName = lname + "." + prefix + ":" + gName;
					if (HaxSunPluginManager.checkAPISignature(meth))
					{
						gens.add(fullName);
						if (genCalls.get(fullName) == null)
							genCalls.put(fullName, new LinkedList<Member>());
						genCalls.get(fullName).add(meth);
					}
					else
					{
						System.err.println("Generic call " + fullName + " had invalid signature.");
					}
				}
				else
				{
					System.err.println("Ignoring method " + name + ".");
				}
			}
		}
		final Field[] fields = pluginClass.getFields();
		for(final Field field: fields)
		{
			// We don't want these. :)
			if (field.getDeclaringClass() != pluginClass)
				continue;

			final String name = field.getName();
			if (name.startsWith("command"))
			{
			}
/*			else if (name.startsWith("api"))
			{
			} API == generic */
			else if (name.startsWith("filter"))
			{
			}
			else if (name.startsWith("on"))
			{
			}
			else if (name.startsWith("interval"))
			{
			}
			else
			{
				// File it as a generic.
				final Matcher matcher = Pattern.compile("([a-z]+)([A-Z].+)?").matcher(name);
				if (matcher.matches())
				{
					// Is a real generic.
					final String prefix = matcher.group(1);
					final String gName = name.substring(prefix.length()).toLowerCase();
					final String fullName = lname + "." + prefix + ":" + gName;

					gens.add(fullName);
					if (genCalls.get(fullName) == null)
						genCalls.put(fullName, new LinkedList<Member>());
					genCalls.get(fullName).add(field);
				}
				else
				{
					System.err.println("Ignoring field " + name + ".");
				}
			}
		}
	}

	synchronized Object getPluginObj(final String pluginName)
	{
		return plugins.get(pluginName.toLowerCase());
	}

	synchronized Object getPluginObj(final Member meth)
	{
		return getPluginObj(meth.getDeclaringClass().getSimpleName().toLowerCase());
	}

	synchronized Method getCommand(final String commandName)
	{
		return commands.get(commandName.toLowerCase());
	}

	synchronized List<String> getCommands(final String pluginName)
	{
		return pluginCommands.get(pluginName.toLowerCase());
	}

	/*synchronized Method getAPI(String apiName)
	{
		return apiCallSigs.get(apiName.toLowerCase());
	}

	synchronized void setAPI(String apiName, Method meth)
	{
		pluginApiCallSigs.get(meth.getDeclaringClass().getSimpleName().toLowerCase()).add(apiName.toLowerCase());
		apiCallSigs.put(apiName.toLowerCase(), meth);
	}

	synchronized List<Method> getAllAPI(String apiName)
	{
		return apiCalls.get(apiName.toLowerCase());
	}*/

	synchronized Member getGeneric(final String genName)
	{
		return genCallSigs.get(genName.toLowerCase());
	}

	synchronized void setGeneric(final String genName, final Member obj)
	{
		pluginGenCallSigs.get(obj.getDeclaringClass().getSimpleName().toLowerCase()).add(genName.toLowerCase());
		genCallSigs.put(genName.toLowerCase(), obj);
	}

	synchronized List<Member> getAllGeneric(final String genName)
	{
		return genCalls.get(genName.toLowerCase());
	}

	synchronized List<Method> getFilter(final String text)
	{
		final List<Method> ret = new LinkedList<Method>();
		final Iterator<Pattern> pats = filters.keySet().iterator();
		while(pats.hasNext())
		{
			final Pattern pat = pats.next();
			if (pat.matcher(text).find())
				ret.addAll(filters.get(pat));
		}
		return ret;
	}

	synchronized List<Method> getEvent(final String eventName)
	{
		final List<Method> handlers = events.get(eventName);
		if (handlers != null)
			return new ArrayList<Method>(handlers);
		return null;
	}

	synchronized Method getInterval(final String pluginName)
	{
		return pluginInterval.get(pluginName.toLowerCase());
	}

	synchronized List<String> getAllSimpleCommands()
	{
		List<String> ret = new ArrayList<String>();
		for (Entry<String, Method> cmdref : commands.entrySet())
			if (HaxSunPluginManager.isSimpleCommand(cmdref.getValue()))
				ret.add(cmdref.getKey());
		return ret;
	}
}
