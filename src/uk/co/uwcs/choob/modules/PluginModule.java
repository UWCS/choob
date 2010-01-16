/*
 * PluginModule.java
 *
 * Created on June 16, 2005, 2:36 PM
 */

package uk.co.uwcs.choob.modules;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import uk.co.uwcs.choob.Choob;
import uk.co.uwcs.choob.ChoobDistributingPluginManager;
import uk.co.uwcs.choob.ChoobPluginManager;
import uk.co.uwcs.choob.ChoobTask;
import uk.co.uwcs.choob.ChoobThread;
import uk.co.uwcs.choob.ChoobThreadManager;
import uk.co.uwcs.choob.plugins.AnnotatedJavaPluginManager;
import uk.co.uwcs.choob.plugins.HaxSunPluginManager;
import uk.co.uwcs.choob.plugins.JavaScriptPluginManager;
import uk.co.uwcs.choob.support.CallAPIResult;
import uk.co.uwcs.choob.support.ChoobAuthError;
import uk.co.uwcs.choob.support.ChoobBadSyntaxError;
import uk.co.uwcs.choob.support.ChoobError;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.ChoobInternalError;
import uk.co.uwcs.choob.support.ChoobInvocationError;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.ChoobNoSuchPluginException;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.DbConnectionBroker;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.NoSuchCommandException;
import uk.co.uwcs.choob.support.NoSuchPluginException;
import uk.co.uwcs.choob.support.events.Message;

/**
 * Module that performs functions relating to the plugin architecture of the bot.
 * @author sadiq
 */
public final class PluginModule
{
	private final DbConnectionBroker broker;
	private final Modules mods;
	private final ChoobPluginManager hsPlugMan;
	private final ChoobPluginManager annotatedPlugMan;
	private final ChoobPluginManager dPlugMan;
	private final ChoobPluginManager jsPlugMan;
	private final Choob bot;
	private final IRCInterface irc;

	/**
	 * Creates a new instance of the PluginModule.
	 * @param pluginMap Map containing currently loaded plugins.
	 */
	PluginModule(final DbConnectionBroker broker, final Modules mods,
			final IRCInterface irc, final Choob bot) throws ChoobException {
		this.broker = broker;
		this.mods = mods;
		this.hsPlugMan = new HaxSunPluginManager(mods, irc);
		this.annotatedPlugMan = new AnnotatedJavaPluginManager(mods, irc);
		this.dPlugMan = new ChoobDistributingPluginManager();
		this.jsPlugMan = new JavaScriptPluginManager(mods, irc);
		this.bot = bot;
		this.irc = irc;
	}

	public ChoobPluginManager getPlugMan()
	{
		// XXX Need better permission name.
		AccessController.checkPermission(new ChoobPermission("getPluginManager"));

		return dPlugMan;
	}

	/**
	 * Adds a plugin to the loaded plugin map but first unloads any plugin already there.
	 *
	 * This method also calls the create() method on any new plugin.
	 * @param URL URL to the source of the plugin.
	 * @param pluginName Name for the class of the new plugin.
	 * @throws ChoobException Thrown if there's a syntactical error in the plugin's source.
	 */
	public void addPlugin(final String pluginName, final String URL) throws ChoobException {
		final URL srcURL;
		try
		{
			srcURL = new URL(URL);
		}
		catch (final MalformedURLException e)
		{
			throw new ChoobException("URL " + URL + " is malformed: " + e);
		}

		// Small hack to allow the ChoobTask to return a value.
		final boolean[] existed = new boolean[] { false };
		final ChoobException[] err = new ChoobException[] { null };

		final ChoobTask task = new ChoobTask(null, "addPlugin-" + pluginName) {
			@Override
			public void run() {
				try {
					String file = srcURL.getFile();
					if (file.endsWith(".js"))
						existed[0] = jsPlugMan.loadPlugin(pluginName, srcURL);
					else if(file.contains("Annotated"))
						existed[0] = annotatedPlugMan.loadPlugin(pluginName, srcURL);
					else
						existed[0] = hsPlugMan.loadPlugin(pluginName, srcURL);
				} catch (final ChoobException e) {
					err[0] = e;
				}
			}
		};
		final ChoobThread thread = new ChoobThread(task, "choob-addPlugin-" + pluginName);
		thread.pushPlugin(pluginName);

		thread.start();
		try {
			thread.join();
		} catch (final InterruptedException e) {}

		if (err[0] != null)
			throw err[0];

		// Inform plugins, if they want to know.
		if (existed[0])
			bot.onPluginReLoaded(pluginName);
		else
			bot.onPluginLoaded(pluginName);

		addPluginToDb(pluginName, URL);
	}

	/**
	 * Reloads a plugin which has been loaded previously, but may not be loaded currently.
	 *
	 * This method simply looks up the last source URL for the plugin, and calls addPlugin with it.
	 * @param pluginName Name of the plugin to reload.
	 * @throws ChoobException Thrown if there's a syntactical error in the plugin's source.
	 */
	public void reloadPlugin(String pluginName) throws ChoobException {
		final String URL = getPluginURL(pluginName);
		pluginName = getPluginName(pluginName); // Fix the case of the param
		if (URL == null)
			throw new ChoobNoSuchPluginException(pluginName);
		addPlugin(pluginName, URL);
	}

	/**
	 * Calmly stops a loaded plugin from queuing any further tasks. Existing tasks will run until they finish.
	 * @param pluginName Name of the plugin to detach.
	 * @throws ChoobNoSuchPluginException Thrown if the plugin doesn't exist.
	 */
	public void detachPlugin(String pluginName) throws ChoobNoSuchPluginException {
		pluginName = getPluginName(pluginName); // Fix the case of the param
		dPlugMan.unloadPlugin(pluginName);
		bot.onPluginUnLoaded(pluginName);
	}

	/**
	 * Calmly stops a loaded plugin from queuing any further tasks. Existing tasks will run until they finish.
	 * @param pluginName Name of the plugin to reload.
	 * @throws ChoobNoSuchPluginException Thrown if the plugin doesn't exist.
	 */
	public void setCorePlugin(final String pluginName, final boolean isCore) throws ChoobNoSuchPluginException {
		AccessController.checkPermission(new ChoobPermission("plugin.core"));
		setCoreStatus(pluginName, isCore);
	}

	/**
	 * Call the API subroutine of name name on plugin pluginName and return the result.
	 * @param pluginName The name of the plugin to call.
	 * @param APIString The name of the routine to call.
	 * @param params Parameters to pass to the routine.
	 * @throws ChoobNoSuchCallException If the call could not be resolved.
	 * @throws ChoobInvocationError If the call threw an exception.
	 */
	public Object callAPI(final String pluginName, final String APIString, final Object... params) throws ChoobNoSuchCallException
	{
		AccessController.doPrivileged(new PrivilegedAction<Object>() {
			public Object run() {
				ChoobThread.pushPluginStatic(pluginName);
				return null;
			}
		});
		try
		{
			return dPlugMan.doAPI(pluginName, APIString, params);
		}
		finally
		{
			AccessController.doPrivileged(new PrivilegedAction<Object>() {
				public Object run() {
					ChoobThread.popPluginStatic();
					return null;
				}
			});
		}
	}

	/**
	 * Call an API subroutine of a specific name on all plugins that support it.
	 * @param APIString The name of the routine to call.
	 * @param params Parameters to pass to the routine.
	 */

	public List<CallAPIResult> broadcastCallAPI(final String APIString, final Object... params)
	{
		final List<CallAPIResult> rvList = new ArrayList<CallAPIResult>();

		final String[] plugins = dPlugMan.plugins();
		for (final String plugin : plugins)
		{
			CallAPIResult rv;
			try
			{
				rv = new CallAPIResult(plugin, callAPI(plugin, APIString, params), null);
			}
			catch (final ChoobNoSuchCallException e)
			{
				// Plugin doesn't support this API call, so ignore error.
				continue;
			}
			catch (final Exception e)
			{
				rv = new CallAPIResult(plugin, null, e);
			}
			rvList.add(rv);
		}

		return rvList;
	}
	
	public String[] getHelp(String pluginName, String commandName) throws NoSuchCommandException
	{
		return hsPlugMan.getHelp(pluginName, commandName);
	}

	public String[] getInfo(String pluginName) throws NoSuchPluginException
	{
		return hsPlugMan.getInfo(pluginName);
	}
	
	/**
	 * Call the generic subroutine of type type and name name on plugin pluginName and return the result.
	 * @param pluginName The name of the plugin to call.
	 * @param type The type of the routine to call.
	 * @param name The name of the routine to call.
	 * @param params Parameters to pass to the routine.
	 * @throws ChoobNoSuchCallException If the call could not be resolved.
	 * @throws ChoobInvocationError If the call threw an exception.
	 */
	public Object callGeneric(final String pluginName, final String type, final String name, final Object... params) throws ChoobNoSuchCallException
	{
		AccessController.checkPermission(new ChoobPermission("generic." + type));

		AccessController.doPrivileged(new PrivilegedAction<Object>() {
			public Object run() {
				ChoobThread.pushPluginStatic(pluginName);
				return null;
			}
		});
		try
		{
			return dPlugMan.doGeneric(pluginName, type, name, params);
		}
		finally
		{
			AccessController.doPrivileged(new PrivilegedAction<Object>() {
				public Object run() {
					ChoobThread.popPluginStatic();
					return null;
				}
			});
		}
	}
	
	/**
	 * Cause a command of plugin pluginName to be queued for execution.
	 * @param pluginName The name of the plugin to call.
	 * @param command The name of the command to call.
	 * @param mes The message to pass to the routing
	 * @throws ChoobNoSuchCallException If the call could not be resolved.
	 */
	public void queueCommand(final String pluginName, final String command, final Message mes) throws ChoobNoSuchCallException
	{
		AccessController.checkPermission(new ChoobPermission("generic.command"));
		final ChoobTask task = dPlugMan.commandTask(pluginName, command, mes);
		if (task != null)
			ChoobThreadManager.queueTask(task);
		else
			throw new ChoobNoSuchCallException(pluginName, "command " + command);
	}

	public void exceptionReply(final Message mes, final Throwable e, final String pluginName)
	{
		if (pluginName == null)
		{
			if (e instanceof ChoobBadSyntaxError)
			{
				try
				{
					final String[] params = mods.util.getParamArray(mes);
					final String[] guts = (String[])callAPI("Help", "GetSyntax", params[0]);
					irc.sendContextReply(mes, guts);
				}
				catch (final ChoobNoSuchCallException f)
				{
					irc.sendContextReply(mes, "Bad syntax! Unfortunately, I don't have Help loaded to tell you what it should be.");
				}
			}
			else if (e instanceof ChoobException || e instanceof ChoobError)
				irc.sendContextReply(mes, "A plugin went wrong: " + e.getMessage());
			else if (e instanceof AccessControlException)
				irc.sendContextReply(mes, "D'oh! A plugin needs permission " + ChoobAuthError.getPermissionText(((AccessControlException)e).getPermission()) + "!");
			else
				irc.sendContextReply(mes, "The plugin author was too lazy to trap the exception: " + e);
		}
		else
		{
			if (e instanceof ChoobBadSyntaxError)
			{
				try
				{
					final String[] params = mods.util.getParamArray(mes);
					final String[] guts = (String[])callAPI("Help", "GetSyntax", params[0]);
					irc.sendContextReply(mes, guts);
				}
				catch (final ChoobNoSuchCallException f)
				{
					irc.sendContextReply(mes, "Bad syntax! Unfortunately, I don't have Help loaded to tell you what it should be.");
				}
			}
			else if (e instanceof ChoobException || e instanceof ChoobError)
				irc.sendContextReply(mes, "Plugin " + pluginName + " went wrong: " + e.getMessage());
			else if (e instanceof AccessControlException)
				irc.sendContextReply(mes, "D'oh! Plugin " + pluginName + " needs permission " + ChoobAuthError.getPermissionText(((AccessControlException)e).getPermission()) + "!");
			else
				irc.sendContextReply(mes, "The author of plugin " + pluginName + " was too lazy to trap the exception: " + e);
		}
	}

	/**
	 * Get a task to run the interval handler in a plugin.
	 * @param plugin The name of the plugin to run the interval on.
	 * @param param The parameter to pass to the interval handler.
	 * @return A ChoobTask that will run the handler.
	 */
	public ChoobTask doInterval(final String plugin, final Object param)
	{
		AccessController.checkPermission(new ChoobPermission("interval"));
		return dPlugMan.intervalTask(plugin, param);
	}

	/**
	 * Get a list of loaded plugins.
	 * @return Names of all loaded plugins.
	 */
	public String[] getLoadedPlugins()
	{
		return dPlugMan.plugins();
	}

	/**
	 * Get a list of loaded plugins.
	 * @param pluginName plugin name to query
	 * @return Names of all commands in the plugin.
	 */
	public String[] getPluginCommands(final String pluginName) throws ChoobNoSuchPluginException
	{
		final String[] commands = dPlugMan.commands(pluginName);
		if (commands == null)
			throw new ChoobNoSuchPluginException(pluginName);
		return commands;
	}

	/**
	 * Get a list of simplecommands.
	 * @return Names of all commands.
	 */
	public List<String> getSimpleCommands()
	{
		return ((HaxSunPluginManager)hsPlugMan).getSimpleCommands();
	}

	/**
	 * Get a list of known plugins.
	 * @param onlyCore whether to only return known core plugins
	 * @return Names of all loaded plugins.
	 */
	public String[] getAllPlugins(final boolean onlyCore)
	{
		return getPluginList(onlyCore);
	}

	/**
	 * Get the source URL for the plugin, or where it was last loaded from.
	 * @param pluginName The name of the plugin to get the source URL for.
	 * @return URL string for the plugin.
	 */
	public String getPluginSource(final String pluginName) throws ChoobNoSuchPluginException
	{
		return getPluginURL(pluginName);
	}

	/**
	 * Gets the maximum number of threads a plugin is allowed to have.
	 * @param pluginName the name of the plugin to get the limit of.
	 * @return Integer thread limit for the plugin.
	 */
	public int getConcurrencyLimit(final String pluginName)
	{
		// Small hack to allow the ChoobTask to return a value. Default here is
		// 2 threads/plugin.
		final int[] limit = new int[] { 2 };

		final ChoobTask task = new ChoobTask(null, "getConcurrencyLimit") {
			@Override
			public void run() {
				try {
					limit[0] = (Integer)mods.plugin.callAPI("Concurrency", "GetThreadLimit", pluginName);
				} catch (final Exception e) {
					// Don't care about anything. Lalala.
				}
			}
		};
		final ChoobThread thread = new ChoobThread(task, "choob-getConcurrencyLimit");
		thread.pushPlugin(pluginName);

		thread.start();
		try {
			thread.join();
		} catch (final InterruptedException e) {}

		return limit[0];
	}

	private void setCoreStatus(final String pluginName, final boolean isCore) throws ChoobNoSuchPluginException {
		Connection dbCon = null;
		try {
			dbCon = broker.getConnection();
			final PreparedStatement sqlSetCore = dbCon.prepareStatement("UPDATE Plugins SET CorePlugin = ? WHERE PluginName = ?");
			sqlSetCore.setInt(1, isCore ? 1 : 0);
			sqlSetCore.setString(2, pluginName);
			if (sqlSetCore.executeUpdate() == 0)
				throw new ChoobNoSuchPluginException(pluginName);
		} catch (final SQLException e) {
			e.printStackTrace();
			throw new ChoobInternalError("SQL Exception while setting core status on the plugin.");
		} finally {
			if (dbCon != null)
				broker.freeConnection(dbCon);
		}
	}

	private String[] getPluginList(final boolean onlyCore) {
		Connection dbCon = null;
		try {
			dbCon = broker.getConnection();
			PreparedStatement sqlPlugins;
			if (onlyCore)
				sqlPlugins = dbCon.prepareStatement("SELECT PluginName FROM Plugins WHERE CorePlugin = 1");
			else
				sqlPlugins = dbCon.prepareStatement("SELECT PluginName FROM Plugins");

			final ResultSet names = sqlPlugins.executeQuery();

			final String[] plugins = new String[0];
			if (!names.first())
				return plugins;

			final List<String> plugList = new ArrayList<String>();
			do
			{
				plugList.add(names.getString(1));
			}
			while(names.next());
			return plugList.toArray(plugins);
		} catch (final SQLException e) {
			e.printStackTrace();
			throw new ChoobInternalError("SQL Exception while setting core status on the plugin.");
		} finally {
			if (dbCon != null)
				broker.freeConnection(dbCon);
		}
	}

	private String getPluginURL(final String pluginName) throws ChoobNoSuchPluginException {
		Connection dbCon = null;
		try {
			dbCon = broker.getConnection();
			final PreparedStatement sqlGetURL = dbCon.prepareStatement("SELECT URL FROM Plugins WHERE PluginName = ?");
			sqlGetURL.setString(1, pluginName);
			final ResultSet url = sqlGetURL.executeQuery();

			if (!url.first())
				throw new ChoobNoSuchPluginException(pluginName);

			return url.getString("URL");
		} catch (final SQLException e) {
			e.printStackTrace();
			throw new ChoobInternalError("SQL Exception while finding the plugin in the database.");
		} finally {
			if (dbCon != null)
				broker.freeConnection(dbCon);
		}
	}

	private String getPluginName(final String pluginName) throws ChoobNoSuchPluginException {
		Connection dbCon = null;
		try {
			dbCon = broker.getConnection();
			final PreparedStatement sqlGetName = dbCon.prepareStatement("SELECT PluginName FROM Plugins WHERE PluginName = ?");
			sqlGetName.setString(1, pluginName);
			final ResultSet name = sqlGetName.executeQuery();

			if (!name.first())
				throw new ChoobNoSuchPluginException(pluginName);

			return name.getString("PluginName");
		} catch (final SQLException e) {
			e.printStackTrace();
			throw new ChoobInternalError("SQL Exception while finding the plugin in the database.");
		} finally {
			if (dbCon != null)
				broker.freeConnection(dbCon);
		}
	}

	private void addPluginToDb(final String pluginName, final String URL) {
		Connection dbCon = null;
		try {
			dbCon = broker.getConnection();
			final PreparedStatement pluginReplace = dbCon.prepareStatement("INSERT INTO Plugins (PluginName, URL) VALUES (?, ?) ON DUPLICATE KEY UPDATE URL = ?");

			pluginReplace.setString(1, pluginName);
			pluginReplace.setString(2, URL);
			pluginReplace.setString(3, URL);

			pluginReplace.executeUpdate();
		} catch (final SQLException e) {
			e.printStackTrace();
			throw new ChoobInternalError("SQL Exception while adding the plugin to the database...");
		} finally {
			broker.freeConnection(dbCon);
		}
	}

	public boolean commandExists(final String pluginName, final String commandName)
	{
		String commands[];
		try
		{
			commands=getPluginCommands(pluginName);
		}
		catch (final ChoobNoSuchPluginException e)
		{
			return false;
		}

		for (final String command : commands)
			if (command.equalsIgnoreCase(commandName))
				return true;
		return false;
	}


	public boolean validCommand(final String command)
	{
		if (validInternalCommand(command))
			return true;
		if (validAliasCommand(command))
			return true;
		return false;
	}

	public boolean validAliasCommand(final String command)
	{
		final String[] bits=command.split("\\.");
		try
		{
			if (bits.length == 1 && mods.plugin.callAPI("Alias", "Get", bits[0].trim())!=null)
				return true;
		}
		catch (final ChoobNoSuchCallException e)
		{}
		return false;
	}

	public boolean validInternalCommand(final String command)
	{
		final String[] bits=command.split("\\.");
		if (bits.length == 2 && mods.plugin.commandExists(bits[0], bits[1]))
			return true;
		return false;
	}
}

