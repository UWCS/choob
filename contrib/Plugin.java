import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;

class Plugin
{
	public void commandLoadPlugin( Context con, Modules modules, IRCInterface irc )
	{
		List parms = modules.util.getParms( con );

		if( parms.size() < 3 )
		{
			irc.sendContextMessage(con, "Incorrect syntax! Dyooooooh!");
			return;
		}

		irc.sendContextMessage(con, "Loading plugin.. " + parms.get(1));
		modules.plugin.addPlugin(parms.get(2), parms.get(1));
		irc.sendContextMessage(con, "Plugin parsed, compiled and loaded!");
	}
}
