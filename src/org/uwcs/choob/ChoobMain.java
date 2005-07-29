/*
 * Choob.java
 *
 * Created on June 1, 2005, 2:20 AM
 */

/**
 *
 * @author  sadiq
 */
package org.uwcs.choob;

import org.jibble.pircbot.*;

/**
 * Main class in the Choob project, simply creates a Choob instance and sets it
 * running.
 */
public class ChoobMain
{
    
    public static void main(String[] args)
    {
        
        // Now start our bot up.
        try
        {
            Choob bot = new Choob();
            
            bot.init();
            
            // Enable debugging output.
            bot.setVerbose(true);
            
            // Change nick
            bot.changeNick("Choob");
            
            // Connect to the IRC server.
            bot.connect("irc.uwcs.co.uk");
            
            // Join the #pircbot channel.
            bot.joinChannel("#bots");
        }
        catch( Exception e )
        {
            e.printStackTrace();
            System.out.println("Fatal exception in setting up bot. Exiting.");
        }
        
    }
    
}