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
	private ChoobPluginManager plugMan;
	private ChoobPluginManager dPlugMan;
	private ChoobPluginManager jsPlugMan;
	private Choob bot;

	/**
	 * Creates a new instance of the PluginModule.
	 * @param pluginMap Map containing currently loaded plugins.
	 */
	PluginModule(Map pluginMap, DbConnectionBroker broker, Modules mods, IRCInterface irc, Choob bot) throws ChoobException {
		this.pluginMap = pluginMap;
		this.broker = broker;
		this.mods = mods;
		this.plugMan = new HaxSunPluginManager(mods, irc);
		this.dPlugMan = new ChoobDistributingPluginManager();
		this.jsPlugMan = new JavaScriptPluginManager(mods, irc);
		this.bot=bot;
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
	 * @throws Exception Thrown if there's a syntactical error in the plugin's source.
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
			existed = plugMan.loadPlugin(pluginName, srcURL);

		// Inform plugins, if they want to know.
		if (existed)
			bot.onPluginReLoaded(pluginName);
		else
			bot.onPluginLoaded(pluginName);

		addPluginToDb(pluginName);
	}

	/**
	 * Call the API subroutine of name name on plugin pluginName and return the result.
	 * @param pluginName The name of the plugin to call.
	 * @param APIString The name of the routine to call.
	 * @param params Parameters to pass to the routine.
	 * @throws ChoobNoSuchCallException If the call could not be resolved.
	 * @throws ChoobInvocationError If the call threw an exception.
	 */
	public Object callAPI(String pluginName, String APIString, Object... params) throws ChoobNoSuchCallException
	{
		boolean done = false;
		try
		{
			ChoobThread.pushPluginStatic(pluginName);
			done = true;
			return dPlugMan.doAPI(pluginName, APIString, params);
		}
		finally
		{
			if (done)
				ChoobThread.popPluginStatic();
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
	public Object callGeneric(String pluginName, String type, String name, Object... params) throws ChoobNoSuchCallException
	{
		AccessController.checkPermission(new ChoobPermission("generic." + type));
		boolean done = false;
		try
		{
			ChoobThread.pushPluginStatic(pluginName);
			done = true;
			return dPlugMan.doGeneric(pluginName, type, name, params);
		}
		finally
		{
			if (done)
				ChoobThread.popPluginStatic();
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

	public String exceptionReply(Throwable e, String pluginName)
	{
		if (pluginName == null)
		{
			if (e instanceof ChoobException || e instanceof ChoobError)
				return "A plugin went wrong: " + e.getMessage();
			else if (e instanceof AccessControlException)
				return "D'oh! A plugin needs permission " + ChoobAuthError.getPermissionText(((AccessControlException)e).getPermission()) + "!";
			else
				return "The plugin author was too lazy to trap the exception: " + e;
		}
		else
		{
			if (e instanceof ChoobException || e instanceof ChoobError)
				return "Plugin " + pluginName + " went wrong: " + e.getMessage();
			else if (e instanceof AccessControlException)
				return "D'oh! Plugin " + pluginName + " needs permission " + ChoobAuthError.getPermissionText(((AccessControlException)e).getPermission()) + "!";
			else
				return "The author of plugin " + pluginName + " was too lazy to trap the exception: " + e;
		}
	}

	public ChoobTask doInterval(String plugin, Object param)
	{
		AccessController.checkPermission(new ChoobPermission("interval"));
		return dPlugMan.intervalTask(plugin, param);
	}

	public String[] plugins()
	{
		return plugMan.plugins();
	}

	public String[] commands(String pluginName)
	{
		return plugMan.commands(pluginName);
	}

	public void loadDbPlugins( Modules modules ) throws Exception
	{
		AccessController.checkPermission(new ChoobPermission("canLoadSavedPlugins"));

		Connection dbCon = broker.getConnection();

		PreparedStatement getSavedPlugins = dbCon.prepareStatement("SELECT * FROM LoadedPlugins");

		ResultSet savedPlugins = getSavedPlugins.executeQuery();

		savedPlugins.first();

		do
		{
			addPlugin( savedPlugins.getString("PluginName"), null );
		}
		while( savedPlugins.next() );
	}

	private void addPluginToDb(String pluginName) throws ChoobException
	{
		Connection dbCon=null;
		try
		{
			dbCon= broker.getConnection();
			PreparedStatement pluginReplace = dbCon.prepareStatement("REPLACE INTO LoadedPlugins VALUES(?,?)");

			pluginReplace.setString(1,pluginName);
			pluginReplace.setString(2,"SunHaxPluginManager");

			pluginReplace.executeUpdate();
		}
		catch (SQLException e)
		{
			System.err.println("SQL Exception: " + e);
			throw new ChoobException("SQL Exception while adding the plugin to the database...");
		}
		finally
		{
			broker.freeConnection(dbCon);
		}
	}
}

