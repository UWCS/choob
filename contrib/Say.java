import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;

class Say
{
	public void commandSay( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextMessage(con, modules.util.getParmString(con));
	}

	public void commandMe( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextAction(con, modules.util.getParmString(con));
	}
}
