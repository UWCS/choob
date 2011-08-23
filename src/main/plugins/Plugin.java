import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.ChoobNoSuchPluginException;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

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

	private final Modules mods;
	private final IRCInterface irc;
	public Plugin(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	void loadOrReloadPlugin(final Message mes, final String pluginName, final String url, final boolean reloading)
	{
		if (!reloading)
			irc.sendContextReply(mes, "Loading plugin '" + pluginName + "'...");
		else
			irc.sendContextReply(mes, "Reloading plugin '" + pluginName + "'...");

		final String actioning = (reloading ? "re" : "") + "loading";
		final String actioned  = (reloading ? "re" : "") + "loaded";

		try
		{
			if (!reloading)
				mods.plugin.addPlugin(pluginName, url);
			else
				mods.plugin.reloadPlugin(pluginName);
			String[] info;
			info = getInfo(pluginName);
			if (info.length >= 3)
				irc.sendContextReply(mes, "Plugin '" + pluginName + "' " + actioned + " OK, new version is " + info[3] + ".");
			else
				irc.sendContextReply(mes, "Plugin '" + pluginName + "' " + actioned + " OK, but has missing info.");
		}
		catch (final ChoobNoSuchPluginException e)
		{
			if (!reloading)
				irc.sendContextReply(mes, "Error " + actioning + " plugin '" + pluginName + "'; plugin not found.");
			else
				irc.sendContextReply(mes, "Never seen plugin '" + pluginName + "' before, cannot reload it.");
		}
		catch (final ChoobNoSuchCallException e)
		{
			irc.sendContextReply(mes, "Plugin '" + pluginName + "' " + actioned + ", but doesn't have any info.");
		}
		catch (final ClassCastException e)
		{
			irc.sendContextReply(mes, "Plugin '" + pluginName + "' " + actioned + ", but has invalid info.");
		}
		catch (final Exception e)
		{
			irc.sendContextReply(mes, "Error " + actioning + " plugin '" + pluginName + "', see log for more details. " + e);
			e.printStackTrace();
		}
	}

	public String[] helpCommandLoad = {
		"Load a plugin.",
		"[<Name>] <URL>",
		"<Name> is an optional name for the plugin (if you don't give one, it'll be guessed from the URL)",
		"<URL> is the URL from which to load the plugin"
	};
	public void commandLoad( final Message mes )
	{
		// First, do auth!
		final List<String> params = mods.util.getParams( mes );

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

			url=params.get(2);
			classname=params.get(1);
			if ( classname.indexOf("/") != -1 )
			{
				irc.sendContextReply(mes, "Arguments the other way around, you spoon.");
				return;
			}
		}

		mods.security.checkNickPerm( new ChoobPermission( "plugin.load." + classname.toLowerCase() ), mes );

		try
		{
			mods.security.addGroup("plugin." + classname.toLowerCase());
		}
		catch (final ChoobException e)
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
	public void commandReload(final Message mes) {
		final List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			irc.sendContextReply(mes, "Syntax: 'Plugin.Reload " + helpCommandReload[1] + "'.");
			return;
		}

		for (int i = 1; i < params.size(); ++i)
		{
			final String pluginName = params.get(i);
			mods.security.checkNickPerm(new ChoobPermission("plugin.load." + pluginName.toLowerCase()), mes);
			loadOrReloadPlugin(mes, pluginName, null, true);
		}
	}

	public String[] helpCommandDetach = {
		"Stops a plugin executing any new tasks.",
		"<Name>",
		"<Name> is the name of the plugin"
	};
	public void commandDetach(final Message mes) {
		final List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			irc.sendContextReply(mes, "Syntax: 'Plugin.Detach " + helpCommandDetach[1] + "'.");
			return;
		}

		final String pluginName = params.get(1);

		mods.security.checkNickPerm(new ChoobPermission("plugin.unload." + pluginName.toLowerCase()), mes);

		try {
			mods.plugin.detachPlugin(pluginName);
			irc.sendContextReply(mes, "Plugin detached OK! (It might still be running stuff, though.)");
		} catch (final ChoobNoSuchPluginException e) {
			irc.sendContextReply(mes, "Plugin " + pluginName + " isn't loaded!");
		}
	}

	public String[] helpCommandSetCore = {
		"Makes a plugin a core plugin.",
		"<Name>",
		"<Name> is the name of the plugin"
	};
	public void commandSetCore(final Message mes) {
		final List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			irc.sendContextReply(mes, "Syntax: 'Plugin.SetCore " + helpCommandSetCore[1] + "'.");
			return;
		}

		final String pluginName = params.get(1);

		mods.security.checkNickPerm(new ChoobPermission("plugin.core"), mes);

		try {
			mods.plugin.setCorePlugin(pluginName, true);
			irc.sendContextReply(mes, "Plugin is now core!");
		} catch (final ChoobNoSuchPluginException e) {
			irc.sendContextReply(mes, "Plugin " + pluginName + " doesn't exist!");
		}
	}

	public String[] helpCommandUnsetCore = {
		"Makes a core plugin no longer core.",
		"<Name>",
		"<Name> is the name of the plugin"
	};
	public void commandUnsetCore(final Message mes) {
		final List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			irc.sendContextReply(mes, "Syntax: 'Plugin.UnsetCore " + helpCommandUnsetCore[1] + "'.");
			return;
		}

		final String pluginName = params.get(1);

		mods.security.checkNickPerm(new ChoobPermission("plugin.core"), mes);

		try {
			mods.plugin.setCorePlugin(pluginName, false);
			irc.sendContextReply(mes, "Plugin is no longer core!");
		} catch (final ChoobNoSuchPluginException e) {
			irc.sendContextReply(mes, "Plugin " + pluginName + " doesn't exist!");
		}
	}

	public String[] helpCommandList = {
		"List all known plugins."
	};
	public void commandList(final Message mes) {
		// Get all plugins.
		final String[] plugins = mods.plugin.getLoadedPlugins();
		Arrays.sort(plugins);

		// Hash all core plugins.
		final String[] corePlugins = mods.plugin.getAllPlugins(true);
		final Set<String> coreSet = new HashSet<String>();
		for (final String corePlugin : corePlugins)
			coreSet.add(corePlugin.toLowerCase());

		final StringBuilder buf = new StringBuilder();
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

	private String[] getInfo(final String pluginName) throws ChoobNoSuchCallException, ClassCastException
	{
		return (String[])mods.plugin.callGeneric(pluginName, "Info", "");
	}

	public String[] helpCommandInfo = {
		"Get info about a plugin.",
		"<Plugin>",
		"<Plugin> is the name of the plugin"
	};
	public void commandInfo(final Message mes)
	{
		final List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			irc.sendContextReply(mes, "Syntax: 'Plugin.Info " + helpCommandInfo[1] + "'.");
			return;
		}

		final String pluginName = params.get(1);
		String[] info;
		try
		{
			info = getInfo(pluginName);
		}
		catch (final ChoobNoSuchCallException e)
		{
			irc.sendContextReply(mes, "Oi! Plugin " + pluginName + " isn't loaded. (Or has no info...)");
			return;
		}
		catch (final ClassCastException e)
		{
			irc.sendContextReply(mes, "Plugin " + pluginName + " had invalid info!");
			return;
		}

		if(info.length == 0) {
			irc.sendContextReply(mes, pluginName + " has no information available");
		} else {
			irc.sendContextReply(mes, pluginName + ": " + info[0] + " By " + info[1] + " <" + info[2] + ">; version is " + info[3] + ".");
		}

	}

	public String[] helpCommandSource = {
		"Get the source URL for a plugin.",
		"<Plugin>",
		"<Plugin> is the name of the plugin"
	};
	public void commandSource(final Message mes)
	{
		final List<String> params = mods.util.getParams(mes);
		if (params.size() == 1)
		{
			irc.sendContextReply(mes, "Syntax: 'Plugin.Source " + helpCommandInfo[1] + "'.");
			return;
		}

		final String[] plugins = mods.plugin.getLoadedPlugins();

		final String pluginName = params.get(1);
		final String pluginNameL = pluginName.toLowerCase();
		String source;
		try
		{
			source = mods.plugin.getPluginSource(pluginName);
		}
		catch (final ChoobNoSuchPluginException e)
		{
			irc.sendContextReply(mes, "Plugin " + pluginName + " has never been loaded.");
			return;
		}

		boolean loaded = false;
		for (final String plugin : plugins)
		{
			if (plugin.toLowerCase().equals(pluginNameL)) {
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
