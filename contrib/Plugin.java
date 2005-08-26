import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;

class Plugin
{
	public void commandLoadPlugin( Message con, Modules modules, IRCInterface irc )
	{
		List parms = modules.util.getParms( con );

		if( parms.size() < 3 )
		{
			irc.sendContextMessage(con, "Incorrect syntax! Dyooooooh!");
			return;
		}

		if ( parms.get(1).indexOf("/") != -1 )
		{
			irc.sendContextMessage(con, "Arguments the other way around, you spoon.");
			return;
		}

		irc.sendContextMessage(con, "Loading plugin.. " + parms.get(1));
		try
		{
			modules.plugin.addPlugin(parms.get(2), parms.get(1));
			irc.sendContextMessage(con, "Plugin parsed, compiled and loaded!");
		}
		catch (Exception e)
		{
			irc.sendContextMessage(con, "Error parsing plugin, see log for details.");
			e.printStackTrace();
		}
	}
}
