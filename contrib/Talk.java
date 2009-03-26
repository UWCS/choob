import org.jibble.pircbot.Colors;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.ChoobPermission;
import java.util.List;

/**
 * Choob talky talky plugin
 *
 * @author bucko
 *
 * Anyone who needs further docs for this module has some serious Java issues.
 * :)
 */

public class Talk
{
	public String[] info()
	{
		return new String[] {
			"Plugin which allows users to make the bot speak.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private final Modules mods;
	private final IRCInterface irc;
	public Talk(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public String[] helpCommandShout = {
		"Give a yell to deafen countries.",
		"<Text>",
		"<Text> is the text to yell"
	};
	public void commandShout( final Message mes )
	{
		irc.sendContextReply(mes, Colors.BOLD + mods.util.getParamString(mes));
	}

	public String[] helpCommandSay = {
		"Get the bot to say something.",
		"<Text>",
		"<Text> is the text to say"
	};
	public String commandSay( final String mes )
	{
		return mes;
	}

	public String[] helpCommandReply = {
		"Get the bot to reply to you.",
		"<Text>",
		"<Text> is the text with which to reply"
	};
	public void commandReply( final Message mes )
	{
		irc.sendContextReply(mes, mods.util.getParamString(mes));
	}

	public String[] helpCommandMsg = {
		"Get the bot to speak to people.",
		"<Target> <Text>",
		"<Target> is the destination",
		"<Text> is the message to send"
	};
	public void commandMsg( final Message mes )
	{
		final String params = mods.util.getParamString(mes);
		final int spacePos = params.indexOf(' ');
		if (spacePos == -1) {
			irc.sendContextReply(mes, "Syntax: '" + helpCommandMsg[1] + "'.");
		} else {
			final String target = params.substring(0, spacePos);
			final String message = params.substring(spacePos + 1);
			irc.sendMessage(target, mes.getNick() + " says: " + message);
		}
	}

	public String[] helpCommandMe = {
		"Get the bot to do things.",
		"<Text>",
		"<Text> is the text that describes what to do"
	};
	public void commandMe( final Message mes )
	{
		irc.sendContextAction(mes, mods.util.getParamString(mes));
	}


	public String[] helpCommandPrivMe = {
		"Get the bot to send an action to people [RETRICTED].",
		"<Target> <Text>",
		"<Target> is the destination",
		"<Text> is the message to send as an action"
	};
	public void commandPrivMe(Message mes)
	{
		mods.security.checkNickPerm(new ChoobPermission("plugins.talk.privaction"), mes);
		List<String> params = mods.util.getParams(mes,2);

		if (params.size() != 3)
		{
			irc.sendContextReply(mes,"Usage: <nick> <message>");
			return;
		}
		
		irc.sendAction(params.get(1),params.get(2));
	}

	public String[] helpCommandPrivMsg = {
		"Get the bot to send a message to people [RETRICTED].",
		"<Target> <Text>",
		"<Target> is the destination",
		"<Text> is the message to send"
	};
	public void commandPrivMsg(Message mes)
	{
		mods.security.checkNickPerm(new ChoobPermission("plugins.talk.privmes"), mes);
		List<String> params = mods.util.getParams(mes,2);

		if (params.size() != 3)
		{
			irc.sendContextReply(mes,"Usage: <nick> <message>");
			return;
		}
		
		irc.sendMessage(params.get(1),params.get(2));
	}

	public String[] helpCommandDescribe = {
		"Get the bot to do things to people.",
		"<Target> <Text>",
		"<Target> is the destination",
		"<Text> is the thing to do"
	};
	public void commandDescribe( final Message mes )
	{
		final String params = mods.util.getParamString(mes);
		final int spacePos = params.indexOf(' ');
		if (spacePos == -1) {
			irc.sendContextReply(mes, "Syntax: '" + helpCommandDescribe[1] + "'.");
		} else {
			final String target = params.substring(0, spacePos);
			final String message = params.substring(spacePos + 1);
			irc.sendAction(target, message + " (From " + mes.getNick() + ".)");
		}
	}
}
