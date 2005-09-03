import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.regex.*;

class Plugin
{
	public void commandLoadPlugin( Message con, Modules modules, IRCInterface irc )
	{
		List params = modules.util.getParams( con );

		String url="";
		String classname="";

		if (params.size() == 2)
		{
			Pattern pa;
			Matcher ma;
			url=params.get(1);
			pa = Pattern.compile("^.*\\/([^\\/]+)\\.java$");
			ma = pa.matcher(url);
			if (ma.matches())
				classname=ma.group(1);
			else
				irc.sendContextReply(con, "Unable to parse url (" + url + ") -> classname, please specify.");
		}
		else
		{
			if( params.size() != 3 )
			{
				irc.sendContextReply(con, "Syntax: [classname] url");
				return;
			}
			else
			{
				url=params.get(2);
				classname=params.get(1);
				if ( classname.indexOf("/") != -1 )
				{
					irc.sendContextReply(con, "Arguments the other way around, you spoon.");
					return;
				}

			}
		}


		irc.sendContextReply(con, "Loading plugin.. " + classname);

		try
		{
			modules.plugin.addPlugin(url, classname);
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
		List params = modules.util.getParams( mes );
		if (params.size() != 2)
		{
			irc.sendContextReply(mes, "You must specify a plugin name and only that.");
			return;
		}
		modules.plugin.reloadPluginPermissions((String) params.get(1));
		irc.sendContextReply(mes, "OK, permissions reloaded for " + params.get(1) + ".");
	}
}
