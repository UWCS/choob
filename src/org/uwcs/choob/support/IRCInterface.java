/*
 * IRCInterface.java
 *
 * Created on July 10, 2005, 10:56 PM
 */

package org.uwcs.choob.support;

import org.uwcs.choob.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.security.AccessController;

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
		bot.sendMessage(ev.getContext(), message);
	}

	public void sendContextAction(Message ev, String message)
	{
		bot.sendAction(ev.getContext(), message);
	}

	public void sendContextReply(Message ev, String message)
	{
		if( ev instanceof PrivateEvent )
		{
			bot.sendMessage(ev.getContext(),message);
		}
		else
		{
			bot.sendMessage(ev.getContext(),ev.getNick() + ": " + message);
		}
	}

	public void sendMessage(String target, String message)
	{
		AccessController.checkPermission(new ChoobPermission("message.send.privmsg"));

		bot.sendMessage(target, message);
	}

	public void sendAction(String target, String message)
	{
		AccessController.checkPermission(new ChoobPermission("message.send.action"));

		bot.sendAction(target, message);
	}
}
