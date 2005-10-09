import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;

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
	public void commandShout( Message mes, Modules mods, IRCInterface irc )
	{
		irc.sendContextReply(mes, Colors.BOLD + "Bold yellow on magenta.");
	}

	public void commandSay( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextReply(con, modules.util.getParamString(con));
	}

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

	public void commandMe( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextAction(con, modules.util.getParamString(con));
	}

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
