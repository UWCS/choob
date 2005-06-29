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

public class Choob extends PircBot
{
    DbConnectionBroker broker;
    Map pluginMap;
    List choobThreads;
    Modules modules;
    
    /** Creates a new instance of Choob */
    public Choob() throws IOException
    {
        pluginMap = Collections.synchronizedMap(new HashMap());
        
        this.setName("Choob");
        
        broker = new DbConnectionBroker("com.mysql.jdbc.Driver", "jdbc:mysql://localhost/choob?autoReconnect=true&autoReconnectForPools=true&initialTimeout=1", "choob", "choob", 10, 20, "/tmp/db.log", 1, true, 60, 3) ;
        
        modules = new Modules(broker, pluginMap);
    }
    
    public void init()
    {
        // Create our list of threads
        choobThreads = new ArrayList();
        
        int c;
        
        for( c = 0 ; c < 5 ; c++ )
        {
            ChoobThread tempThread = new ChoobThread(broker,modules,pluginMap);
            choobThreads.add(tempThread);
            tempThread.start();
        }
        
        try
        {
            modules.getPluginModule().addPlugin("http://sadiq.uwcs.co.uk/Test.java","Test");
            modules.getPluginModule().addPlugin("http://sadiq.uwcs.co.uk/Plugin.java","Plugin");
        }
        catch( Exception e ) 
        { 
            System.out.println(e); 
            e.printStackTrace(); 
        }
        
        System.setSecurityManager( new ChoobSecurityManager(broker) );
    }
    
    protected void onMessage(String channel, String sender, String login, String hostname, String message)
    {
        spinThread( channel, sender, login, hostname, message, false );
    }
    
    protected void onPrivateMessage(String sender, String login, String hostname, String message)
    {
        spinThread( "", sender, login, hostname, message, true );
    }
    
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
                        modules.getLoggerModule().addLog(newCon);
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
            
            //this.wait(1000);
        }
    }
}