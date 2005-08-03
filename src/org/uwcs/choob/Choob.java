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
		modules = new Modules(broker, pluginMap, filterList);

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

		try
		{
			// We need to have an initial set of plugins that ought to be loaded as core.

			Connection dbConnection = broker.getConnection();
			PreparedStatement coreplugSmt = dbConnection.prepareStatement("SELECT * FROM CorePlugins;");
			ResultSet coreplugResults = coreplugSmt.executeQuery();
			if ( coreplugResults.first() )
				do
					modules.plugin.addPlugin(coreplugResults.getString("URL"), coreplugResults.getString("PluginName"));
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

					Context newCon = new Context(sender,channel,message,privMessage);

					try
					{
						modules.logger.addLog(newCon);
					}
					catch( Exception e )
					{
						System.out.println("Exception: " + e + " Cause: " + e.getCause());
						e.printStackTrace();
					}

					tempThread.setContext( newCon );
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
}