import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;

import org.jibble.pircbot.Colors;

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

	private Modules mods;
	private IRCInterface irc;
	public Talk(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public String[] helpCommandShout = {
		"Give a yell to deafen countries.",
		"<Text>",
		"<Text> is the text to yell"
	};
	public void commandShout( Message mes )
	{
		irc.sendContextReply(mes, Colors.BOLD + mods.util.getParamString(mes));
	}

	public String[] helpCommandSay = {
		"Get the bot to say something.",
		"<Text>",
		"<Text> is the text to say"
	};
	public void commandSay( Message mes )
	{
		irc.sendContextMessage(mes, mods.util.getParamString(mes));
	}

	public String[] helpCommandReply = {
		"Get the bot to reply to you.",
		"<Text>",
		"<Text> is the text with which to reply"
	};
	public void commandReply( Message mes )
	{
		irc.sendContextReply(mes, mods.util.getParamString(mes));
	}

	public String[] helpCommandMsg = {
		"Get the bot to speak to people.",
		"<Target> <Text>",
		"<Target> is the destination",
		"<Text> is the message to send"
	};
	public void commandMsg( Message mes )
	{
		String params = mods.util.getParamString(mes);
		int spacePos = params.indexOf(' ');
		if (spacePos == -1) {
			irc.sendContextReply(mes, "Syntax: '" + helpCommandMsg[1] + "'.");
		} else {
			String target = params.substring(0, spacePos);
			String message = params.substring(spacePos + 1);
			irc.sendMessage(target, mes.getNick() + " says: " + message);
		}
	}

	public String[] helpCommandMe = {
		"Get the bot to do things.",
		"<Text>",
		"<Text> is the text that describes what to do"
	};
	public void commandMe( Message mes )
	{
		irc.sendContextAction(mes, mods.util.getParamString(mes));
	}

	public String[] helpCommandDescribe = {
		"Get the bot to do things to people.",
		"<Target> <Text>",
		"<Target> is the destination",
		"<Text> is the thing to do"
	};
	public void commandDescribe( Message mes )
	{
		String params = mods.util.getParamString(mes);
		int spacePos = params.indexOf(' ');
		if (spacePos == -1) {
			irc.sendContextReply(mes, "Syntax: '" + helpCommandDescribe[1] + "'.");
		} else {
			String target = params.substring(0, spacePos);
			String message = params.substring(spacePos + 1);
			irc.sendAction(target, message + " (From " + mes.getNick() + ".)");
		}
	}
}
