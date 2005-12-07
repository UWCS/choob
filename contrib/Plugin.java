import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.regex.*;
import java.util.*;

public class Plugin
{
	public String[] helpCommandLoad = {
		"Load a plugin.",
		"[<Name>] <URL>",
		"<Name> is an optional name for the plugin (if you don't give one, it'll be guessed from the URL)",
		"<URL> is the URL from which to load the plugin"
	};
	public void commandLoad( Message mes, Modules mods, IRCInterface irc )
	{
		// First, do auth!
		List<String> params = mods.util.getParams( mes );

		String url="";
		String classname="";

		if (params.size() == 2)
		{
			Pattern pa;
			Matcher ma;
			url=params.get(1);
			pa = Pattern.compile("^.*\\/([^\\/]+)\\.(java|js)$");
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

		mods.security.checkNickPerm( new ChoobPermission( "plugin.load." + classname.toLowerCase() ), mes.getNick() );

		try
		{
			mods.security.addGroup("plugin." + classname.toLowerCase());
		}
		catch (ChoobException e)
		{
			// TODO: Make a groupExists() or something so we don't need to squelch this
		}

		irc.sendContextReply(mes, "Loading plugin '" + classname + "'...");

		try
		{
			mods.plugin.addPlugin(classname, url);
			irc.sendContextReply(mes, "Plugin loaded OK!");
		}
		catch (Exception e)
		{
			irc.sendContextReply(mes, "Error loading plugin, see log for more details. " + e);
			e.printStackTrace();
		}
	}

	public String[] helpCommandReload = {
		"Reloads an existing plugin.",
		"<Name>",
		"<Name> is the name of the plugin"
	};
	public void commandReload(Message mes, Modules mods, IRCInterface irc) {
		List<String> params = mods.util.getParams(mes);
		String pluginName = params.get(1);
		
		mods.security.checkNickPerm(new ChoobPermission("plugin.load." + pluginName.toLowerCase()), mes.getNick());

		irc.sendContextReply(mes, "Reloading plugin '" + pluginName + "'...");
		
		try {
			mods.plugin.reloadPlugin(pluginName);
			irc.sendContextReply(mes, "Plugin reloaded OK!");
		} catch (Exception e) {
			irc.sendContextReply(mes, "Error reloading plugin, see log for more details. " + e);
			e.printStackTrace();
		}
	}

	public String[] helpCommandDetach = {
		"Stops a plugin executing any new tasks.",
		"<Name>",
		"<Name> is the name of the plugin"
	};
	public void commandDetach(Message mes, Modules mods, IRCInterface irc) {
		List<String> params = mods.util.getParams(mes);
		String pluginName = params.get(1);

		mods.security.checkNickPerm(new ChoobPermission("plugin.unload." + pluginName.toLowerCase()), mes.getNick());

		try {
			mods.plugin.detachPlugin(pluginName);
			irc.sendContextReply(mes, "Plugin detached OK! (It might still be running stuff, though.)");
		} catch (ChoobNoSuchPluginException e) {
			irc.sendContextReply(mes, "Plugin " + pluginName + " isn't loaded!");
		}
	}

	public String[] helpCommandSetCore = {
		"Makes a plugin a core plugin.",
		"<Name>",
		"<Name> is the name of the plugin"
	};
	public void commandSetCore(Message mes, Modules mods, IRCInterface irc) {
		List<String> params = mods.util.getParams(mes);
		String pluginName = params.get(1);

		mods.security.checkNickPerm(new ChoobPermission("plugin.core"), mes.getNick());

		try {
			mods.plugin.setCorePlugin(pluginName, true);
			irc.sendContextReply(mes, "Plugin is now core!");
		} catch (ChoobNoSuchPluginException e) {
			irc.sendContextReply(mes, "Plugin " + pluginName + " doesn't exist!");
		}
	}

	public String[] helpCommandUnsetCore = {
		"Makes a core plugin no longer core.",
		"<Name>",
		"<Name> is the name of the plugin"
	};
	public void commandUnsetCore(Message mes, Modules mods, IRCInterface irc) {
		List<String> params = mods.util.getParams(mes);
		String pluginName = params.get(1);

		mods.security.checkNickPerm(new ChoobPermission("plugin.core"), mes.getNick());

		try {
			mods.plugin.setCorePlugin(pluginName, false);
			irc.sendContextReply(mes, "Plugin is no longer core!");
		} catch (ChoobNoSuchPluginException e) {
			irc.sendContextReply(mes, "Plugin " + pluginName + " doesn't exist!");
		}
	}

	public String[] helpCommandList = {
		"List all known plugins."
	};
	public void commandList(Message mes, Modules mods, IRCInterface irc) {
		// Get all plugins.
		String[] plugins = mods.plugin.getAllPlugins(false);
		Arrays.sort(plugins);

		// Hash all core plugins.
		String[] corePlugins = mods.plugin.getAllPlugins(true);
		Set<String> coreSet = new HashSet<String>();
		for(int i=0; i<corePlugins.length; i++)
			coreSet.add(corePlugins[i]);

		StringBuilder buf = new StringBuilder();
		buf.append("Plugin list (core marked with *): ");
		for(int i=0; i<plugins.length; i++)
		{
			buf.append(plugins[i]);
			if (coreSet.contains(plugins[i]))
				buf.append("*");
			if (i == plugins.length - 2)
				buf.append(" and ");
			else if (i != plugins.length - 1)
				buf.append(", ");
		}
		buf.append(".");

		irc.sendContextReply(mes, buf.toString());
	}
}
