/** @author Faux */

import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;

public class Lua
{

	Modules mods;
	IRCInterface irc;

	public Lua (Modules mods, IRCInterface irc)
	{
		this.mods=mods;
		this.irc=irc;
	}


	public void commandDo( Message mes )
	{
		try
		{
			final JniLua l=new JniLua();
			irc.sendContextReply(mes, l.interpret(mods.util.getParamString(mes)));
		}
		catch (Exception e)
		{
			irc.sendContextReply(mes, e.toString());
		}
	}
}

