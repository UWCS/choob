/*
 * IRCInterface.java
 *
 * Created on July 10, 2005, 10:56 PM
 */

package uk.co.uwcs.choob.support;

import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.events.*;
import java.security.AccessController;
import java.util.List;
import java.util.ArrayList;

/**
 * The primary way for plugins to pass feedback to IRC.
 */
public final class IRCInterface
{
	private Choob bot;
	private Modules mods;

	public final static int MAX_MESSAGE_LENGTH = 400; // Arbiatary hax!
	public final static int MAX_MESSAGE_TRUNCATION = 100;
	public final static int MAX_MESSAGES = 3; // Max messages before /msg is employed instead.

	/** Creates a new instance of IRCInterface */
	public IRCInterface(Choob bot)
	{
		this.bot = bot;
		this.mods = null; // Fixes dependency problem
	}

	public void grabMods()
	{
		if (this.mods==null)
			this.mods = bot.getMods();
	}

	/**
	 * Cleanse a message of bad things like newlines.
	 * @param message The text to cleanse.
	 * @return a safe string.
	 */
	public String cleanse(String message)
	{
		// TODO: See if there's any other nasties.
		if (message.indexOf('\n') != -1)
			return message.substring(0, message.indexOf('\n'));
		return message;
	}

	/**
	 * Similar to the sendContextReply function, except sends an action instead of a message.
	 * @param context A context object to target the reply at.
	 * @param message A String of the /me you want to send.
	 */
	public void sendContextAction(ContextEvent context, String message)
	{
		message = cleanse(message);
		// Can't really cutSting an action...
		if (message.length() > MAX_MESSAGE_LENGTH)
			message = message.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";

		String target, thePrefix;
		if( context instanceof PrivateEvent || mods.pc.isProtected(context.getContext()))
			bot.sendAction(((UserEvent)context).getNick(), message);
		else
			bot.sendAction(context.getContext(), message);
	}

	/**
	 * Break a part a list of messages into deliverable chunks.
	 * @param messages The strings to break apart.
	 * @param prefix A length to ensure space for in the cut strings.
	 * @return A list of strings, cut to the max message length.
	 */
	public List<String> cutStrings(List<String> messages, int prefix)
	{
		List<String> ret = new ArrayList<String>();
		for(String line: messages)
			ret.addAll(cutString(cleanse(line), prefix));
		return ret;
	}

	/**
	 * Break a part a list of messages into deliverable chunks.
	 * @param messages The strings to break apart.
	 * @param prefix A length to ensure space for in the cut strings.
	 * @return A list of strings, cut to the max message length.
	 */
	public List<String> cutStrings(String[] messages, int prefix)
	{
		List<String> ret = new ArrayList<String>();
		for(String line: messages)
			ret.addAll(cutString(cleanse(line), prefix));
		return ret;
	}

	/**
	 * Break a part a message into deliverable chunks.
	 * @param message The string to break apart.
	 * @param prefix A length to ensure space for in the cut strings.
	 * @return A list of strings, cut to the max message length.
	 */
	public List<String> cutString(String message, int prefix)
	{
		int max_length = MAX_MESSAGE_LENGTH - prefix;
		List<String> lines = new ArrayList<String>();
		int pos = 0;
		while (true)
		{
			if (message.length() - pos > max_length)
			{
				// must do some cutting.
				int spacePos = message.lastIndexOf(' ', pos + max_length);

				if (spacePos == -1 || pos + max_length - spacePos > MAX_MESSAGE_TRUNCATION)
				{
					// If we're here, need to cut a word...
					int newPos = pos + max_length - 3;
					lines.add(message.substring(pos, newPos) + "...");
					pos = newPos;
				}
				else
				{
					// Have a nice word break.
					lines.add(message.substring(pos, spacePos));
					pos = spacePos + 1;
				}
			}
			else
			{
				// End of string.
				lines.add(message.substring(pos));
				break;
			}
		}
		return lines;
	}

	/**
	 * Sends an in-context response to an event. Automatically decides between replying in the channel the event was triggered from, and private messaging the response.
	 * This is the best function to call, unless you never want the reply to appear in the channel.
	 * @param context A context to reply in.
	 * @param messages A Strings[] containing the message you want to send.
	 * @param prefix If in a channel, should the reply be prefixed with "&lt;Nick&gt;: "?
	 */
	public void sendContextReply(ContextEvent context, String[] messages, boolean prefix)
	{
		if ( !(context instanceof UserEvent) )
			throw new IllegalArgumentException("ConextEvent " + context + " passed to sendContextReply was not a contextEvent!");

		String nick = ((UserEvent)context).getNick();
		List<String> bits = cutStrings(messages, nick.length() + 2);

		privateSendContextMessage(context, bits, prefix);
	}

	/**
	 * Sends an in-context response to an event. Automatically decides between replying in the channel the event was triggered from, and private messaging the response.
	 * This is the best function to call, unless you never want the reply to appear in the channel.
	 * @param context A context to reply in.
	 * @param messages A list of Strings containing the message you want to send.
	 * @param prefix If in a channel, should the reply be prefixed with "&lt;Nick&gt;: "?
	 */
	public void sendContextReply(ContextEvent context, List<String> messages, boolean prefix)
	{
		if ( !(context instanceof UserEvent) )
			throw new IllegalArgumentException("ConextEvent " + context + " passed to sendContextReply was not a contextEvent!");

		String nick = ((UserEvent)context).getNick();
		List<String> bits = cutStrings(messages, nick.length() + 2);

		privateSendContextMessage(context, bits, prefix);
	}

	/**
	 * Sends an in-context response to an event. Automatically decides between replying in the channel the event was triggered from, and private messaging the response.
	 * This is the best function to call, unless you never want the reply to appear in the channel.
	 * @param context A context to reply in.
	 * @param message A String containing the message you want to send.
	 * @param prefix If in a channel, should the reply be prefixed with "&lt;Nick&gt;: "?
	 */
	public void sendContextReply(ContextEvent context, String message, boolean prefix)
	{
		if ( !(context instanceof UserEvent) )
			throw new IllegalArgumentException("ConextEvent " + context + " passed to sendContextReply was not a contextEvent!");

		String nick = ((UserEvent)context).getNick();
		List<String> bits = cutString(cleanse(message), nick.length() + 2);

		privateSendContextMessage(context, bits, prefix);
	}

	private void privateSendContextMessage(ContextEvent context, List<String> lines, boolean prefix)
	{
		String nick = ((UserEvent)context).getNick();
		String target, thePrefix;
		if( context instanceof PrivateEvent || mods.pc.isProtected(context.getContext()))
		{
			// Must send in private
			target = nick;
			thePrefix = "";
		}
		else if ( lines.size() > MAX_MESSAGES )
		{
			privateSendMessage(context.getContext(), nick + ": Sorry, the output is too long! Private messaging it to you!");
			target = nick;
			thePrefix = "";
		}
		else
		{
			// Send to channel
			if (prefix)
				thePrefix = nick + ": ";
			else
				thePrefix = "";

			target = context.getContext();
		}

		for(String line: lines)
			privateSendMessage(target, thePrefix + line);
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
	 * Send a simple reply to an event.
	 * @param context A context to reply in.
	 * @param message A String containing the message you want to send.
	 */
	public void sendContextReply(ContextEvent context, String message)
	{
		sendContextReply(context, message, true);
	}

	/**
	 * Send a block of lines as a single reply.
	 * @param context A context to reply in.
	 * @param lines A String[] containing the message you want to send.
	 */
	public void sendContextReply(ContextEvent context, String[] lines)
	{
		sendContextReply(context, lines, true);
	}

	/**
	 * Send a block of lines as a single reply.
	 * @param context A context to reply in.
	 * @param lines A list of Strings containing the message you want to send.
	 */
	public void sendContextReply(ContextEvent context, List<String> lines)
	{
		sendContextReply(context, lines, true);
	}

	/**
	 * Alias of sendContextReply that doesn't prefix the message with "{Nick}: "
	 * @param context A context to reply in.
	 * @param message A String containing the message you want to send.
	 */
	public void sendContextMessage(ContextEvent context, String message)
	{
		sendContextReply(context, message, false);
	}

	/**
	 * Send a block of lines as a single reply.
	 * @param context A context to reply in.
	 * @param lines A String[] containing the message you want to send.
	 */
	public void sendContextMessage(ContextEvent context, String[] lines)
	{
		sendContextReply(context, lines, false);
	}

	/**
	 * Send a block of lines as a single reply.
	 * @param context A context to reply in.
	 * @param lines A list of Strings containing the message you want to send.
	 */
	public void sendContextMessage(ContextEvent context, List<String> lines)
	{
		sendContextReply(context, lines, false);
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

		List<String> lines = cutString(cleanse(message), 0);

		for (String line: lines)
			privateSendMessage(target, line);
	}

	private void privateSendMessage(String target, String message)
	{
		if (target.trim().equalsIgnoreCase(bot.getName()))
			return;
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

		message = cleanse(message);
		// Can't really cutSting an action...
		if (message.length() > MAX_MESSAGE_LENGTH)
			message = message.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";

		bot.sendAction(target, cleanse(message));
	}

	/**
	 * Sends a raw IRC line to the server. Very dangerous!
	 * @param line The line of text to send. Should not be terminated with \n.
	 */
	public void sendRawLine(String line)
	{
		AccessController.checkPermission(new ChoobPermission("message.send.raw"));
		bot.sendRawLineViaQueue(line);
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

	public void quit(String message) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("state.quit"));
		bot.setExitCode(0);
		bot.quitServer(message);
	}

	public void restart(String message) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("state.quit"));
		bot.setExitCode(1);
		bot.quitServer(message);
	}

	public String[] getUsers(String channel)
	{
		ArrayList<String> nicks = new ArrayList<String>();
		org.jibble.pircbot.User[] us = bot.getUsers(channel);
		for (org.jibble.pircbot.User u : us)
			nicks.add(u.getNick());
		return nicks.toArray(new String[]{});
	}
}
