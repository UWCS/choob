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

		try
		{
			Properties botProps = new Properties();
			botProps.load(this.getClass().getClassLoader().getResourceAsStream ("bot.conf"));
			botName = botProps.getProperty("botName");
			dbUser = botProps.getProperty("dbUser");
			dbPass = botProps.getProperty("dbPass");
                        trigger = botProps.getProperty("botTrigger");
		}
		catch (Exception e)
		{
			System.out.println(e);
			System.out.println("Fatal error reading bot.conf. Exiting.");
                        e.printStackTrace(System.out);
			System.exit(2);
		}

		this.setName(botName);

		// Set the bot's hostname.
		this.setLogin("Choob");

		// Create a new database connection broker using the MySQL drivers
		broker = new DbConnectionBroker("com.mysql.jdbc.Driver", "jdbc:mysql://localhost/choob?autoReconnect=true&autoReconnectForPools=true&initialTimeout=1", dbUser, dbPass, 10, 20, "/tmp/db.log", 1, true, 60, 3) ;

		// Initialise our modules.
		modules = new Modules(broker, pluginMap, filterList, intervalList, this);

		// Create a new IRC interface
		irc = new IRCInterface( this );

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
		for( c = 0 ; c < 5 ; c++ )
		{
			ChoobThread tempThread = new ChoobThread(broker,modules,pluginMap,filterList,trigger);
			choobThreads.add(tempThread);
			tempThread.start();
		}

		watcher = new ChoobWatcherThread(intervalList, irc, pluginMap, modules);

		watcher.start();

		try
		{
			// We need to have an initial set of plugins that ought to be loaded as core.

			Connection dbConnection = broker.getConnection();
			PreparedStatement coreplugSmt = dbConnection.prepareStatement("SELECT * FROM CorePlugins;");
			ResultSet coreplugResults = coreplugSmt.executeQuery();
			if ( coreplugResults.first() )
				do
				{
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

		// Now we've finished most of the stuff we need high access priviledges
		// to do, we can set up our security manager that checks all priviledged
		// accesses from a Beanshell plugin with their permissions in the MySQL
		// table.
		System.setSecurityManager( new ChoobSecurityManager(broker) );
	}

	// Since Choob extends pircbot, we need to implement the following two methods
	// in order to receive IRC channel and private messages. There'll be some
	// more of these methods when Events are implemented.
	/**
	 * Over-ridden method from the Pircbot class receives message events from IRC.
	 * @param channel
	 * @param sender
	 * @param login
	 * @param hostname
	 * @param message
	 */
	protected void onMessage(String channel, String sender, String login, String hostname, String message)
	{
		// Spin off the appropriate thread to handle this.
		spinThread( channel, sender, login, hostname, message, false );
	}

	/**
	 * Over-ridden method from the Pircbot class receives private message events from IRC.
	 * @param sender
	 * @param login
	 * @param hostname
	 * @param message
	 */
	protected void onPrivateMessage(String sender, String login, String hostname, String message)
	{
		// Spin off the appropriate thread to handle this.
		spinThread( "", sender, login, hostname, message, true );
	}

	/**
	 * Over-ridden method from the Pircbot class receives nick-change events from IRC.
	 * @param oldNick
	 * @param login
	 * @param hostname
	 * @param newNick
	 */
/*
	protected void onNickChange(String oldNick, String login, String hostname, String newNick)
	{
		// Spin off the appropriate thread to handle this.
		spinThread( new anEvent(oldNick) );
	}
*/


	/* Handled internally in pircBot, overriding causes breakage, don't let it happen:
	protected void onFinger(String sourceNick, String sourceLogin, String sourceHostname, String target) { spinThread(new ChannelEvent(ChannelEvent.ce_Finger, new String[] {sourceNick, sourceLogin, sourceHostname, target })); }
	protected void onPing(String sourceNick, String sourceLogin, String sourceHostname, String target, String pingValue) { spinThread(new ChannelEvent(ChannelEvent.ce_Ping, new String[] {sourceNick, sourceLogin, sourceHostname, target, pingValue})); }
	protected void onServerPing(String response) { spinThread(new ChannelEvent(ChannelEvent.ce_ServerPing, new String[] {response  })); }
	protected void onServerResponse(int code, String response) { spinThread(new ChannelEvent(ChannelEvent.ce_ServerResponse, new String[] {Integer.toString(code), response  })); }
	protected void onTime(String sourceNick, String sourceLogin, String sourceHostname, String target) { spinThread(new ChannelEvent(ChannelEvent.ce_Time, new String[] {sourceNick, sourceLogin, sourceHostname, target })); }
	protected void onVersion(String sourceNick, String sourceLogin, String sourceHostname, String target) { spinThread(new ChannelEvent(ChannelEvent.ce_Version, new String[] {sourceNick, sourceLogin, sourceHostname, target })); }
	*/

	/* Protect against RFC breakage.
	protected void onNotice(String sourceNick, String sourceLogin, String sourceHostname, String target, String notice) { spinThread(new ChannelEvent(ChannelEvent.ce_Notice, new String[] {sourceNick, sourceLogin, sourceHostname, target, notice})); }
	*/

	/* Handled elsewhere in this file, for now:
	protected void onMessage(String channel, String sender, String login, String hostname, String message) { spinThread(new ChannelEvent(ChannelEvent.ce_Message, new String[] {channel, sender, login, hostname, message})); }
	protected void onPrivateMessage(String sender, String login, String hostname, String message) { spinThread(new ChannelEvent(ChannelEvent.ce_PrivateMessage, new String[] {sender, login, hostname, message })); }
	*/

	protected void onAction(String sender, String login, String hostname, String target, String action) { spinThread(new ChannelEvent(ChannelEvent.ce_Action, new String[] {sender, login, hostname, target, action})); }
	protected void onChannelInfo(String channel, int userCount, String topic) { spinThread(new ChannelEvent(ChannelEvent.ce_ChannelInfo, new String[] {channel, Integer.toString(userCount), topic })); }
	protected void onDeVoice(String channel, String sourceNick, String sourceLogin, String sourceHostname, String recipient) { spinThread(new ChannelEvent(ChannelEvent.ce_DeVoice, new String[] {channel, sourceNick, sourceLogin, sourceHostname, recipient})); }
	protected void onDeop(String channel, String sourceNick, String sourceLogin, String sourceHostname, String recipient) { spinThread(new ChannelEvent(ChannelEvent.ce_Deop, new String[] {channel, sourceNick, sourceLogin, sourceHostname, recipient})); }
	protected void onInvite(String targetNick, String sourceNick, String sourceLogin, String sourceHostname, String channel) { spinThread(new ChannelEvent(ChannelEvent.ce_Invite, new String[] {targetNick, sourceNick, sourceLogin, sourceHostname, channel})); }
	protected void onJoin(String channel, String sender, String login, String hostname) { spinThread(new ChannelEvent(ChannelEvent.ce_Join, new String[] {channel, sender, login, hostname })); }
	protected void onKick(String channel, String kickerNick, String kickerLogin, String kickerHostname, String recipientNick, String reason) { spinThread(new ChannelEvent(ChannelEvent.ce_Kick, new String[] {channel, kickerNick, kickerLogin, kickerHostname, recipientNick, reason})); }
	protected void onMode(String channel, String sourceNick, String sourceLogin, String sourceHostname, String mode) { spinThread(new ChannelEvent(ChannelEvent.ce_Mode, new String[] {channel, sourceNick, sourceLogin, sourceHostname, mode})); }
	protected void onNickChange(String oldNick, String login, String hostname, String newNick) { spinThread(new ChannelEvent(ChannelEvent.ce_NickChange, new String[] {oldNick, login, hostname, newNick })); }
	protected void onOp(String channel, String sourceNick, String sourceLogin, String sourceHostname, String recipient) { spinThread(new ChannelEvent(ChannelEvent.ce_Op, new String[] {channel, sourceNick, sourceLogin, sourceHostname, recipient})); }
	protected void onPart(String channel, String sender, String login, String hostname) { spinThread(new ChannelEvent(ChannelEvent.ce_Part, new String[] {channel, sender, login, hostname })); }
	protected void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason) { spinThread(new ChannelEvent(ChannelEvent.ce_Quit, new String[] {sourceNick, sourceLogin, sourceHostname, reason })); }
	protected void onRemoveChannelBan(String channel, String sourceNick, String sourceLogin, String sourceHostname, String hostmask) { spinThread(new ChannelEvent(ChannelEvent.ce_RemoveChannelBan, new String[] {channel, sourceNick, sourceLogin, sourceHostname, hostmask})); }
	protected void onRemoveChannelKey(String channel, String sourceNick, String sourceLogin, String sourceHostname, String key) { spinThread(new ChannelEvent(ChannelEvent.ce_RemoveChannelKey, new String[] {channel, sourceNick, sourceLogin, sourceHostname, key})); }
	protected void onRemoveChannelLimit(String channel, String sourceNick, String sourceLogin, String sourceHostname) { spinThread(new ChannelEvent(ChannelEvent.ce_RemoveChannelLimit, new String[] {channel, sourceNick, sourceLogin, sourceHostname })); }
	protected void onRemoveInviteOnly(String channel, String sourceNick, String sourceLogin, String sourceHostname) { spinThread(new ChannelEvent(ChannelEvent.ce_RemoveInviteOnly, new String[] {channel, sourceNick, sourceLogin, sourceHostname })); }
	protected void onRemoveModerated(String channel, String sourceNick, String sourceLogin, String sourceHostname) { spinThread(new ChannelEvent(ChannelEvent.ce_RemoveModerated, new String[] {channel, sourceNick, sourceLogin, sourceHostname })); }
	protected void onRemoveNoExternalMessages(String channel, String sourceNick, String sourceLogin, String sourceHostname) { spinThread(new ChannelEvent(ChannelEvent.ce_RemoveNoExternalMessages, new String[] {channel, sourceNick, sourceLogin, sourceHostname })); }
	protected void onRemovePrivate(String channel, String sourceNick, String sourceLogin, String sourceHostname) { spinThread(new ChannelEvent(ChannelEvent.ce_RemovePrivate, new String[] {channel, sourceNick, sourceLogin, sourceHostname })); }
	protected void onRemoveSecret(String channel, String sourceNick, String sourceLogin, String sourceHostname) { spinThread(new ChannelEvent(ChannelEvent.ce_RemoveSecret, new String[] {channel, sourceNick, sourceLogin, sourceHostname })); }
	protected void onRemoveTopicProtection(String channel, String sourceNick, String sourceLogin, String sourceHostname) { spinThread(new ChannelEvent(ChannelEvent.ce_RemoveTopicProtection, new String[] {channel, sourceNick, sourceLogin, sourceHostname })); }
	protected void onSetChannelBan(String channel, String sourceNick, String sourceLogin, String sourceHostname, String hostmask) { spinThread(new ChannelEvent(ChannelEvent.ce_SetChannelBan, new String[] {channel, sourceNick, sourceLogin, sourceHostname, hostmask})); }
	protected void onSetChannelKey(String channel, String sourceNick, String sourceLogin, String sourceHostname, String key) { spinThread(new ChannelEvent(ChannelEvent.ce_SetChannelKey, new String[] {channel, sourceNick, sourceLogin, sourceHostname, key})); }
	protected void onSetChannelLimit(String channel, String sourceNick, String sourceLogin, String sourceHostname, int limit) { spinThread(new ChannelEvent(ChannelEvent.ce_SetChannelLimit, new String[] {channel, sourceNick, sourceLogin, sourceHostname, Integer.toString(limit)})); }
	protected void onSetInviteOnly(String channel, String sourceNick, String sourceLogin, String sourceHostname) { spinThread(new ChannelEvent(ChannelEvent.ce_SetInviteOnly, new String[] {channel, sourceNick, sourceLogin, sourceHostname })); }
	protected void onSetModerated(String channel, String sourceNick, String sourceLogin, String sourceHostname) { spinThread(new ChannelEvent(ChannelEvent.ce_SetModerated, new String[] {channel, sourceNick, sourceLogin, sourceHostname })); }
	protected void onSetNoExternalMessages(String channel, String sourceNick, String sourceLogin, String sourceHostname) { spinThread(new ChannelEvent(ChannelEvent.ce_SetNoExternalMessages, new String[] {channel, sourceNick, sourceLogin, sourceHostname })); }
	protected void onSetPrivate(String channel, String sourceNick, String sourceLogin, String sourceHostname) { spinThread(new ChannelEvent(ChannelEvent.ce_SetPrivate, new String[] {channel, sourceNick, sourceLogin, sourceHostname })); }
	protected void onSetSecret(String channel, String sourceNick, String sourceLogin, String sourceHostname) { spinThread(new ChannelEvent(ChannelEvent.ce_SetSecret, new String[] {channel, sourceNick, sourceLogin, sourceHostname })); }
	protected void onSetTopicProtection(String channel, String sourceNick, String sourceLogin, String sourceHostname) { spinThread(new ChannelEvent(ChannelEvent.ce_SetTopicProtection, new String[] {channel, sourceNick, sourceLogin, sourceHostname })); }
	protected void onTopic(String channel, String topic, String setBy, long date, boolean changed) { spinThread(new ChannelEvent(ChannelEvent.ce_Topic, new String[] {channel, topic, setBy, Long.toString(date), Boolean.toString(changed)})); }
	protected void onUnknown(String line) { spinThread(new ChannelEvent(ChannelEvent.ce_Unknown, new String[] {line  })); }
	protected void onUserMode(String targetNick, String sourceNick, String sourceLogin, String sourceHostname, String mode) { spinThread(new ChannelEvent(ChannelEvent.ce_UserMode, new String[] {targetNick, sourceNick, sourceLogin, sourceHostname, mode})); }
	protected void onVoice(String channel, String sourceNick, String sourceLogin, String sourceHostname, String recipient) { spinThread(new ChannelEvent(ChannelEvent.ce_Voice, new String[] {channel, sourceNick, sourceLogin, sourceHostname, recipient})); }


	private synchronized void spinThread(anEvent ev)
	{
		int c;
		boolean done = false;

		while( !done )
		{
			for( c = 0; c < 5 ; c++ )
			{
				System.out.println("Looking for threads.. " + c);

				if( !((ChoobThread)choobThreads.get(c)).isBusy() )
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
	private synchronized void spinThread(String channel, String sender, String login, String hostname, String message, boolean privMessage)
	{
		spinThread(new Message(sender,channel,message,privMessage));
	}
}