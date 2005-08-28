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

/**
 * Module that performs functions relating to the plugin architecture of the bot.
 * @author sadiq
 */
public class PluginModule {
	Map pluginMap;
	List filterList;
	DbConnectionBroker broker;
	Modules mods;

	/**
	 * Creates a new instance of the PluginModule.
	 * @param pluginMap Map containing currently loaded plugins.
	 */
	public PluginModule(Map pluginMap, DbConnectionBroker broker, List filterList, Modules mods) {
		this.pluginMap = pluginMap;
		this.broker = broker;
		this.filterList = filterList;
		this.mods = mods;
	}

	/**
	 * Adds a plugin to the loaded plugin map but first unloads any plugin already there.
	 *
	 * This method also calls the create() method on any new plugin.
	 * @param URL URL to the source of the plugin.
	 * @param pluginName Name for the class of the new plugin.
	 * @throws Exception Thrown if there's a syntactical error in the plugin's source.
	 */
	public void addPlugin(String URL,String pluginName ) throws Exception {
		ChoobSecurityManager sec = null;

		{
			SecurityManager s = System.getSecurityManager();
			if( s != null && s instanceof ChoobSecurityManager ) {
				sec = (ChoobSecurityManager)s;
				sec.checkPermission(new ChoobPermission("canAddPlugins"));
			}
		}

		Object plugin = pluginMap.get(pluginName);

		if( plugin != null ) {
			BeanshellPluginUtils.callPluginDestroy(plugin, mods);
		}

		String srcContent = "";

		URL srcURL = new URL(URL);

		URLConnection srcURLCon = (URLConnection)srcURL.openConnection();

		srcURLCon.connect();

		BufferedReader srcReader = new BufferedReader(new InputStreamReader( srcURLCon.getInputStream() ));

		while( srcReader.ready() ) {
			srcContent = srcContent + srcReader.readLine() + "\n";
		}

		if ( srcContent.length() == 0)
			throw new Exception("No data read from " + URL + ".");

		plugin = BeanshellPluginUtils.createBeanshellPlugin(srcContent, pluginName);

		BeanshellPluginUtils.callPluginCreate(plugin, mods);

		addPluginToDb(srcContent, pluginName);

		pluginMap.put(pluginName,plugin);

		if ( sec != null )
			sec.invalidatePluginPermissions(pluginName);

		reloadFilters();
	}

	/**
	 * Cause the plugin permissions to be reloaded from the database.
	 */
	public void reloadPluginPermissions(String pluginName)
	{
		ChoobSecurityManager sec = null;

		SecurityManager s = System.getSecurityManager();
		if( s != null && s instanceof ChoobSecurityManager ) {
			sec = (ChoobSecurityManager)s;
			sec.invalidatePluginPermissions(pluginName);
		}
	}

	public Object callAPI(String APIString, Object... params)
	{
		// TODO - Permissions here? Or should they be in the plugin API call?
		String APIName, pluginName;

		int dotIndex = APIString.indexOf(".");
		if (dotIndex > -1) {
			// plugin.hook
			pluginName = APIString.substring(0, dotIndex);
			APIName = APIString.substring(dotIndex + 1);
		} else {
			// expecting an alias
			// TODO
			throw new RuntimeException("Ooops, unimplemented API alias called");
		}
		Object plugin = pluginMap.get(pluginName);
		if (plugin == null)
			throw new RuntimeException("No plugin named " + pluginName + "exists.");

		return BeanshellPluginUtils.doAPI(plugin, APIName, params);
	}

	private void reloadFilters()
	{
		List newFilters = Collections.synchronizedList(new ArrayList());
		Set pluginSet = pluginMap.keySet();

		Iterator tempIt = pluginSet.iterator();

		while( tempIt.hasNext() )
		{
			newFilters.addAll(BeanshellPluginUtils.getFilters( pluginMap.get( tempIt.next() ) ));
		}

		synchronized( filterList )
		{
			filterList.clear();
			filterList.addAll( newFilters );
		}

		System.out.println("Filters list now contains: " + filterList.size() + " filters");
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
			addPlugin( savedPlugins.getString("Source"), savedPlugins.getString("PluginName") );
		}
		while( savedPlugins.next() );
	}

	private void addPluginToDb(String source, String pluginName) throws SQLException
	{
		Connection dbCon = broker.getConnection();

		PreparedStatement pluginReplace = dbCon.prepareStatement("REPLACE INTO LoadedPlugins VALUES(?,?)");

		pluginReplace.setString(1,pluginName);
		pluginReplace.setString(2,source);

		pluginReplace.executeUpdate();

		broker.freeConnection( dbCon );
	}
}

