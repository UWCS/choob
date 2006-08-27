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

	void loadOrReloadPlugin(Message mes, String pluginName, String url, boolean reloading)
	{
		if (!reloading)
			irc.sendContextReply(mes, "Loading plugin '" + pluginName + "'...");
		else
			irc.sendContextReply(mes, "Reloading plugin '" + pluginName + "'...");
		
		String actioning = (reloading ? "re" : "") + "loading";
		String actioned  = (reloading ? "re" : "") + "loaded";
		
		try
		{
			if (!reloading)
				mods.plugin.addPlugin(pluginName, url);
			else
				mods.plugin.reloadPlugin(pluginName);
			String[] info;
			info = getInfo(pluginName);
			if (info.length >= 3)
				irc.sendContextReply(mes, "Plugin " + actioned + " OK, new version is " + info[3] + ".");
			else
				irc.sendContextReply(mes, "Plugin " + actioned + " OK, but has missing info.");
		}
		catch (ChoobNoSuchCallException e)
		{
			irc.sendContextReply(mes, "Plugin " + actioned + ", but doesn't have any info.");
		}
		catch (ClassCastException e)
		{
			irc.sendContextReply(mes, "Plugin " + actioned + ", but has invalid info.");
		}
		catch (Exception e)
		{
			irc.sendContextReply(mes, "Error " + actioning + " plugin, see log for more details. " + e);
			e.printStackTrace();
		}
	}

	public String[] helpCommandLoad = {
		"Load a plugin.",
		"[<Name>] <URL>",
		"<Name> is an optional name for the plugin (if you don't give one, it'll be guessed from the URL)",
		"<URL> is the URL from which to load the plugin"
	};
	public void commandLoad( Message mes )
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

		mods.security.checkNickPerm( new ChoobPermission( "plugin.load." + classname.toLowerCase() ), mes );

		try
		{
			mods.security.addGroup("plugin." + classname.toLowerCase());
		}
		catch (ChoobException e)
		{
			// TODO: Make a groupExists() or something so we don't need to squelch this
		}

		loadOrReloadPlugin(mes, classname, url, false);
	}

	public String[] helpCommandReload = {
		"Reloads an existing plugin.",
		"<Name>",
		"<Name> is the name of the plugin"
	};
	public void commandReload(Message mes) {
		List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			irc.sendContextReply(mes, "Syntax: 'Plugin.Reload " + helpCommandReload[1] + "'.");
			return;
		}

		String pluginName = params.get(1);

		mods.security.checkNickPerm(new ChoobPermission("plugin.load." + pluginName.toLowerCase()), mes);

		loadOrReloadPlugin(mes, pluginName, null, true);
	}

	public String[] helpCommandDetach = {
		"Stops a plugin executing any new tasks.",
		"<Name>",
		"<Name> is the name of the plugin"
	};
	public void commandDetach(Message mes) {
		List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			irc.sendContextReply(mes, "Syntax: 'Plugin.Detach " + helpCommandDetach[1] + "'.");
			return;
		}

		String pluginName = params.get(1);

		mods.security.checkNickPerm(new ChoobPermission("plugin.unload." + pluginName.toLowerCase()), mes);

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
	public void commandSetCore(Message mes) {
		List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			irc.sendContextReply(mes, "Syntax: 'Plugin.SetCore " + helpCommandSetCore[1] + "'.");
			return;
		}

		String pluginName = params.get(1);

		mods.security.checkNickPerm(new ChoobPermission("plugin.core"), mes);

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
	public void commandUnsetCore(Message mes) {
		List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			irc.sendContextReply(mes, "Syntax: 'Plugin.UnsetCore " + helpCommandUnsetCore[1] + "'.");
			return;
		}

		String pluginName = params.get(1);

		mods.security.checkNickPerm(new ChoobPermission("plugin.core"), mes);

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
	public void commandList(Message mes) {
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

		irc.sendContextReply(mes, buf.toString());
	}

	private String[] getInfo(String pluginName) throws ChoobNoSuchCallException, ClassCastException
	{
		return (String[])mods.plugin.callGeneric(pluginName, "Info", "");
	}

	public String[] helpCommandInfo = {
		"Get info about a plugin.",
		"<Plugin>",
		"<Plugin> is the name of the plugin"
	};
	public void commandInfo(Message mes)
	{
		List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			irc.sendContextReply(mes, "Syntax: 'Plugin.Info " + helpCommandInfo[1] + "'.");
			return;
		}

		String pluginName = params.get(1);
		String[] info;
		try
		{
			info = getInfo(pluginName);
		}
		catch (ChoobNoSuchCallException e)
		{
			irc.sendContextReply(mes, "Oi! Plugin " + pluginName + " isn't loaded. (Or has no info...)");
			return;
		}
		catch (ClassCastException e)
		{
			irc.sendContextReply(mes, "Plugin " + pluginName + " had invalid info!");
			return;
		}

		irc.sendContextReply(mes, pluginName + ": " + info[0] + " By " + info[1] + " <" + info[2] + ">; version is " + info[3] + ".");
	}

	public String[] helpCommandSource = {
		"Get the source URL for a plugin.",
		"<Plugin>",
		"<Plugin> is the name of the plugin"
	};
	public void commandSource(Message mes)
	{
		List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			irc.sendContextReply(mes, "Syntax: 'Plugin.Source " + helpCommandInfo[1] + "'.");
			return;
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
			irc.sendContextReply(mes, "Plugin " + pluginName + " has never been loaded.");
			return;
		}

		boolean loaded = false;
		for (int i = 0; i < plugins.length; i++) {
			if (plugins[i].toLowerCase().equals(pluginNameL)) {
				loaded = true;
				break;
			}
		}
		if (loaded) {
			irc.sendContextReply(mes, pluginName + " is loaded from <" + source + ">.");
		} else {
			irc.sendContextReply(mes, pluginName + " was last loaded from <" + source + ">.");
		}
	}
}
