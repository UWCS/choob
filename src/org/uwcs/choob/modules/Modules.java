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
    public DbConnectionBroker dbBroker;
    public PluginModule plugin;
    public LoggerModule logger;
    public UtilModule util;
    public NickModule nick;
    public IntervalModule interval;
    Map pluginMap;
    List intervalList;
    List filterList;
    private Choob bot;

    /**
     * Creates a new instance of ModuleGroup
     * @param dbBroker
     * @param pluginMap
     */
    public Modules( DbConnectionBroker dbBroker, Map pluginMap, List filterList, List intervalList, Choob bot )
    {
        plugin = new PluginModule(pluginMap, dbBroker, filterList, this);
        logger = new LoggerModule(dbBroker);
        util = new UtilModule( bot );
        nick = new NickModule(dbBroker);
        interval = new IntervalModule( intervalList );
        this.dbBroker = dbBroker;
        this.pluginMap = pluginMap;
        this.intervalList = intervalList;
        this.filterList = filterList;
        this.bot = bot;
    }
}
