/*
 * ModuleGroup.java
 *
 * Created on June 16, 2005, 7:26 PM
 */

package uk.co.uwcs.choob.modules;

import java.util.List;

import uk.co.uwcs.choob.Choob;
import uk.co.uwcs.choob.support.*;

/**
 * Wrapper for the group of Modules in the bot.
 * An instance of this class gets passed to the plugins with each call, and is avaliable at most locations in the core, the idea being to provide a consistent set of relevant library functions.
 * @author sadiq
 */
public final class Modules
{

	/**
	 * An instance of the PluginModule, used for loading and otherwise interacting with other plugins.
	 */
	public PluginModule plugin;

	/**
	 * An instance of the HistoryModule, used for saving and retrieving logs.
	 */
	public HistoryModule history;

	/**
	 * An instance of the UtilModule, a set of utility functions to remove some repetitive tasks from plugins' code.
	 */
	public UtilModule util;

	/**
	 * An instance of the NickModule, an implementation of nicklinking. This should not be used for anything involving security.
	 */
	public NickModule nick;

	/**
	 * An instance of the IntervalModule, used for creating interval (callback) events.
	 */
	public IntervalModule interval;

	/**
	 * An instance of the SyntheticModule, used for generating synthetic events.
	 */
	public SyntheticModule synthetic;

	/**
	 * An instance of the SecurityModule, used for managing security related tasks.
	 */
	public SecurityModule security;

	/**
	 * An instance of the ProtectedChannels, used for limiting what the bot can do in certain channels.
	 */
	public ProtectedChannels pc;

	/**
	 * An instance of the ObjectDbModule, an interface to the generic ObjectDatabase, for the persistance of objects.
	 */
	public ObjectDbModule odb;

	/**
	 * An instance of the ScraperModule, a set of tools to simplify screen-scraping.
	 */
	public ScraperModule scrape;

	/**
	 * An instance of the DateModule, a set of tools for manipulating dates.
	 */
	public DateModule date;

	/**
	 * Creates a new instance of the Modules.
	 */
	public Modules(DbConnectionBroker dbBroker, List<Interval> intervalList,
			Choob bot, IRCInterface irc) {
		try {
			date = new DateModule();
			history = new HistoryModule(dbBroker);
			interval = new IntervalModule( intervalList, this );
			nick = new NickModule();
			odb = new ObjectDbModule( dbBroker, this );
			pc = new ProtectedChannels();
			plugin = new PluginModule(dbBroker, this, irc, bot);
			scrape = new ScraperModule();
			security = new SecurityModule( dbBroker, this );
			synthetic = new SyntheticModule( bot );
			util = new UtilModule(irc);
		}
		catch (ChoobException e)
		{
			throw new RuntimeException("Could not instantiate modules: " + e);
		}
	}
}
