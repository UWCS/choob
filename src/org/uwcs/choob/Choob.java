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

/**
 * Core class of the Choob bot, main interaction with IRC.
 */
public class Choob extends PircBot
{
    DbConnectionBroker broker;
    Map pluginMap;
    List choobThreads;
    Modules modules;

    /**
     * Constructor for Choob, initialises vital variables.
     * @throws IOException Possibly arises from the database connection pool creation.
     */
    public Choob() throws IOException
    {
        // We wrap the pluginMap with a synchronizedMap in order to prevent
        // concurrent modication of it and a possible race condition.
        pluginMap = Collections.synchronizedMap(new HashMap());

        // Set the bot's nickname.

        String Name;
		BufferedReader fl;
		try {
			fl = new BufferedReader(new FileReader("./botname.conf"));
			Name=fl.readLine();
			fl.close();
		}
		catch (IOException e)
		{
			Name="Choob";
		}

        this.setName(Name);

        // Set the bot's hostname.
        this.setLogin("Choob");

        // Create a new database connection broker using the MySQL drivers
        broker = new DbConnectionBroker("com.mysql.jdbc.Driver", "jdbc:mysql://localhost/choob?autoReconnect=true&autoReconnectForPools=true&initialTimeout=1", "choob", "choob", 10, 20, "/tmp/db.log", 1, true, 60, 3) ;

        // Initialise our modules.
        modules = new Modules(broker, pluginMap);
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
            ChoobThread tempThread = new ChoobThread(broker,modules,pluginMap);
            choobThreads.add(tempThread);
            tempThread.start();
        }

        try
        {
            // We need to have an initial set of plugins that ought to be loaded
            // as core. This needs to be done in a cleaner more-confiugurable
            // fashion.
            modules.plugin.addPlugin("http://sadiq.uwcs.co.uk/Test.java","Test");
            modules.plugin.addPlugin("http://sadiq.uwcs.co.uk/Plugin.java","Plugin");
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

                    Context newCon = new Context(sender,channel,message,privMessage,this);

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