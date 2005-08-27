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

	public void sendContextMessage(Message ev, String message)
	{
		if( ev.isPrivMessage() )
		{
			bot.sendMessage(ev.getNick(), message);
		}
		else
		{
			bot.sendMessage(ev.getChannel(), message);
		}
	}

	public void sendContextAction(Message ev, String message)
	{
		if( ev.isPrivMessage() )
		{
			bot.sendAction(ev.getNick(), message);
		}
		else
		{
			bot.sendAction(ev.getChannel(), message);
		}
	}

	public void sendContextReply(Message ev, String message)
	{
		if( ev.isPrivMessage() )
		{
			bot.sendMessage(ev.getNick(),message);
		}
		else
		{
			bot.sendMessage(ev.getChannel(),ev.getNick() + ": " + message);
		}
	}

	public void sendMessage(String target, String message)
	{
		if( System.getSecurityManager() != null )
		{
			System.getSecurityManager().checkPermission(new ChoobPermission("canSendMessage"));
		}

		bot.sendMessage(target, message);
	}

	public void sendAction(String target, String message)
	{
		if( System.getSecurityManager() != null )
		{
			System.getSecurityManager().checkPermission(new ChoobPermission("canSendMessage"));
		}

		bot.sendAction(target, message);
	}

/* See sendMessage.
	public void sendPrivateMessage(String nick,String message)
	{
		bot.sendMessage(nick,message);
	}*/
}
