/*
 * PluginModule.java
 *
 * Created on June 16, 2005, 2:36 PM
 */

package org.uwcs.choob.modules;

import org.uwcs.choob.plugins.*;
import java.lang.*;
import java.util.*;
/**
 *
 * @author  sadiq
 */
public class PluginModule
{
    Map pluginMap;
    
    public PluginModule(Map pluginMap)
    {
        this.pluginMap = pluginMap;
    }
    
    public void addPlugin(String URL,String pluginName) throws Exception
    {
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
