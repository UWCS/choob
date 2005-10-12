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
 * The primary way for plugins to pass feedback to IRC.
 */
public class IRCInterface {
	Choob bot;
	Modules mods;

	final int maxmsglength=400; // Arbiatary hax!
	final int maxmsgtruncation=30; // Arbiatary hax!

	/** Creates a new instance of IRCInterface */
	public IRCInterface(Choob bot) {
		this.bot = bot;
		this.mods = null; // Fixes dependency problem
	}

	public void setMods(Modules mods)
	{
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
	public void sendContextReply(ContextEvent ev, String message, boolean prefix)
	{
		if ( !(ev instanceof UserEvent) )
			return; // XXX!?!

		String sprefix="";
		String target=null;
		if( ev instanceof PrivateEvent)
			target=((UserEvent)ev).getNick();
		else if ( mods.pc.isProtected(ev.getContext()) ) // It's a channel
			target=((UserEvent)ev).getNick();
		else
		{
			if (prefix)
				sprefix = ((UserEvent)ev).getNick() + ": ";

			target=ev.getContext();
			message=sprefix+message;
		}

		if (target==null)
			return;

		if (message.length() > maxmsglength)
		{
			// XXX HAX XXX HAX XXX HAX I'm so drunk.
			do
			{
				int lio=message.substring(0, Math.min(maxmsglength, message.length())).lastIndexOf(' ');
				if (lio==-1 || maxmsglength-lio > maxmsgtruncation)
				{
					System.out.println(maxmsglength-lio);
					if (message.trim().length()>0)
						bot.sendMessage(target, message.substring(0, Math.min(maxmsglength, message.length())) + (message.length() > maxmsglength ? "..." : ""));
					message=sprefix + message.substring(Math.min(maxmsglength, message.length()));
				}
				else
				{
					bot.sendMessage(target, message.substring(0, lio));
					message=sprefix + message.substring(Math.min(lio, message.length()));
				}
			}
			while (message.length() > sprefix.length() );
		}
		else
		{
			bot.sendMessage(target, message);
		}
	}


	/**
	 * See getTriggerRegex in Choob.
	 */
	public String getTriggerRegex()
	{
		return bot.getTriggerRegex();
	}

	/**
	 * See getTrigger in Choob.
	 */
	public String getTrigger()
	{
		return bot.getTrigger();
	}

	/**
	 * Facilitate the optional third parameter to sendContextReply.
	 */
	public void sendContextReply(ContextEvent ev, String message)
	{
		sendContextReply(ev, message, true);
	}

	/**
	 * Alias of sendContextReply that doesn't prefix the message with "{Nick}: "
	 */
	public void sendContextMessage(ContextEvent ev, String message)
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
	 * Sends a message to the target you specify, may be a #channel, having post-fixed it with the user's nick.
	 * This should be more-avaliable than sendMessage(), so it can be used in commands like !msg.
	 * The security of this function relies on plugins not being able to construct events...
	 * @param target A String of the target you want to recive the message.
	 * @param message A String of the message you want to send.
	 * @param ev The UserEvent that the Nick is to be pulled from.
	 */
	public void sendTaggedMessage(String target, String message, UserEvent ev)
	{
		AccessController.checkPermission(new ChoobPermission("message.send.tagged.privmsg"));

		bot.sendMessage(target, message + " (" + ev.getNick() + ")");
	}
	/**
	 * Sends an action to the target you specify, may be a #channel, having post-fixed it with the user's nick.
	 * This should be more-avaliable than sendAction(), so it can be used in commands like !describe.
	 * The security of this function relies on plugins not being able to construct events...
	 * @param target A String of the target you want to recive the message.
	 * @param message A String of the message you want to send.
	 * @param ev The UserEvent that the Nick is to be pulled from.
	 */
	public void sendTaggedAction(String target, String message, UserEvent ev)
	{
		AccessController.checkPermission(new ChoobPermission("message.send.tagged.action"));

		bot.sendAction(target, message + " (" + ev.getNick() + ")");
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

	public void join(String channel) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("state.join." + channel));
		bot.joinChannel(channel);
	}

	public void part(String channel) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("state.part." + channel));
		bot.partChannel(channel);
	}


}
