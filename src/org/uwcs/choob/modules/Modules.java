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
	public HistoryModule history;
	public UtilModule util;
	public NickModule nick;
	public IntervalModule interval;
	public SyntheticModule synthetic;
	public SecurityModule security;
	public ProtectedChannels pc;
	public ObjectDbModule odb;
	Map pluginMap;
	List intervalList;
	List filterList;
	private Choob bot;

	/**
	 * Creates a new instance of ModuleGroup
	 * @param dbBroker
	 * @param pluginMap
	 */
	public Modules( DbConnectionBroker dbBroker, Map pluginMap, List filterList, List intervalList, Choob bot, IRCInterface irc )
	{
		try
		{
			plugin = new PluginModule(pluginMap, dbBroker, filterList, this, irc);
			history = new HistoryModule(dbBroker);
			util = new UtilModule( bot );
			nick = new NickModule(dbBroker);
			interval = new IntervalModule( intervalList );
			synthetic = new SyntheticModule( bot );
			security = new SecurityModule( dbBroker );
			pc = new ProtectedChannels();
			odb = new ObjectDbModule( dbBroker );
		}
		catch (ChoobException e)
		{
			throw new RuntimeException("Could not instantiate modules: " + e);
		}
		
		this.dbBroker = dbBroker;
		this.pluginMap = pluginMap;
		this.intervalList = intervalList;
		this.filterList = filterList;
		this.bot = bot;
	}
}
