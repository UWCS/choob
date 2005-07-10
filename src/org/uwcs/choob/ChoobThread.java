/*
 * ChoobThread.java
 *
 * Created on June 16, 2005, 7:25 PM
 */

package org.uwcs.choob;

import org.uwcs.choob.plugins.*;
import org.uwcs.choob.modules.*;
import java.sql.*;
import org.uwcs.choob.support.*;
import java.util.*;
import java.util.regex.*;

/**
 * Worker thread. Waits on it's waitObject and then wakes, performs the operations
 * required on the line from IRC and then goes back to sleep.
 * @author sadiq
 */
public class ChoobThread extends Thread
{
    boolean running;
    Object waitObject;
    DbConnectionBroker dbBroker;
    Connection dbConnection;
    Modules modules;
    int threadID;
    Map pluginMap;
    
    /**
     * Holds value of property context.
     */
    private Context context;
    
    /**
     * Holds value of property busy.
     */
    private boolean busy;
    
    /** Creates a new instance of ChoobThread */
    public ChoobThread(DbConnectionBroker dbBroker, Modules modules, Map pluginMap)
    {
        waitObject = new Object();
        
        this.dbBroker = dbBroker;
        
        this.modules = modules;
        
        threadID = (int)(Math.random() * 1000);
        
        this.pluginMap = pluginMap;
    }
    
    public void run()
    {
        running = true;
        
        while( running )
        {
            try
            {
                synchronized( waitObject )
                {
                    dbConnection = dbBroker.getConnection();                    
                    
                    busy = false;
                    
                    waitObject.wait();
                    
                    System.out.println("Thread("+threadID+") handed line " + context.getText());
                    
                    busy = true;
                    
                    if( context.getText().indexOf("~") == 0 )
                    {
                        // Namespace alias code would go here
                        
                        String pluginName = context.getText().substring(context.getText().indexOf("~")+1, context.getText().indexOf("."));
                        int endPos = 0;
                        
                        if( context.getText().indexOf(" ") < 0 ) 
                        {
                            endPos = context.getText().length();
                        }
                        else
                        {
                            endPos = context.getText().indexOf(" ");
                        }
                        
                        String commandName = context.getText().substring(context.getText().indexOf(".")+1,endPos);
                        
                        System.out.println("Looking for plugin " + pluginName + " and command " + commandName);
                        
                        if( pluginMap.get(pluginName) != null ) 
                        {
                            System.out.println("Map for " + pluginName + " is not null, calling.");
                            Object tempPlugin = ((Object)pluginMap.get(pluginName));
                            
                            BeanshellPluginUtils.doCommand(tempPlugin, commandName, context, modules);
                        }
                    }
                    
                    dbBroker.freeConnection( dbConnection );
                }
            }
            catch( Exception e )
            {
                System.out.println("Exception: " + e);
                e.printStackTrace();
            }
            finally
            {
                busy = false;
            }            
        }
    }
    
    /**
     * Getter method for the thread's current Context object.
     * @return Value of property context.
     */
    public Context getContext()
    {
        return this.context;
    }
    
    /**
     * Setter method for the thread's current Context object.
     * @param context New value of property context.
     */
    public void setContext(Context context)
    {
        System.out.println("Context set for thread("+threadID+")");
        this.context = context;
    }
    
    /**
     * Checks whether the thread is busy or not.
     * @return Value of property busy.
     */
    public boolean isBusy()
    {
        return this.busy;
    }
    
    /**
     * Getter for waitObject.
     * @return Value of property waitObject.
     */
    public Object getWaitObject()
    {
        return this.waitObject;
    }
    
    /**
     * Setter for waitObject.
     * @param waitObject New value of property waitObject.
     */
    public void setWaitObject(Object waitObject)
    {
        this.waitObject = waitObject;
    }

    /**
     * Stops the thread performing another processing loop. Does not immediately terminate
     * thread execution.
     */    
    public void stopRunning()
    {
        running = false;
    }
}
