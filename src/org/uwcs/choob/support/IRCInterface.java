/*
 * IRCInterface.java
 *
 * Created on July 10, 2005, 10:56 PM
 */

package org.uwcs.choob.support;

import org.uwcs.choob.*;
import org.uwcs.choob.support.*;

/**
 *
 * @author  sadiq
 */
public class IRCInterface {
    private Choob bot;
    
    /** Creates a new instance of IRCInterface */
    public IRCInterface(Choob bot) {
        this.bot = bot;
    }
    
    public void sendContextMessage(Context con, String message)
    {
        if( con.isPrivMessage() )
        {
            bot.sendMessage(con.getNick(),message);
        }
        else
        {
            bot.sendMessage(con.getChannel(),message);
        }
    }
    
    public void sendMessage(String channel,String message)
    {
        if( System.getSecurityManager() != null )
        {
            System.getSecurityManager().checkPermission(new ChoobPermission("canSendMessage"));
        }
        
        bot.sendMessage(channel,message);
    }
    
    public void sendPrivateMessage(String nick,String message)
    {
        bot.sendMessage(nick,message);
    }
}