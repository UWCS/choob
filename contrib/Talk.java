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
	public String[] helpCommandShout = {
		"Give a yell to deafen countries.",
		"<Text>",
		"<Text> is the text to yell"
	};
	public void commandShout( Message mes, Modules mods, IRCInterface irc )
	{
		irc.sendContextReply(mes, Colors.BOLD + mods.util.getParamString(mes));
	}

	public String[] helpCommandSay = {
		"Get the bot to say something.",
		"<Text>",
		"<Text> is the text to say"
	};
	public void commandSay( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextMessage(con, modules.util.getParamString(con));
	}

	public String[] helpCommandReply = {
		"Get the bot to reply to you.",
		"<Text>",
		"<Text> is the text with which to reply"
	};
	public void commandReply( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextReply(con, modules.util.getParamString(con));
	}

	public String[] helpCommandMsg = {
		"Get the bot to speak to people.",
		"<Target> <Text>",
		"<Target> is the destination",
		"<Text> is the message to send"
	};
	public void commandMsg( Message con, Modules modules, IRCInterface irc )
	{
		String params = modules.util.getParamString(con);
		int spacePos = params.indexOf(' ');
		if (spacePos == -1) {
			irc.sendContextReply(con, "Not enough parameters!");
		} else {
			String target = params.substring(0, spacePos);
			String message = params.substring(spacePos + 1);
			irc.sendTaggedMessage(target, message, con);
		}
	}

	public String[] helpCommandMe = {
		"Get the bot to do things.",
		"<Text>",
		"<Text> is the text that describes what to do"
	};
	public void commandMe( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextAction(con, modules.util.getParamString(con));
	}

	public String[] helpCommandDescribe = {
		"Get the bot to do things to people.",
		"<Target> <Text>",
		"<Target> is the destination",
		"<Text> is the thing to do"
	};
	public void commandDescribe( Message con, Modules modules, IRCInterface irc )
	{
		String params = modules.util.getParamString(con);
		int spacePos = params.indexOf(' ');
		if (spacePos == -1) {
			irc.sendContextReply(con, "Not enough parameters!");
		} else {
			String target = params.substring(0, spacePos);
			String message = params.substring(spacePos + 1);
			irc.sendTaggedAction(target, message, con);
		}
	}
}
