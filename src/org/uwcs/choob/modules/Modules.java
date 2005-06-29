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
 *
 * @author  sadiq
 */
public class Modules
{
    
    /**
     * Holds value of property pluginModule.
     */
    DbConnectionBroker dbBroker;
    PluginModule pluginModule;
    LoggerModule loggerModule;
    Map pluginMap;
    
    /** Creates a new instance of ModuleGroup */
    public Modules( DbConnectionBroker dbBroker, Map pluginMap )
    {
        pluginModule = new PluginModule(pluginMap);
        loggerModule = new LoggerModule(dbBroker);
        this.dbBroker = dbBroker;
        this.pluginMap = pluginMap;
    }
    
    /**
     * Getter for property pluginModule.
     * @return Value of property pluginModule.
     */
    public PluginModule getPluginModule() throws Exception
    {
        return this.pluginModule;
    }
    
    public LoggerModule getLoggerModule() throws Exception
    {
        return loggerModule;
    }
}
