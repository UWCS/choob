/*
 * IRCInterface.java
 *
 * Created on July 10, 2005, 10:56 PM
 */

package uk.co.uwcs.choob.support;

import java.security.AccessController;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.co.uwcs.choob.Bot;
import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.events.ContextEvent;
import uk.co.uwcs.choob.support.events.PrivateEvent;
import uk.co.uwcs.choob.support.events.UserEvent;

/**
 * The primary way for plugins to pass feedback to IRC.
 */
public final class IRCInterface
{
	private final Bot bot;
	private Modules mods;
	private ChoobMessageQueue outQueue;

	public final static int MAX_MESSAGE_LENGTH = 400; // Arbiatary hax!
	public final static int MAX_MESSAGE_TRUNCATION = 100;
	public final static int MAX_MESSAGES = 3; // Max messages before /msg is employed instead.

	/** Creates a new instance of IRCInterface */
	public IRCInterface(final Bot bot)
	{
		this.bot = bot;
		this.outQueue = new ChoobMessageQueue(bot);
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
	public String cleanse(final String message)
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
	public void sendContextAction(final ContextEvent context, String message)
	{
		message = cleanse(message);
		// Can't really cutSting an action...
		if (message.length() > MAX_MESSAGE_LENGTH)
			message = message.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";

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
	public List<String> cutStrings(final List<String> messages, final int prefix)
	{
		final List<String> ret = new ArrayList<String>();
		for(final String line: messages)
			ret.addAll(cutString(cleanse(line), prefix));
		return ret;
	}

	/**
	 * Break a part a list of messages into deliverable chunks.
	 * @param messages The strings to break apart.
	 * @param prefix A length to ensure space for in the cut strings.
	 * @return A list of strings, cut to the max message length.
	 */
	public List<String> cutStrings(final String[] messages, final int prefix)
	{
		final List<String> ret = new ArrayList<String>();
		for(final String line: messages)
			ret.addAll(cutString(cleanse(line), prefix));
		return ret;
	}

	/**
	 * Break a part a message into deliverable chunks.
	 * @param message The string to break apart.
	 * @param prefix A length to ensure space for in the cut strings.
	 * @return A list of strings, cut to the max message length.
	 */
	public List<String> cutString(final String message, final int prefix)
	{
		final int max_length = MAX_MESSAGE_LENGTH - prefix;
		final List<String> lines = new ArrayList<String>();
		int pos = 0;
		while (true)
		{
			if (message.length() - pos > max_length)
			{
				// must do some cutting.
				final int spacePos = message.lastIndexOf(' ', pos + max_length);

				if (spacePos == -1 || pos + max_length - spacePos > MAX_MESSAGE_TRUNCATION)
				{
					// If we're here, need to cut a word...
					final int newPos = pos + max_length - 3;
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
	public void sendContextReply(final ContextEvent context, final String[] messages, final boolean prefix)
	{
		if ( !(context instanceof UserEvent) )
			throw new IllegalArgumentException("ConextEvent " + context + " passed to sendContextReply was not a contextEvent!");

		final String nick = ((UserEvent)context).getNick();
		final List<String> bits = cutStrings(messages, nick.length() + 2);

		privateSendContextMessage(context, bits, prefix);
	}

	/**
	 * Sends an in-context response to an event. Automatically decides between replying in the channel the event was triggered from, and private messaging the response.
	 * This is the best function to call, unless you never want the reply to appear in the channel.
	 * @param context A context to reply in.
	 * @param messages A list of Strings containing the message you want to send.
	 * @param prefix If in a channel, should the reply be prefixed with "&lt;Nick&gt;: "?
	 */
	public void sendContextReply(final ContextEvent context, final List<String> messages, final boolean prefix)
	{
		if ( !(context instanceof UserEvent) )
			throw new IllegalArgumentException("ConextEvent " + context + " passed to sendContextReply was not a contextEvent!");

		final String nick = ((UserEvent)context).getNick();
		final List<String> bits = cutStrings(messages, nick.length() + 2);

		privateSendContextMessage(context, bits, prefix);
	}

	/**
	 * Sends an in-context response to an event. Automatically decides between replying in the channel the event was triggered from, and private messaging the response.
	 * This is the best function to call, unless you never want the reply to appear in the channel.
	 * @param context A context to reply in.
	 * @param message A String containing the message you want to send.
	 * @param prefix If in a channel, should the reply be prefixed with "&lt;Nick&gt;: "?
	 */
	public void sendContextReply(final ContextEvent context, final String message, final boolean prefix)
	{
		if ( !(context instanceof UserEvent) )
			throw new IllegalArgumentException("ConextEvent " + context + " passed to sendContextReply was not a contextEvent!");

		final String nick = ((UserEvent)context).getNick();
		final List<String> bits = cutString(cleanse(message), nick.length() + 2);

		privateSendContextMessage(context, bits, prefix);
	}

	private void privateSendContextMessage(final ContextEvent context, final List<String> lines, final boolean prefix)
	{
		final String nick = ((UserEvent)context).getNick();
		String target, thePrefix;
		if( context instanceof PrivateEvent || mods.pc.isProtected(context.getContext()))
		{
			// Must send in private
			target = nick;
			thePrefix = "";
		}
		else if ( lines.size() > MAX_MESSAGES )
		{
			privateSendMessage(context.getContext(), nick, nick + ": Sorry, the output is too long! Private messaging it to you!");
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

		for(final String line: lines)
			privateSendMessage(target, nick, thePrefix + line);
	}

	/**
	 * Returns the bot's current nickname.
	 * @return current nickname of the bot.
	 */
	public String getNickname()
	{
		return bot.getNick();
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
		return bot.getTriggerRegex();
	}

	/**
	 * Send a simple reply to an event.
	 * @param context A context to reply in.
	 * @param message A String containing the message you want to send.
	 */
	public void sendContextReply(final ContextEvent context, final String message)
	{
		sendContextReply(context, message, true);
	}

	/**
	 * Send a block of lines as a single reply.
	 * @param context A context to reply in.
	 * @param lines A String[] containing the message you want to send.
	 */
	public void sendContextReply(final ContextEvent context, final String[] lines)
	{
		sendContextReply(context, lines, true);
	}

	/**
	 * Send a block of lines as a single reply.
	 * @param context A context to reply in.
	 * @param lines A list of Strings containing the message you want to send.
	 */
	public void sendContextReply(final ContextEvent context, final List<String> lines)
	{
		sendContextReply(context, lines, true);
	}

	/**
	 * Alias of sendContextReply that doesn't prefix the message with "{Nick}: "
	 * @param context A context to reply in.
	 * @param message A String containing the message you want to send.
	 */
	public void sendContextMessage(final ContextEvent context, final String message)
	{
		sendContextReply(context, message, false);
	}

	/**
	 * Send a block of lines as a single reply.
	 * @param context A context to reply in.
	 * @param lines A String[] containing the message you want to send.
	 */
	public void sendContextMessage(final ContextEvent context, final String[] lines)
	{
		sendContextReply(context, lines, false);
	}

	/**
	 * Send a block of lines as a single reply.
	 * @param context A context to reply in.
	 * @param lines A list of Strings containing the message you want to send.
	 */
	public void sendContextMessage(final ContextEvent context, final List<String> lines)
	{
		sendContextReply(context, lines, false);
	}

	/**
	 * Sends a message to the target you specify, may be a #channel.
	 * It is better to use sendContextReply, if possible.
	 * @param target A String of the target you want to recive the message.
	 * @param message A String of the message you want to send.
	 */
	public void sendMessage(final String target, final String message)
	{
		AccessController.checkPermission(new ChoobPermission("message.send.privmsg"));

		final List<String> lines = cutString(cleanse(message), 0);

		for (final String line: lines)
			privateSendMessage(target, "", line);
	}

	private void privateSendMessage(final String target, final String nick, final String message)
	{
		if (target.trim().equalsIgnoreCase(bot.getName()))
			return;

		String toSend;
		try
		{
			toSend = (String) mods.plugin.callAPI("OutputFilter", "Apply", target, nick, message);
		}
		catch (Throwable e)
		{
			// Discard the exception entirely, go team.
			toSend = message;
		}

		// stuff in here.
		outQueue.postMessage(target, toSend);
	}

	/**
	 * Sends an action to the target you specify, may be a #channel.
	 * It is better to use sendContextReply, if possible.
	 * @param target A String of the target you want to recive the message.
	 * @param message A String of the /me you want to send.
	 */
	public void sendAction(final String target, String message)
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
	public void sendRawLine(final String line)
	{
		AccessController.checkPermission(new ChoobPermission("message.send.raw"));
		bot.sendRawLineViaQueue(line);
	}

	public void op(final String channel, final String nick) throws ChoobException
	{
	    AccessController.checkPermission(new ChoobPermission("channel.admin." + channel));
	    bot.op(channel, nick);
	}

	public void deOp(final String channel, final String nick) throws ChoobException
	{
	    AccessController.checkPermission(new ChoobPermission("channel.admin." + channel));
	    bot.deOp(channel, nick);
	}

	public void voice(final String channel, final String nick) throws ChoobException
	{
	    AccessController.checkPermission(new ChoobPermission("channel.admin." + channel));
	    bot.voice(channel, nick);
	}

	public void deVoice(final String channel, final String nick) throws ChoobException
	{
	    AccessController.checkPermission(new ChoobPermission("channel.admin." + channel));
	    bot.voice(channel, nick);
	}

	public void kick(final String channel, final String nick) throws ChoobException
    {
        AccessController.checkPermission(new ChoobPermission("channel.admin." + channel));
        bot.kick(channel, nick);
    }

	public void kick(final String channel, final String nick, final String reason) throws ChoobException
	{
	    AccessController.checkPermission(new ChoobPermission("channel.admin." + channel));
	    bot.kick(channel, nick, reason);
	}

	public void ban(final String channel, final String nick) throws ChoobException
	{
	    AccessController.checkPermission(new ChoobPermission("channel.admin." + channel));
	    final String hostmask = nick + "!*@*";
	    bot.ban(channel, hostmask);
	}

	public void unban(final String channel, final String nick) throws ChoobException
	{
	    AccessController.checkPermission(new ChoobPermission("channel.admin." + channel));
	    final String hostmask = nick + "!*@*";
	    bot.unBan(channel, hostmask);
	}

	public void join(final String channel)
	{
		AccessController.checkPermission(new ChoobPermission("state.join." + channel));
		bot.sendRawLineViaQueue("JOIN " + channel);
	}

	public void part(final String channel) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("state.part." + channel));
		bot.partChannel(channel);
	}

	public void quit(final String message) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("state.quit"));
		bot.setExitCode(0);
		bot.quitServer(message);
	}

	public void restart(final String message) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("state.quit"));
		bot.setExitCode(1);
		bot.quitServer(message);
	}

	public String[] getUsers(final String channel)
	{
		return getUsersList(channel).toArray(new String[]{});
	}

	public List<String> getUsersList(final String channel)
	{
		final ArrayList<String> nicks = new ArrayList<String>();
		final org.jibble.pircbot.User[] us = bot.getUsers(channel);
		for (final org.jibble.pircbot.User u : us)
			nicks.add(u.getNick());
		return nicks;
	}

	public String[] getChannels() {
		return bot.getChannels();
	}

	public Set<String> getAllKnownUsers() {
		final Set<String> users = new HashSet<String>();
		for(String channel : getChannels()) {
			users.addAll(getUsersList(channel));
		}
		return users;
	}

	public boolean isKnownUser(String name) {
		return getAllKnownUsers().contains(name);
	}

	public long getMessageDelay()
	{
		return bot.getMessageDelay();
	}

}
