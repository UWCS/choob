/*
 * Choob.java
 *
 * Created on June 1, 2005, 2:22 AM
 */

/**
 *
 * @author	sadiq
 */

package org.uwcs.choob;

import org.jibble.pircbot.*;
import bsh.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.reflect.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.plugins.*;
import org.uwcs.choob.support.events.*;
import org.uwcs.choob.modules.*;
import java.sql.*;

/**
 * Core class of the Choob bot, main interaction with IRC.
 */
public class Choob extends PircBot
{
	DbConnectionBroker broker;
	Map pluginMap;
	Modules modules;
	IRCInterface irc;
	String trigger;
	List <Interval> intervalList;
	ChoobWatcherThread watcher;

	private static final int INITTHREADS = 5;
	private static final int MAXTHREADS = 20;

	private ConfigReader conf;

	/**
	 * Constructor for Choob, initialises vital variables.
	 */
	public Choob()
	{

		try
		{
			conf=new ConfigReader("bot.conf");
		}
		catch (IOException e)
		{
			e.printStackTrace();
			System.out.println("\n\nError reading config file, exiting.");
			System.exit(2);
			return;
		}

		trigger = conf.getSettingFallback("botTrigger","~");

		// Create a shiny synchronised (americans--) list
		intervalList = new ArrayList();

		// Create a new database connection broker using the MySQL drivers
		try
		{
			broker = new DbConnectionBroker("com.mysql.jdbc.Driver", "jdbc:mysql://" + conf.getSettingFallback("dbServer","localhost") + "/choob?autoReconnect=true&autoReconnectForPools=true&initialTimeout=1", conf.getSettingFallback("dbUser","choob"), conf.getSettingFallback("dbPass",""), 10, 20, "/tmp/db.log", 1, true, 60, 3) ;
		}
		catch (IOException e)
		{
			e.printStackTrace();
			System.out.println("Unexpected error in DbConnectionBroker setup, exiting.");
			System.exit(5);
			return;
		}

		// Create a new IRC interface
		irc = new IRCInterface( this );

		// Initialise our modules.
		modules = new Modules(broker, pluginMap, intervalList, this, irc );

		irc.setMods(modules);

		// ---

		// Set the name from the config file.
		this.setName(conf.getSettingFallback("botName", "Choob"));
		modules.util.updateTrigger();

		// Set the bot's hostname.
		this.setLogin("Choob");

		// Disable PircBot's flood protection, reducing percieved lag.
		this.setMessageDelay(0);

		// Set Version (the comment).
		this.setVersion("Choob SVN - http://svn.uwcs.co.uk/repos/choob/");


		// Set up the threading stuff.
		init();

		// Enable debugging output.
		setVerbose(true);

		try
		{
			doConnect();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			System.out.println("Connection Error, exiting: " + e);
			System.exit(2);
		}
		catch (IrcException e)
		{
			e.printStackTrace();
			System.out.println("Unhandled IRC Error on connect, exiting: ." + e);
			System.exit(3);
		}
	}

	//* Connect the initialised bot to IRC, and do hard-coded post-connection stuff. */
	void doConnect() throws IOException, IrcException
	{
		// Connect to the IRC server.
		connect(conf.getSettingFallback("server","irc.uwcs.co.uk"));

		// Set mode +B (is a bot)
		sendRawLineViaQueue("MODE " + getName() + " +B");

		String[] commands = conf.getSettingFallback("connectstring","").split("\\|\\|\\|");
		for (int i=0; i<commands.length; i++)
			sendRawLineViaQueue(commands[i]);

		// Join the channels.
		String[] channels = conf.getSettingFallback("channels","").split("[ ,]");
		for (int i=0; i<channels.length; i++)
			joinChannel(channels[i]);
	}

	public IRCInterface getIRC()
	{
		return irc;
	}

	/**
	 * Initialises the Choob thread poll as well as loading the few core plugins that ought to be present at start.
	 */
	public void init()
	{
		// Create our list of threads
		int c;

		watcher = new ChoobWatcherThread(intervalList, irc, pluginMap, modules);

		watcher.start();

		// TODO - make this a proper class
		java.security.Policy.setPolicy( new java.security.Policy()
		{
			boolean f = false;
			// I think this is all that's ever really needed...
			public synchronized boolean implies(java.security.ProtectionDomain d, java.security.Permission p)
			{
				if (!f)
				{
					f = true;
//					System.err.println("Checking pd " + d + " and perm " + p);
					f = false;
				}
				if ( !(d instanceof ChoobProtectionDomain) )
					return true;
				else
					return false;
			}
			public synchronized java.security.PermissionCollection getPermissions(java.security.ProtectionDomain d)
			{
				if (!f)
				{
					f = true;
//					System.err.println("Checking pd " + d);
					f = false;
				}
				java.security.PermissionCollection p = new java.security.Permissions();
				if ( !(d instanceof ChoobProtectionDomain) )
					p.add( new java.security.AllPermission() );
				return p;
			}
			public synchronized java.security.PermissionCollection getPermissions(java.security.CodeSource s)
			{
				if (!f)
				{
					f = true;
//					System.err.println("Checking cs " + s);
					f = false;
				}
				java.security.PermissionCollection p = new java.security.Permissions();
				//if ( !(d instanceof ChoobCodeSource) )
				//	p.add( new java.security.AllPermission() );
				return p;
			}
			public void refresh() {}
		});

		// Now we've finished most of the stuff we need high access priviledges
		// to do, we can set up our security manager that checks all priviledged
		// accesses from a Beanshell plugin with their permissions in the MySQL
		// table.

		// Note to self: Install security AFTER making sure we have permissions
		// to grant ourselves permissions. :( -- bucko
		if ( System.getSecurityManager() == null )
			System.setSecurityManager(new SecurityManager());

		// This is needed to properly initialise a ChoobProtectionDomain.
		ChoobPluginManager.initialise( modules, irc );

		// Initialise the thread manager, too
		ChoobThreadManager.initialise( );
		ChoobDecoderTask.initialise( broker, modules, irc );

		try
		{
			// We need to have an initial set of plugins that ought to be loaded as core.

			Connection dbConnection = broker.getConnection();
			PreparedStatement coreplugSmt = dbConnection.prepareStatement("SELECT * FROM CorePlugins;");
			ResultSet coreplugResults = coreplugSmt.executeQuery();
			if ( coreplugResults.first() )
				do
				{
					System.out.println("Plugin loading: " + coreplugResults.getString("PluginName"));
					modules.plugin.addPlugin(coreplugResults.getString("PluginName"), coreplugResults.getString("URL"));
				}
				while ( coreplugResults.next() );

			coreplugSmt.close();

			broker.freeConnection(dbConnection);
		}
		catch( Exception e )
		{
			e.printStackTrace();
			// If we failed to load the core plugins, we've got issues.
			throw new RuntimeException("Failed to load core plugin list!", e);
		}
	}

	/**
	 * Return a regex that can (and should) be used to match lines for a
	 * prefix indicating that the line is a command.
	 */
	public String getTriggerRegex()
	{
		return trigger + "|" + this.getName() + ",\\s+|bot,\\s+";
	}

	/**
	 * Return a string which can (and should) be prepended onto a line to
	 * make it into a command.
	 */
	public String getTrigger()
	{
		return trigger;
	}

	public void onSyntheticMessage(IRCEvent mes) {
		spinThread( mes );
	}

	protected void onDisconnect()
	{
		System.out.println ("Connection lost!");
		for (;;)
		{
			System.out.println ("Waiting...");
			try
			{
				Thread.sleep(30000);
			} catch (InterruptedException e) { }

			System.out.println ("Reconnecting...");

			try
			{
				doConnect();
				return;
			}
			catch (IOException e)
			{
				e.printStackTrace();
				System.out.println("Connection Error, exiting: " + e);
			}
			catch (IrcException e)
			{
				e.printStackTrace();
				System.out.println("Unhandled IRC Error on connect, exitin: ." + e);
			}
		}
	}

	/*
	 * DO NOT EDIT THE CODE IN THE PASTE BLOCK MANUALLY
	 *
	 * This code is automatically replaced by HorriblePerlScript when it is
	 * run. Make your modifications there.
	 */

	// BEGIN PASTE!

	protected void onNotice(String nick, String login, String hostname, String target, String message) {
		if (target.indexOf('#') == 0)
			spinThread(new ChannelNotice("onNotice", System.currentTimeMillis(), ((int)(Math.random()*127)), message, nick, login, hostname, target, target));
		else
			spinThread(new PrivateNotice("onPrivateNotice", System.currentTimeMillis(), ((int)(Math.random()*127)), message, nick, login, hostname, target));
	}

	protected void onMessage(String target, String nick, String login, String hostname, String message) {
		spinThread(new ChannelMessage("onMessage", System.currentTimeMillis(), ((int)(Math.random()*127)), message, nick, login, hostname, target, target));
	}

	protected void onPrivateMessage(String nick, String login, String hostname, String message) {
		spinThread(new PrivateMessage("onPrivateMessage", System.currentTimeMillis(), ((int)(Math.random()*127)), message, nick, login, hostname, null));
	}

	protected void onAction(String nick, String login, String hostname, String target, String message) {
		if (target.indexOf('#') == 0)
			spinThread(new ChannelAction("onAction", System.currentTimeMillis(), ((int)(Math.random()*127)), message, nick, login, hostname, target, target));
		else
			spinThread(new PrivateAction("onPrivateAction", System.currentTimeMillis(), ((int)(Math.random()*127)), message, nick, login, hostname, target));
	}

	protected void onChannelInfo(String channel, int userCount, String topic) {
		spinThread(new ChannelInfo("onChannelInfo", System.currentTimeMillis(), ((int)(Math.random()*127)), channel));
	}

	protected void onDeVoice(String channel, String nick, String login, String hostname, String target) {
		spinThread(new ChannelUserMode("onDeVoice", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "v", true, target));
	}

	protected void onDeop(String channel, String nick, String login, String hostname, String target) {
		spinThread(new ChannelUserMode("onDeop", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "o", true, target));
	}

	protected void onInvite(String target, String nick, String login, String hostname, String channel) {
		spinThread(new ChannelInvite("onInvite", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, nick, login, hostname, target));
	}

	protected void onJoin(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelJoin("onJoin", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, nick, login, hostname));
	}

	protected void onKick(String channel, String nick, String login, String hostname, String target, String message) {
		spinThread(new ChannelKick("onKick", System.currentTimeMillis(), ((int)(Math.random()*127)), message, channel, nick, login, hostname, target));
	}

	protected void onMode(String channel, String nick, String login, String hostname, String modes) {
		spinThread(new ChannelModes("onMode", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, modes));
	}

	protected void onNickChange(String nick, String login, String hostname, String newNick) {
		spinThread(new NickChange("onNickChange", System.currentTimeMillis(), ((int)(Math.random()*127)), nick, login, hostname, newNick));
	}

	protected void onOp(String channel, String nick, String login, String hostname, String target) {
		spinThread(new ChannelUserMode("onOp", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "o", true, target));
	}

	protected void onPart(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelPart("onPart", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, nick, login, hostname));
	}

	protected void onQuit(String nick, String login, String hostname, String message) {
		spinThread(new QuitEvent("onQuit", System.currentTimeMillis(), ((int)(Math.random()*127)), message, nick, login, hostname));
	}

	protected void onRemoveChannelBan(String channel, String nick, String login, String hostname, String param) {
		spinThread(new ChannelParamMode("onRemoveChannelBan", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "b", false, param));
	}

	protected void onRemoveChannelKey(String channel, String nick, String login, String hostname, String param) {
		spinThread(new ChannelParamMode("onRemoveChannelKey", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "k", false, param));
	}

	protected void onRemoveChannelLimit(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onRemoveChannelLimit", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "l", false));
	}

	protected void onRemoveInviteOnly(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onRemoveInviteOnly", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "i", false));
	}

	protected void onRemoveModerated(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onRemoveModerated", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "m", false));
	}

	protected void onRemoveNoExternalMessages(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onRemoveNoExternalMessages", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "n", false));
	}

	protected void onRemovePrivate(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onRemovePrivate", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "p", false));
	}

	protected void onRemoveSecret(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onRemoveSecret", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "s", false));
	}

	protected void onRemoveTopicProtection(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onRemoveTopicProtection", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "t", false));
	}

	protected void onSetChannelBan(String channel, String nick, String login, String hostname, String param) {
		spinThread(new ChannelParamMode("onSetChannelBan", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "b", true, param));
	}

	protected void onSetChannelKey(String channel, String nick, String login, String hostname, String param) {
		spinThread(new ChannelParamMode("onSetChannelKey", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "k", true, param));
	}

	protected void onSetInviteOnly(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onSetInviteOnly", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "i", true));
	}

	protected void onSetModerated(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onSetModerated", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "m", true));
	}

	protected void onSetNoExternalMessages(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onSetNoExternalMessages", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "n", true));
	}

	protected void onSetPrivate(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onSetPrivate", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "p", true));
	}

	protected void onSetSecret(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onSetSecret", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "s", true));
	}

	protected void onSetTopicProtection(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onSetTopicProtection", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "t", true));
	}

	protected void onTopic(String channel, String message, String nick, long date, boolean changed) {
		spinThread(new ChannelTopic("onTopic", System.currentTimeMillis(), ((int)(Math.random()*127)), message, channel));
	}

	protected void onUnknown(String line) {
		spinThread(new UnknownEvent("onUnknown", System.currentTimeMillis(), ((int)(Math.random()*127))));
	}

	protected void onUserMode(String targetNick, String nick, String login, String hostname, String modes) {
		spinThread(new UserModes("onUserMode", System.currentTimeMillis(), ((int)(Math.random()*127)), modes));
	}

	protected void onVoice(String channel, String nick, String login, String hostname, String target) {
		spinThread(new ChannelUserMode("onVoice", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "v", true, target));
	}

	// END PASTE!

	private synchronized void spinThread(IRCEvent ev)
	{
		if (ev instanceof UserEvent && ((UserEvent)ev).getNick().toLowerCase().indexOf("bot")!=-1)
			return;

		ChoobTask task = new ChoobDecoderTask(ev);

		ChoobThreadManager.queueTask(task);
	}
}
