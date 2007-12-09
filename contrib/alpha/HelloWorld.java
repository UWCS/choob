/** @author Faux */

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

public class HelloWorld
{
	public void commandOne( Message mes, Modules mods, IRCInterface irc )
	{
		irc.sendContextReply(mes, "Hello world!");
	}
}
