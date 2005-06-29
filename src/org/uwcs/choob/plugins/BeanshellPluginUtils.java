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
 *
 * @author  sadiq
 */
public class BeanshellPluginUtils
{
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
            System.out.println("Exception in calling plugin function: " + e);
            e.printStackTrace();
            // What exactly do we do here? We _know_ we'return going to get these.
        }
    }
    
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
    
    static public void callPluginDestroy(Object plugin)
    {
        callSpecialFunc(plugin, "destroy");
    }
    
    static public void callPluginCreate(Object plugin)
    {
        callSpecialFunc(plugin, "create");
    }    
    
    static public void doCommand(Object plugin, String command, Context con, Modules mods)
    {
        System.out.println("Calling method command" + command);
        callFunc(plugin, "command" + command,con,mods);
    }
    
    static public void doFilter(Object plugin, String filter, Context con, Modules mods)
    {
        callFunc(plugin, "filter" + filter,con,mods);
    }
    
    static public void doInterval(Object plugin, String interval, Context con, Modules mods)
    {
        callFunc(plugin, "interval" + interval,con,mods);
    }
    
    static public List getFilters()
    {
        return null;
    }
    
    static public List getIntervals()
    {
        return null;
    }
    
}
