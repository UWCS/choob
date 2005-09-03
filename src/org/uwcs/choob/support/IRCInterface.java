/*
 * IRCInterface.java
 *
 * Created on July 10, 2005, 10:56 PM
 */

package org.uwcs.choob.support;

import org.uwcs.choob.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.events.*;
import java.security.AccessController;

/**
 *
 * @author	sadiq
 */
public class IRCInterface {
	private Choob bot;
	private Modules mods;

	/** Creates a new instance of IRCInterface */
	public IRCInterface(Choob bot, Modules mods) {
		this.bot = bot;
		this.mods = mods;
	}

	/**
	 * Similar to the sendContextReply function, except sends an action instead of a message.
	 * @param ev The Message object to target the reply at.
	 * @param message A String of the /me you want to send.
	 */
	public void sendContextAction(Message ev, String message)
	{
		bot.sendAction(ev.getContext(), message);
	}

	/**
	 * Sends an in-context response to an event. Automatically decides between replying in the channel the event was triggered from, and private messaging the response.
	 * This is the best function to call, unless you never want the reply to appear in the channel.
	 * @param ev The Message object to target the reply at.
	 * @param message A String of the message you want to send.
	 * @param prefix If in a channel, should the reply be prefixed with "{Nick}: "?
	 */
	public void sendContextReply(Message ev, String message, boolean prefix)
	{
		if( ev instanceof PrivateEvent || mods.pc.isProtected(ev.getContext()) )
		{
			sendMessage(ev.getNick(),message);
		}
		else
		{
			if (prefix)
				sendMessage(ev.getContext(), ev.getNick() + ": " + message);
			else
				sendMessage(ev.getContext(), message);
		}
	}
	/**
	 * Facilitate the optional third parameter to sendContextReply.
	 */
	public void sendContextReply(Message ev, String message)
	{
		sendContextReply(ev, message, true);
	}

	/**
	 * Alias of sendContextReply that doesn't prefix the message with "{Nick}: "
	 */
	public void sendContextMessage(Message ev, String message)
	{
		sendContextReply(ev, message, false);
	}

	/**
	 * Sends a message to the target you specify, may be a #channel.
	 * It is better to use sendContextReply, if possible.
	 * @param target A String of the target you want to recive the message.
	 * @param message A String of the message you want to send.
	 */
	public void sendMessage(String target, String message)
	{
		AccessController.checkPermission(new ChoobPermission("message.send.privmsg"));

		bot.sendMessage(target, message);
	}

	/**
	 * Sends an action to the target you specify, may be a #channel.
	 * It is better to use sendContextReply, if possible.
	 * @param target A String of the target you want to recive the message.
	 * @param message A String of the /me you want to send.
	 */
	public void sendAction(String target, String message)
	{
		AccessController.checkPermission(new ChoobPermission("message.send.action"));

		bot.sendAction(target, message);
	}
}
