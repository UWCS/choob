import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;

class Plugin
{
	public void commandLoadPlugin( Message con, Modules modules, IRCInterface irc )
	{
		List parms = modules.util.getParms( con );

		if( parms.size() < 3 )
		{
			irc.sendContextReply(con, "Incorrect syntax! Dyooooooh!");
			return;
		}

		if ( parms.get(1).indexOf("/") != -1 )
		{
			irc.sendContextReply(con, "Arguments the other way around, you spoon.");
			return;
		}

		irc.sendContextReply(con, "Loading plugin.. " + parms.get(1));
		try
		{
			modules.plugin.addPlugin(parms.get(2), parms.get(1));
			irc.sendContextReply(con, "Plugin parsed, compiled and loaded!");
		}
		catch (Exception e)
		{
			irc.sendContextReply(con, "Error parsing plugin, see log for details.");
			e.printStackTrace();
		}
	}

	public void commandReloadPluginPermissions( Message mes, Modules modules, IRCInterface irc )
	{
		List parms = modules.util.getParms( mes );
		if (parms.size() != 2)
		{
			irc.sendContextReply(mes, "You must specify a plugin name and only that.");
			return;
		}
		modules.plugin.reloadPluginPermissions((String) parms.get(1));
		irc.sendContextReply(mes, "OK, permissions reloaded for " + parms.get(1) + ".");
	}
}
