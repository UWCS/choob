import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;

class Hook2
{
	public void commandHook( Message con, Modules modules, IRCInterface irc )
	{
		modules.plugin.callAPI("Hook1.Hooker", new Object[] { con, modules, irc });
	}
}
