/*
 * PluginModule.java
 *
 * Created on June 16, 2005, 2:36 PM
 */

package org.uwcs.choob.modules;

import org.uwcs.choob.plugins.*;
import org.uwcs.choob.support.ChoobPermission;
import java.lang.*;
import java.util.*;
/**
 * Module that performs functions relating to the plugin architecture of the bot.
 * @author sadiq
 */
public class PluginModule
{
    Map pluginMap;
    
    /**
     * Creates a new instance of the PluginModule.
     * @param pluginMap Map containing currently loaded plugins.
     */    
    public PluginModule(Map pluginMap)
    {
        this.pluginMap = pluginMap;
    }
    
    /**
     * Adds a plugin to the loaded plugin map but first unloads any plugin already there.
     *
     * This method also calls the create() method on any new plugin.
     * @param URL URL to the source of the plugin.
     * @param pluginName Name for the class of the new plugin.
     * @throws Exception Thrown if there's a syntactical error in the plugin's source.
     */    
    public void addPlugin(String URL,String pluginName) throws Exception
    {
        if( System.getSecurityManager() != null ) System.getSecurityManager().checkPermission(new ChoobPermission("canAddPlugins"));
        
        Object plugin = pluginMap.get(pluginName);
        
        if( plugin != null )
        {
            BeanshellPluginUtils.callPluginDestroy(plugin);
        }
        
        plugin = BeanshellPluginUtils.createBeanshellPlugin(URL, pluginName);
        
        BeanshellPluginUtils.callPluginCreate(plugin);
        
        pluginMap.put(pluginName,plugin);
    }
}
