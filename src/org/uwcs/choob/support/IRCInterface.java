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
public final class IRCInterface
{
	private Choob bot;
	private Modules mods;

	public final static int MAX_MESSAGE_LENGTH=400; 		// Arbiatary hax!
	public final static int MAX_MESSAGE_TRUNCATION=100;

	/** Creates a new instance of IRCInterface */
	public IRCInterface(Choob bot) {
		this.bot = bot;
		this.mods = null; // Fixes dependency problem
	}

	public void grabMods()
	{
		if (this.mods==null)
			this.mods = bot.getMods();
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

	// Break apart a message and send it, Target, Message, Prefix.
	private void sendMessage(String t, String m, String p)
	{
		if (t==null || m==null)
			return;

		m=m.trim();

		if (m.length()==0)
			return;

		if (m.length() > MAX_MESSAGE_LENGTH)
			do
			{
				int maxsublen=Math.min(MAX_MESSAGE_LENGTH, m.length());
				String submes=m.substring(0, maxsublen);
				int lio=submes.lastIndexOf(' ');
				int mlen=m.length();

				if (lio==-1 || MAX_MESSAGE_LENGTH-lio > MAX_MESSAGE_TRUNCATION)
				{
					if (mlen>0)
						bot.sendMessage(t, submes + (mlen > MAX_MESSAGE_LENGTH ? "..." : ""));
					m=p+m.substring(maxsublen);
				}
				else
				{
					bot.sendMessage(t, m.substring(0, lio));
					m=p+m.substring(Math.min(lio, mlen));
				}
			}
			while (m.length() > p.length() );
		else
		{
			bot.sendMessage(t, m);
		}

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

		sendMessage(target, message, sprefix);
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

		sendMessage(target, message, "");
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

		sendMessage(target, message + " (" + ev.getNick() + ")", "");
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
