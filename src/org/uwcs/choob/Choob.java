/*
 * Choob.java
 *
 * Created on June 1, 2005, 2:22 AM
 */

/**
 *
 * @author  sadiq
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
	List choobThreads;
	Modules modules;
	IRCInterface irc;
	String trigger;
	List filterList;
	List intervalList;
	ChoobWatcherThread watcher;

	private static final int INITTHREADS = 5;
	private static final int MAXTHREADS = 20;

	/**
	 * Constructor for Choob, initialises vital variables.
	 * @throws IOException Possibly arises from the database connection pool creation.
	 */
	public Choob() throws IOException
	{
		// We wrap the pluginMap with a synchronizedMap in order to prevent
		// concurrent modication of it and a possible race condition.
		pluginMap = Collections.synchronizedMap(new HashMap());

		// Create a our (sychronised dammit) list of filters
		filterList = Collections.synchronizedList(new ArrayList());

		// Create a shiny synchronised (americans--) list
		intervalList = Collections.synchronizedList(new ArrayList());

		// Set the bot's nickname.
		String botName="Choob";
		String dbUser="";
		String dbPass="";
		String dbServer="localhost";

		try
		{
			Properties botProps = new Properties();
			botProps.load(this.getClass().getClassLoader().getResourceAsStream ("bot.conf"));
			botName = botProps.getProperty("botName");
			dbUser = botProps.getProperty("dbUser");
			dbPass = botProps.getProperty("dbPass");
			trigger = botProps.getProperty("botTrigger");
			dbServer = botProps.getProperty("dbServer");
		}
		catch (Exception e)
		{
			System.out.println(e);
			System.out.println("\n\nFatal error reading bot.conf, check the readme file. Exiting.");
			System.exit(2);
		}

		this.setName(botName);

		// Set the bot's hostname.
		this.setLogin("Choob");

		this.setVersion("Choob SVN - http://svn.uwcs.co.uk/repos/choob/");

		// Create a new database connection broker using the MySQL drivers
		broker = new DbConnectionBroker("com.mysql.jdbc.Driver", "jdbc:mysql://" + dbServer + "/choob?autoReconnect=true&autoReconnectForPools=true&initialTimeout=1", dbUser, dbPass, 10, 20, "/tmp/db.log", 1, true, 60, 3) ;

		// Initialise our modules.
		modules = new Modules(broker, pluginMap, filterList, intervalList, this);

		// Create a new IRC interface
		irc = new IRCInterface( this );
	}

	/**
	 * Adds a new ChoobThread
	 */
	private void addChoobThread() {
		ChoobThread tempThread = new ChoobThread(broker,modules,pluginMap,filterList,trigger);
		tempThread.start();
		choobThreads.add(tempThread);
	}

	/**
	 * Initialises the Choob thread poll as well as loading the few core plugins that ought to be present at start.
	 */
	public void init()
	{
		// Create our list of threads
		choobThreads = new ArrayList();

		int c;

		// Step through list of threads, construct them and star them running.
		for( c = 0 ; c < INITTHREADS ; c++ )
		{
			addChoobThread();
		}

		watcher = new ChoobWatcherThread(intervalList, irc, pluginMap, modules);

		watcher.start();

		// TODO - make this a proper class
		java.security.Policy.setPolicy( new java.security.Policy()
		{
			// I think this is all that's ever really needed...
			public boolean implies(java.security.ProtectionDomain d, java.security.Permission p)
			{
				if ( !(d instanceof ChoobProtectionDomain) )
					return true;
				else
					return false;
			}
			public java.security.PermissionCollection getPermissions(java.security.ProtectionDomain d)
			{
				java.security.PermissionCollection p = new java.security.Permissions();
				//if ( !(d instanceof ChoobProtectionDomain) )
				//	p.add( new java.security.AllPermission() );
				return p;
			}
			public java.security.PermissionCollection getPermissions(java.security.CodeSource s)
			{
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
		BeanshellPluginUtils.setMods( modules );

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
					modules.plugin.addPlugin(coreplugResults.getString("URL"), coreplugResults.getString("PluginName"));
				}
				while ( coreplugResults.next() );

			broker.freeConnection(dbConnection);
		}
		catch( Exception e )
		{
			// If we failed to load the core plugins, we've got issues.
			System.out.println(e);
			e.printStackTrace();
		}
	}

	public void onSyntheticMessage(IRCEvent mes) {
		spinThread( mes );
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
			spinThread(new ChannelNotice("onNotice", message, nick, login, hostname, target, target));
		else
			spinThread(new PrivateNotice("onNotice", message, nick, login, hostname, target));
	}

	protected void onMessage(String target, String nick, String login, String hostname, String message) {
		spinThread(new ChannelMessage("onMessage", message, nick, login, hostname, target, target));
	}

	protected void onPrivateMessage(String nick, String login, String hostname, String message) {
		spinThread(new PrivateMessage("onPrivateMessage", message, nick, login, hostname, null));
	}

	protected void onAction(String nick, String login, String hostname, String target, String message) {
		if (target.indexOf('#') == 0)
			spinThread(new ChannelAction("onAction", message, nick, login, hostname, target, target));
		else
			spinThread(new PrivateAction("onAction", message, nick, login, hostname, target));
	}

	protected void onChannelInfo(String channel, int userCount, String topic) {
		spinThread(new ChannelInfo("onChannelInfo", channel));
	}

	protected void onDeVoice(String channel, String nick, String login, String hostname, String target) {
		spinThread(new ChannelUserMode("onDeVoice", channel, "v", true, target));
	}

	protected void onDeop(String channel, String nick, String login, String hostname, String target) {
		spinThread(new ChannelUserMode("onDeop", channel, "o", true, target));
	}

	protected void onInvite(String target, String nick, String login, String hostname, String channel) {
		spinThread(new ChannelInvite("onInvite", channel, nick, login, hostname, target));
	}

	protected void onJoin(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelJoin("onJoin", channel, nick, login, hostname));
	}

	protected void onKick(String channel, String nick, String login, String hostname, String target, String reason) {
		spinThread(new ChannelKick("onKick", channel, nick, login, hostname, target));
	}

	protected void onMode(String channel, String nick, String login, String hostname, String modes) {
		spinThread(new ChannelModes("onMode", channel, modes));
	}

	protected void onNickChange(String nick, String login, String hostname, String newNick) {
		spinThread(new NickChange("onNickChange", nick, login, hostname, newNick));
	}

	protected void onOp(String channel, String nick, String login, String hostname, String target) {
		spinThread(new ChannelUserMode("onOp", channel, "o", true, target));
	}

	protected void onPart(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelPart("onPart", channel, nick, login, hostname));
	}

	protected void onQuit(String nick, String login, String hostname, String message) {
		spinThread(new QuitEvent("onQuit", message, nick, login, hostname));
	}

	protected void onRemoveChannelBan(String channel, String nick, String login, String hostname, String param) {
		spinThread(new ChannelParamMode("onRemoveChannelBan", channel, "b", false, param));
	}

	protected void onRemoveChannelKey(String channel, String nick, String login, String hostname, String param) {
		spinThread(new ChannelParamMode("onRemoveChannelKey", channel, "k", false, param));
	}

	protected void onRemoveChannelLimit(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onRemoveChannelLimit", channel, "l", false));
	}

	protected void onRemoveInviteOnly(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onRemoveInviteOnly", channel, "i", false));
	}

	protected void onRemoveModerated(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onRemoveModerated", channel, "m", false));
	}

	protected void onRemoveNoExternalMessages(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onRemoveNoExternalMessages", channel, "n", false));
	}

	protected void onRemovePrivate(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onRemovePrivate", channel, "p", false));
	}

	protected void onRemoveSecret(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onRemoveSecret", channel, "s", false));
	}

	protected void onRemoveTopicProtection(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onRemoveTopicProtection", channel, "t", false));
	}

	protected void onSetChannelBan(String channel, String nick, String login, String hostname, String param) {
		spinThread(new ChannelParamMode("onSetChannelBan", channel, "b", true, param));
	}

	protected void onSetChannelKey(String channel, String nick, String login, String hostname, String param) {
		spinThread(new ChannelParamMode("onSetChannelKey", channel, "k", true, param));
	}

	protected void onSetInviteOnly(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onSetInviteOnly", channel, "i", true));
	}

	protected void onSetModerated(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onSetModerated", channel, "m", true));
	}

	protected void onSetNoExternalMessages(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onSetNoExternalMessages", channel, "n", true));
	}

	protected void onSetPrivate(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onSetPrivate", channel, "p", true));
	}

	protected void onSetSecret(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onSetSecret", channel, "s", true));
	}

	protected void onSetTopicProtection(String channel, String nick, String login, String hostname) {
		spinThread(new ChannelMode("onSetTopicProtection", channel, "t", true));
	}

	protected void onTopic(String channel, String message, String nick, long date, boolean changed) {
		spinThread(new ChannelTopic("onTopic", channel, message));
	}

	protected void onUnknown(String line) {
		spinThread(new UnknownEvent("onUnknown"));
	}

	protected void onUserMode(String targetNick, String nick, String login, String hostname, String modes) {
		spinThread(new UserModes("onUserMode", modes));
	}

	protected void onVoice(String channel, String nick, String login, String hostname, String target) {
		spinThread(new ChannelUserMode("onVoice", channel, "v", true, target));
	}

	// END PASTE!

	private synchronized void spinThread(IRCEvent ev)
	{
		int c;
		boolean done = false;
		int count = 0;

		while( !done )
		{
			for( c = 0; c < choobThreads.size() ; c++ )
			{
				System.out.println("Looking for threads.. " + c);

				/*
				 * TODO
				 * Potential race condition here. Since synthesized events can
				 * be sent down through here, this is significant:
				 * The assumption is that if a thread is not busy it will only
				 * be grabbed by the currently running thread. We need a lock()
				 * type call to lock the thread - which returns true if the
				 * lock succeeded.
				 *
				 * Fix attempted -- bucko
				 */
				//if( !((ChoobThread)choobThreads.get(c)).isBusy() )
				if( ((ChoobThread)choobThreads.get(c)).lock() )
				{
					done = true;

					ChoobThread tempThread = ((ChoobThread)choobThreads.get(c));

					try
					{
//						modules.logger.addLog(newCon);
					}
					catch( Exception e )
					{
						System.out.println("Exception: " + e + " Cause: " + e.getCause());
						e.printStackTrace();
					}

					tempThread.setEvent( ev );
					tempThread.setIRC( irc );

					synchronized( tempThread.getWaitObject() )
					{
						tempThread.getWaitObject().notify();
					}

					break;
				}
			}

			if (done) break;

			count++;
			if (count > 3 && choobThreads.size() < MAXTHREADS ) {
				// I guess we'll never be getting a thread!
				// But we can make more! ^.^
				count = 0;
				addChoobThread();
				continue;
			}

			try
			{
				this.wait(1000);
			}
			catch( Exception e )
			{
				// Oh noes! We've been interrupted.
			}
		}
	}

	/**
	 * Starts off a waiting worker thread to work on an incoming line from IRC.
	 * @param channel
	 * @param sender
	 * @param login
	 * @param hostname
	 * @param message
	 * @param privMessage
	 */
	/*
	private synchronized void spinThread(String channel, String sender, String login, String hostname, String message, boolean privMessage)
	{
		spinThread(new Message(sender,channel,message,privMessage));
	}*/
}
