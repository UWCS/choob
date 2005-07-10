/*
 * PluginLoader.java
 *
 * Created on June 13, 2005, 1:25 PM
 */
package org.uwcs.choob.plugins;

import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import java.util.*;
import bsh.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.reflect.*;

/**
 * Set of utilities that are used to load/interogate Beanshell plugins.
 * @author sadiq
 */
public class BeanshellPluginUtils
{
    /**
     * Creates a plugin from a given URL and plugin name.
     * @param URL URL to plugin's source.
     * @param pluginName Class name of plugin.
     * @throws Exception Thrown if there's a syntactical error in the plugin's source.
     * @return Returns an instance of the new plugin.
     */    
        public static Object createBeanshellPlugin(String URL, String pluginName) throws Exception
    {
        Class coreClass;
        Interpreter i;
        
        String srcContent = "";
        i = new Interpreter();
        
        try
        {
            URL srcURL = new URL(URL);
            
            HttpURLConnection srcURLCon = (HttpURLConnection)srcURL.openConnection();
            
            srcURLCon.connect();
            
            BufferedReader srcReader = new BufferedReader(new InputStreamReader( srcURLCon.getInputStream() ));
            
            while( srcReader.ready() )
            {
                srcContent = srcContent + srcReader.readLine();
            }
            
            System.out.println(i.eval(srcContent));
            
            String classname = pluginName;
            
            Class newPlugin = i.getNameSpace().getClass(classname);
            
            if( newPlugin != null )
            {
                Object newPluginObject = newPlugin.newInstance();
                
                return newPluginObject;
            }
            else
            {
                throw new Exception("Could not load new plugin.");
            }
        }
        catch( bsh.EvalError e )
        {
            throw new Exception("Could not compile plugin " + e.getMessage(), e);
        }
        catch( Exception e )
        {
            throw new Exception("Could not compile plugin " + e.getMessage(), e);
        }
    }
    
        /**
         * Calls a given command*, filter*, interval* method in the plugin.
         * @param plugin Plugin to call.
         * @param func Function to call.
         * @param con Context from IRC.
         * @param mods Group of modules available.
         */        
    static private void callFunc(Object plugin, String func, Context con, Modules mods)
    {
        Class coreClass = plugin.getClass();
        try
        {
            if( coreClass != null )
            {
                Method tempMethod = coreClass.getDeclaredMethod(func,new Class[]
                { Context.class, Modules.class });
                
                Object[] objectArray = new Object[2];
                
                objectArray[0] = con;
                objectArray[1] = mods;
                
                tempMethod.invoke(plugin,objectArray);
            }
        }
        catch( Exception e )
        {
            if( e.getCause().getClass() == SecurityException.class )
            {
                con.sendMessage("Security exception: " + e.getCause());
            }
            System.out.println("Exception in calling plugin function: " + e);
            e.printStackTrace();
            // What exactly do we do here? We _know_ we'return going to get these.
        }
    }
    
    /**
     * Calls the create() / destroy() methods in a plugin.
     * @param plugin Plugin to call.
     * @param func Function to call.
     */    
    static private void callSpecialFunc(Object plugin, String func)
    {
        Class coreClass = plugin.getClass();
        try
        {
            if( coreClass != null )
            {
                Method tempMethod = coreClass.getDeclaredMethod(func,new Class[]{});
                
                Object[] objectArray = new Object[0];
                
                tempMethod.invoke(plugin,objectArray);
            }
        }
        catch( NoSuchMethodException e )
        {
            // Here we just shrug our shoulders and go 'meh' in a Skumby-esque fashion
            // If people don't want to provide a create/destroy method, we can't force
            // them.
        }
        catch( Exception e )
        {
            System.out.println("Exception in calling plugin function: " + e);
            e.printStackTrace();
        }
    }
    
    /**
     * Call the destroy() method in a plugin.
     * @param plugin
     */    
    static public void callPluginDestroy(Object plugin)
    {
        callSpecialFunc(plugin, "destroy");
    }
    
    /**
     * Call the create() method in a plugin.
     * @param plugin
     */    
    static public void callPluginCreate(Object plugin)
    {
        callSpecialFunc(plugin, "create");
    }    
    
    /**
     * Attempts to call a method in the plugin, triggered by a line from IRC.
     * @param plugin
     * @param command Command to call.
     * @param con Context from IRC.
     * @param mods Group of modules.
     */    
    static public void doCommand(Object plugin, String command, Context con, Modules mods)
    {
        System.out.println("Calling method command" + command);
        callFunc(plugin, "command" + command,con,mods);
    }
    
    /**
     *
     * @param plugin
     * @param filter
     * @param con
     * @param mods
     */    
    static public void doFilter(Object plugin, String filter, Context con, Modules mods)
    {
        callFunc(plugin, "filter" + filter,con,mods);
    }
    
    /**
     *
     * @param plugin
     * @param interval
     * @param con
     * @param mods
     */    
    static public void doInterval(Object plugin, String interval, Context con, Modules mods)
    {
        callFunc(plugin, "interval" + interval,con,mods);
    }
    
    /**
     *
     * @return
     */    
    static public List getFilters()
    {
        return null;
    }
    
    /**
     *
     * @return
     */    
    static public List getIntervals()
    {
        return null;
    }
    
}
