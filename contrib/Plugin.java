import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.regex.*;
import java.util.*;

public class Plugin
{
	public String[] info()
	{
		return new String[] {
			"Plugin loader/manipulator.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private Modules mods;
	private IRCInterface irc;
	public Plugin(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public String[] helpCommandLoad = {
		"Load a plugin.",
		"[<Name>] <URL>",
		"<Name> is an optional name for the plugin (if you don't give one, it'll be guessed from the URL)",
		"<URL> is the URL from which to load the plugin"
	};


	public Message[] cmdLoad( Message mes )
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
				return mes.contextReply( "Unable to parse url (" + url + ") -> classname, please specify.");
			}
		}
		else
		{
			if( params.size() != 3 )
			{
				return mes.contextReply("Syntax: [classname] url");
			}
			else
			{
				url=params.get(2);
				classname=params.get(1);
				if ( classname.indexOf("/") != -1 )
				{
					return mes.contextReply("Arguments the other way around, you spoon.");
				}
			}
		}

		mods.security.checkNickPerm( new ChoobPermission( "plugin.load." + classname.toLowerCase() ), mes );

		try
		{
			mods.security.addGroup("plugin." + classname.toLowerCase());
		}
		catch (ChoobException e)
		{
			// TODO: Make a groupExists() or something so we don't need to squelch this
		}
		List<String> replies = new ArrayList<String>();
		replies.add("Loading plugin '" + classname + "'...");

		try
		{
			mods.plugin.addPlugin(classname, url);
			replies.add("Plugin loaded OK!");
		}
		catch (Exception e)
		{
			replies.add("Error loading plugin, see log for more details. " + e);
			e.printStackTrace();
		}
		return mes.contextReply(replies);
	}

	public String[] helpCommandReload = {
		"Reloads an existing plugin.",
		"<Name>",
		"<Name> is the name of the plugin"
	};
	public Message[] cmdReload(Message mes) {
		List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			return mes.contextReply("Syntax: 'Plugin.Reload " + helpCommandReload[1] + "'.");
		}

		String pluginName = params.get(1);
		mods.security.checkNickPerm(new ChoobPermission("plugin.load." + pluginName.toLowerCase()), mes);
		List<String> replies = new ArrayList<String>();
		replies.add( "Reloading plugin '" + pluginName + "'...");
		try {
			mods.plugin.reloadPlugin(pluginName);
			replies.add("Plugin reloaded OK!");
		} catch (Exception e) {
			replies.add( "Error reloading plugin, see log for more details. " + e);
			e.printStackTrace();
		}
		return mes.contextReply(replies);
	}

	public String[] helpCommandDetach = {
		"Stops a plugin executing any new tasks.",
		"<Name>",
		"<Name> is the name of the plugin"
	};
	public Message[] cmdDetach(Message mes) {
		List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			return mes.contextReply( "Syntax: 'Plugin.Detach " + helpCommandDetach[1] + "'.");
		}

		String pluginName = params.get(1);

		mods.security.checkNickPerm(new ChoobPermission("plugin.unload." + pluginName.toLowerCase()), mes);

		try {
			mods.plugin.detachPlugin(pluginName);
			return mes.contextReply("Plugin detached OK! (It might still be running stuff, though.)");
		} catch (ChoobNoSuchPluginException e) {
			return mes.contextReply("Plugin " + pluginName + " isn't loaded!");
		}
	}

	public String[] helpCommandSetCore = {
		"Makes a plugin a core plugin.",
		"<Name>",
		"<Name> is the name of the plugin"
	};
	public Message[] cmdSetCore(Message mes) {
		List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			return mes.contextReply( "Syntax: 'Plugin.SetCore " + helpCommandSetCore[1] + "'.");

		}

		String pluginName = params.get(1);

		mods.security.checkNickPerm(new ChoobPermission("plugin.core"), mes);

		try {
			mods.plugin.setCorePlugin(pluginName, true);
			return mes.contextReply("Plugin is now core!");
		} catch (ChoobNoSuchPluginException e) {
			return mes.contextReply("Plugin " + pluginName + " doesn't exist!");
		}
	}

	public String[] helpCommandUnsetCore = {
		"Makes a core plugin no longer core.",
		"<Name>",
		"<Name> is the name of the plugin"
	};
	public Message[] cmdUnsetCore(Message mes) {
		List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			return mes.contextReply("Syntax: 'Plugin.UnsetCore " + helpCommandUnsetCore[1] + "'.");
		}

		String pluginName = params.get(1);

		mods.security.checkNickPerm(new ChoobPermission("plugin.core"), mes);

		try {
			mods.plugin.setCorePlugin(pluginName, false);
			return mes.contextReply("Plugin is no longer core!");
		} catch (ChoobNoSuchPluginException e) {
			return mes.contextReply("Plugin " + pluginName + " doesn't exist!");
		}
	}

	public String[] helpCommandList = {
		"List all known plugins."
	};
	public Message[] cmdList(Message mes) {
		// Get all plugins.
		String[] plugins = mods.plugin.getLoadedPlugins();
		Arrays.sort(plugins);

		// Hash all core plugins.
		String[] corePlugins = mods.plugin.getAllPlugins(true);
		Set<String> coreSet = new HashSet<String>();
		for(int i=0; i<corePlugins.length; i++)
			coreSet.add(corePlugins[i].toLowerCase());

		StringBuilder buf = new StringBuilder();
		buf.append("Plugin list (core marked with *): ");
		for(int i=0; i<plugins.length; i++)
		{
			buf.append(plugins[i]);
			if (coreSet.contains(plugins[i].toLowerCase()))
				buf.append("*");
			if (i == plugins.length - 2)
				buf.append(" and ");
			else if (i != plugins.length - 1)
				buf.append(", ");
		}
		buf.append(".");

		return mes.contextReply(buf.toString());
	}

	public String[] helpCommandInfo = {
		"Get info about a plugin.",
		"<Plugin>",
		"<Plugin> is the name of the plugin"
	};
	public Message[] cmdInfo(Message mes)
	{
		List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			return mes.contextReply("Syntax: 'Plugin.Info " + helpCommandInfo[1] + "'.");
		}

		String pluginName = params.get(1);
		String[] info;
		try
		{
			info = (String[])mods.plugin.callGeneric(pluginName, "Info", "");
		}
		catch (ChoobNoSuchCallException e)
		{
			return mes.contextReply("Oi! Plugin " + pluginName + " isn't loaded. (Or has no info...)");
		}
		catch (ClassCastException e)
		{
			return mes.contextReply("Plugin " + pluginName + " had invalid info!");
		}

		return mes.contextReply(pluginName + ": " + info[0] + " By " + info[1] + " <" + info[2] + ">; version is " + info[3] + ".");
	}
	
	public String[] helpCommandSource = {
		"Get the source URL for a plugin.",
		"<Plugin>",
		"<Plugin> is the name of the plugin"
	};
	public Message[] cmdSource(Message mes)
	{
		List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			return mes.contextReply("Syntax: 'Plugin.Source " + helpCommandInfo[1] + "'.");
		}
		
		String[] plugins = mods.plugin.getLoadedPlugins();
		
		String pluginName = params.get(1);
		String pluginNameL = pluginName.toLowerCase();
		String source;
		try
		{
			source = mods.plugin.getPluginSource(pluginName);
		}
		catch (ChoobNoSuchPluginException e)
		{
			return mes.contextReply( "Plugin " + pluginName + " has never been loaded.");
		}
		
		boolean loaded = false;
		for (int i = 0; i < plugins.length; i++) {
			if (plugins[i].toLowerCase().equals(pluginNameL)) {
				loaded = true;
				break;
			}
		}
		if (loaded) {
			return mes.contextReply(pluginName + " is loaded from <" + source + ">.");
		} else {
			return mes.contextReply( pluginName + " was last loaded from <" + source + ">.");
		}
	}
}
