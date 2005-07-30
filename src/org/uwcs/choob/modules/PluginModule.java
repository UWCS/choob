/*
 * PluginModule.java
 *
 * Created on June 16, 2005, 2:36 PM
 */

package org.uwcs.choob.modules;

import org.uwcs.choob.plugins.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.ChoobPermission;
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
    DbConnectionBroker broker;

    /**
     * Creates a new instance of the PluginModule.
     * @param pluginMap Map containing currently loaded plugins.
     */
    public PluginModule(Map pluginMap, DbConnectionBroker broker) {
        this.pluginMap = pluginMap;
        this.broker = broker;
    }

    /**
     * Adds a plugin to the loaded plugin map but first unloads any plugin already there.
     *
     * This method also calls the create() method on any new plugin.
     * @param URL URL to the source of the plugin.
     * @param pluginName Name for the class of the new plugin.
     * @throws Exception Thrown if there's a syntactical error in the plugin's source.
     */
    public void addPlugin(String URL,String pluginName) throws Exception {
        if( System.getSecurityManager() != null ) System.getSecurityManager().checkPermission(new ChoobPermission("canAddPlugins"));

        Object plugin = pluginMap.get(pluginName);

        if( plugin != null ) {
            BeanshellPluginUtils.callPluginDestroy(plugin);
        }

        String srcContent = "";

        URL srcURL = new URL(URL);

        HttpURLConnection srcURLCon = (HttpURLConnection)srcURL.openConnection();

        srcURLCon.connect();

        BufferedReader srcReader = new BufferedReader(new InputStreamReader( srcURLCon.getInputStream() ));

        while( srcReader.ready() ) {
            srcContent = srcContent + srcReader.readLine() + "\n";
        }

        if ( srcContent.length() == 0)
        	throw new Exception("No data read from " + URL + ".");

        plugin = BeanshellPluginUtils.createBeanshellPlugin(srcContent, pluginName);

        BeanshellPluginUtils.callPluginCreate(plugin);

        addPluginToDb(srcContent, pluginName);

        pluginMap.put(pluginName,plugin);
    }

    public void loadDbPlugins() throws Exception
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

