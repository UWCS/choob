/*
 * PluginModule.java
 *
 * Created on June 16, 2005, 2:36 PM
 */

package uk.co.uwcs.choob.modules;

import uk.co.uwcs.choob.plugins.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import uk.co.uwcs.choob.*;
import java.lang.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.sql.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.AccessControlException;

/**
 * Module that performs functions relating to the plugin architecture of the bot.
 * @author sadiq
 */
public final class PluginModule
{
	private Map pluginMap;
	private DbConnectionBroker broker;
	private Modules mods;
	private ChoobPluginManager hsPlugMan;
	private ChoobPluginManager dPlugMan;
	private ChoobPluginManager jsPlugMan;
	private Choob bot;
	private IRCInterface irc;

	/**
	 * Creates a new instance of the PluginModule.
	 * @param pluginMap Map containing currently loaded plugins.
	 */
	PluginModule(Map pluginMap, DbConnectionBroker broker, Modules mods, IRCInterface irc, Choob bot) throws ChoobException {
		this.pluginMap = pluginMap;
		this.broker = broker;
		this.mods = mods;
		this.hsPlugMan = new HaxSunPluginManager(mods, irc);
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
	public void addPlugin(String pluginName, String URL) throws ChoobException {
		URL srcURL;
		try
		{
			srcURL = new URL(URL);
		}
		catch (MalformedURLException e)
		{
			throw new ChoobException("URL " + URL + " is malformed: " + e);
		}

		boolean existed;
		if (srcURL.getFile().endsWith(".js"))
			existed = jsPlugMan.loadPlugin(pluginName, srcURL);
		else
			existed = hsPlugMan.loadPlugin(pluginName, srcURL);

		// Inform plugins, if they want to know.
		if (existed)
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
		String URL = getPluginURL(pluginName);
		if (URL == null)
			throw new ChoobNoSuchPluginException(pluginName);
		addPlugin(pluginName, URL);
	}

	/**
	 * Calmly stops a loaded plugin from queuing any further tasks. Existing tasks will run until they finish.
	 * @param pluginName Name of the plugin to reload.
	 * @throws ChoobNoSuchPluginException Thrown if the plugin doesn't exist.
	 */
	public void detachPlugin(String pluginName) throws ChoobNoSuchPluginException {
		dPlugMan.unloadPlugin(pluginName);
		bot.onPluginUnLoaded(pluginName);
	}

	/**
	 * Calmly stops a loaded plugin from queuing any further tasks. Existing tasks will run until they finish.
	 * @param pluginName Name of the plugin to reload.
	 * @throws ChoobNoSuchPluginException Thrown if the plugin doesn't exist.
	 */
	public void setCorePlugin(String pluginName, boolean isCore) throws ChoobNoSuchPluginException {
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
	public Object callAPI(final String pluginName, String APIString, Object... params) throws ChoobNoSuchCallException
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
	 * Call the generic subroutine of type type and name name on plugin pluginName and return the result.
	 * @param pluginName The name of the plugin to call.
	 * @param type The type of the routine to call.
	 * @param name The name of the routine to call.
	 * @param params Parameters to pass to the routine.
	 * @throws ChoobNoSuchCallException If the call could not be resolved.
	 * @throws ChoobInvocationError If the call threw an exception.
	 */
	public Object callGeneric(final String pluginName, String type, String name, Object... params) throws ChoobNoSuchCallException
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
	public void queueCommand(String pluginName, String command, Message mes) throws ChoobNoSuchCallException
	{
		AccessController.checkPermission(new ChoobPermission("generic.command"));
		ChoobTask task = dPlugMan.commandTask(pluginName, command, mes);
		if (task != null)
			ChoobThreadManager.queueTask(task);
		else
			throw new ChoobNoSuchCallException(pluginName, "command " + command);
	}

	public void exceptionReply(Message mes, Throwable e, String pluginName)
	{
		if (pluginName == null)
		{
			if (e instanceof ChoobBadSyntaxError)
			{
				try
				{
					String[] params = mods.util.getParamArray(mes);
					String[] guts = (String[])callAPI("Help", "GetSyntax", params[0]);
					irc.sendContextReply(mes, guts);
				}
				catch (ChoobNoSuchCallException f)
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
					String[] params = mods.util.getParamArray(mes);
					String[] guts = (String[])callAPI("Help", "GetSyntax", params[0]);
					irc.sendContextReply(mes, guts);
				}
				catch (ChoobNoSuchCallException f)
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
	public ChoobTask doInterval(String plugin, Object param)
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
	public String[] getPluginCommands(String pluginName) throws ChoobNoSuchPluginException
	{
		String[] commands = dPlugMan.commands(pluginName);
		if (commands == null)
			throw new ChoobNoSuchPluginException(pluginName);
		return commands;
	}

	/**
	 * Get a list of known plugins.
	 * @param onlyCore whether to only return known core plugins
	 * @return Names of all loaded plugins.
	 */
	public String[] getAllPlugins(boolean onlyCore)
	{
		return getPluginList(onlyCore);
	}

	/**
	 * Get the source URL for the plugin, or where it was last loaded from.
	 * @param pluginName The name of the plugin to get the source URL for.
	 * @return URL string for the plugin.
	 */
	public String getPluginSource(String pluginName) throws ChoobNoSuchPluginException
	{
		return getPluginURL(pluginName);
	}

	private void setCoreStatus(String pluginName, boolean isCore) throws ChoobNoSuchPluginException {
		Connection dbCon = null;
		try {
			dbCon = broker.getConnection();
			PreparedStatement sqlSetCore = dbCon.prepareStatement("UPDATE Plugins SET CorePlugin = ? WHERE PluginName = ?");
			sqlSetCore.setInt(1, isCore ? 1 : 0);
			sqlSetCore.setString(2, pluginName);
			if (sqlSetCore.executeUpdate() == 0)
				throw new ChoobNoSuchPluginException(pluginName);
		} catch (SQLException e) {
			e.printStackTrace();
			throw new ChoobInternalError("SQL Exception while setting core status on the plugin.");
		} finally {
			if (dbCon != null)
				broker.freeConnection(dbCon);
		}
	}

	private String[] getPluginList(boolean onlyCore) {
		Connection dbCon = null;
		try {
			dbCon = broker.getConnection();
			PreparedStatement sqlPlugins;
			if (onlyCore)
				sqlPlugins = dbCon.prepareStatement("SELECT PluginName FROM Plugins WHERE CorePlugin = 1");
			else
				sqlPlugins = dbCon.prepareStatement("SELECT PluginName FROM Plugins");

			ResultSet names = sqlPlugins.executeQuery();

			String[] plugins = new String[0];
			if (!names.first())
				return plugins;

			List<String> plugList = new ArrayList<String>();
			do
			{
				plugList.add(names.getString(1));
			}
			while(names.next());
			return plugList.toArray(plugins);
		} catch (SQLException e) {
			e.printStackTrace();
			throw new ChoobInternalError("SQL Exception while setting core status on the plugin.");
		} finally {
			if (dbCon != null)
				broker.freeConnection(dbCon);
		}
	}

	private String getPluginURL(String pluginName) throws ChoobNoSuchPluginException {
		Connection dbCon = null;
		try {
			dbCon = broker.getConnection();
			PreparedStatement sqlGetURL = dbCon.prepareStatement("SELECT URL FROM Plugins WHERE PluginName = ?");
			sqlGetURL.setString(1, pluginName);
			ResultSet url = sqlGetURL.executeQuery();

			if (!url.first())
				throw new ChoobNoSuchPluginException(pluginName);

			return url.getString("URL");
		} catch (SQLException e) {
			e.printStackTrace();
			throw new ChoobInternalError("SQL Exception while finding the plugin in the database.");
		} finally {
			if (dbCon != null)
				broker.freeConnection(dbCon);
		}
	}

	private void addPluginToDb(String pluginName, String URL) {
		Connection dbCon = null;
		try {
			dbCon = broker.getConnection();
			PreparedStatement pluginReplace = dbCon.prepareStatement("INSERT INTO Plugins (PluginName, URL) VALUES (?, ?) ON DUPLICATE KEY UPDATE URL = ?");

			pluginReplace.setString(1, pluginName);
			pluginReplace.setString(2, URL);
			pluginReplace.setString(3, URL);

			pluginReplace.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new ChoobInternalError("SQL Exception while adding the plugin to the database...");
		} finally {
			broker.freeConnection(dbCon);
		}
	}

	public boolean commandExists(String pluginName, String commandName)
	{
		String commands[];
		try
		{
			commands=getPluginCommands(pluginName);
		}
		catch (ChoobNoSuchPluginException e)
		{
			return false;
		}

		for (int i = 0; i < commands.length; i++)
			if (commands[i].equalsIgnoreCase(commandName))
				return true;
		return false;
	}


	public boolean validCommand(String command)
	{
		if (validInternalCommand(command))
			return true;
		if (validAliasCommand(command))
			return true;
		return false;
	}

	public boolean validAliasCommand(String command)
	{
		String[] bits=command.split("\\.");
		try
		{
			if (bits.length == 1 && mods.plugin.callAPI("Alias", "Get", bits[0].trim())!=null)
				return true;
		}
		catch (ChoobNoSuchCallException e)
		{}
		return false;
	}

	public boolean validInternalCommand(String command)
	{
		String[] bits=command.split("\\.");
		if (bits.length == 2 && mods.plugin.commandExists(bits[0], bits[1]))
			return true;
		return false;
	}
}

