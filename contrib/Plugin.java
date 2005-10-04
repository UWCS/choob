import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.regex.*;
import java.util.*;

public class Plugin
{
	public void commandLoadPlugin( Message mes, Modules mods, IRCInterface irc )
	{
		// First, do auth!
		boolean valid;
		try
		{
			valid = (Boolean)mods.plugin.callAPI( "NickServ", "Check", mes.getNick() );
		}
		catch (ChoobException e)
		{
			System.err.println("NickServ check failed; assuming valid: " + e);
			valid = true;
		}

		if ( !valid )
		{
			irc.sendContextReply( mes, "Sorry, but you can only use this command when identified with NickServ" );
			return;
		}

		List<String> params = mods.util.getParams( mes );

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
			{
				irc.sendContextReply(mes, "Unable to parse url (" + url + ") -> classname, please specify.");
				return;
			}
		}
		else
		{
			if( params.size() != 3 )
			{
				irc.sendContextReply(mes, "Syntax: [classname] url");
				return;
			}
			else
			{
				url=params.get(2);
				classname=params.get(1);
				if ( classname.indexOf("/") != -1 )
				{
					irc.sendContextReply(mes, "Arguments the other way around, you spoon.");
					return;
				}
			}
		}

		if ( !mods.security.hasPerm( new ChoobPermission( "plugin.load." + classname.toLowerCase() ), mes.getNick() ) )
		{
			irc.sendContextReply( mes, "Bobdamn it! You're not authed to do that!" );
			return;
		}

		try
		{
			mods.security.addGroup("plugin." + classname.toLowerCase());
		}
		catch (ChoobException e)
		{
			// TODO: Make a groupExists() or something so we don't need to squelch this
		}

		irc.sendContextReply(mes, "Loading plugin.. " + classname);

		try
		{
			mods.plugin.addPlugin(classname, url);
			irc.sendContextReply(mes, "Plugin parsed, compiled and loaded!");
		}
		catch (Exception e)
		{
			irc.sendContextReply(mes, "Error parsing plugin, see log for details.");
			e.printStackTrace();
		}
	}
}
