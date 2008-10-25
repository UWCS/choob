/** @author Faux */

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

public class HelloWorld
{
	public void commandOne( final Message mes, final Modules mods, final IRCInterface irc )
	{
		irc.sendContextReply(mes, "Hello world!");
	}
}
