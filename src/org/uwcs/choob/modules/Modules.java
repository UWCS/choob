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
    public PluginModule plugin;
    public LoggerModule logger;
    public UtilModule util;
    Map pluginMap;
    
    /**
     * Creates a new instance of ModuleGroup
     * @param dbBroker
     * @param pluginMap
     */
    public Modules( DbConnectionBroker dbBroker, Map pluginMap )
    {
        plugin = new PluginModule(pluginMap, dbBroker);
        logger = new LoggerModule(dbBroker);
        util = new UtilModule();
        this.dbBroker = dbBroker;
        this.pluginMap = pluginMap;
    }
}
