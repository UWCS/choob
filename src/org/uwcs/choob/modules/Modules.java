/*
 * ModuleGroup.java
 *
 * Created on June 16, 2005, 7:26 PM
 */

package org.uwcs.choob.modules;

import org.uwcs.choob.support.*;
import org.uwcs.choob.plugins.*;
import org.uwcs.choob.*;
import java.util.*;

/**
 * Wrapper for the group of Modules in the bot.
 * @author sadiq
 */
public class Modules
{
    
    /**
     * Holds value of property pluginModule.
     */
    DbConnectionBroker dbBroker;
    PluginModule pluginModule;
    LoggerModule loggerModule;
    UtilModule utilModule;
    Map pluginMap;
    
    /**
     * Creates a new instance of ModuleGroup
     * @param dbBroker
     * @param pluginMap
     */
    public Modules( DbConnectionBroker dbBroker, Map pluginMap )
    {
        pluginModule = new PluginModule(pluginMap);
        loggerModule = new LoggerModule(dbBroker);
        utilModule = new UtilModule();
        this.dbBroker = dbBroker;
        this.pluginMap = pluginMap;
    }
    
    /**
     * Getter for UtilModule
     *
     */
    public UtilModule getUtilModule()
    {
        return this.utilModule;
    }
    
    /**
     * Getter for PluginModule.
     * @return Value of property pluginModule.
     * @throws Exception
     */
    public PluginModule getPluginModule()
    {
        return this.pluginModule;
    }
    
    /**
     * Getter for the LoggerModule.
     * @throws Exception
     * @return
     */    
    public LoggerModule getLoggerModule()
    {
        return loggerModule;
    }
}
