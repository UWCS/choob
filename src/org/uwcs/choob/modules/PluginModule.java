/*
 * PluginModule.java
 *
 * Created on June 16, 2005, 2:36 PM
 */

package org.uwcs.choob.modules;

import org.uwcs.choob.plugins.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.*;
import java.lang.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.sql.*;
import java.security.AccessController;

/**
 * Module that performs functions relating to the plugin architecture of the bot.
 * @author sadiq
 */
public class PluginModule {
	Map pluginMap;
	List filterList;
	DbConnectionBroker broker;
	Modules mods;
	ChoobPluginManager plugMan;
	ChoobPluginManager dPlugMan;
	Choob bot;

	/**
	 * Creates a new instance of the PluginModule.
	 * @param pluginMap Map containing currently loaded plugins.
	 */
	public PluginModule(Map pluginMap, DbConnectionBroker broker, List filterList, Modules mods, IRCInterface irc) throws ChoobException {
		this.pluginMap = pluginMap;
		this.broker = broker;
		this.filterList = filterList;
		this.mods = mods;
		this.plugMan = new HaxSunPluginManager(mods, irc);
		this.dPlugMan = new ChoobDistributingPluginManager();
	}

	public ChoobPluginManager getPlugMan()
	{
		SecurityManager sm = System.getSecurityManager();
		if (sm != null)
			sm.checkPermission(new ChoobPermission("getPluginManager"));

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

		plugMan.loadPlugin(pluginName, srcURL);

		addPluginToDb(pluginName);
	}

	public Object callAPI(String pluginName, String APIString, Object... params) throws ChoobException
	{
		return plugMan.doAPI(pluginName, APIString, params);
	}

	public ChoobTask doInterval(String plugin, Object param)
	{
		return plugMan.intervalTask(plugin, param);
	}

	public void loadDbPlugins( Modules modules ) throws Exception
	{
		if( System.getSecurityManager() != null ) System.getSecurityManager().checkPermission(new ChoobPermission("canLoadSavedPlugins"));

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
		Connection dbCon = broker.getConnection();
		try {
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

