import java.util.List;

import uk.co.uwcs.choob.ChoobThread;
import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobBadSyntaxError;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.PrivateEvent;

/**
 * Options plugin - allows other plugins to access options which users can set.
 *
 * @author bucko
 */

class UserOption
{
	public int id;
	public String pluginName;
	public String userName;
	public String optionName;
	public String optionValue;
}

class GeneralOption
{
	public int id;
	public String pluginName;
	public String optionName;
	public String optionValue;
}

public class Options
{
	public String[] info()
	{
		return new String[] {
			"Plugin to allow plugins to provide user-settable options.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	Modules mods;
	IRCInterface irc;

	public Options(final Modules mods, final IRCInterface irc)
	{
		this.irc = irc;
		this.mods = mods;
	}

	public String[] helpCommandList = {
		"List available user plugin options.",
		"[ <Plugin> ]",
		"<Plugin> is the optional name of the plugin to list for (default: All)"
	};
	public void commandList(final Message mes)
	{
		_listOptions(mes, false);
	}

	public String[] helpCommandListGeneral = {
		"List available general plugin options.",
		"[ <Plugin> ]",
		"<Plugin> is the optional name of the plugin to list for (default: All)"
	};
	public void commandListGeneral(final Message mes)
	{
		_listOptions(mes, true);
	}

	private void _listOptions(final Message mes, final boolean general)
	{
		final String[] params = mods.util.getParamArray(mes);
		if (params.length > 2)
			throw new ChoobBadSyntaxError();

		if (params.length == 1)
		{
			// List all options.
			boolean first = true;
			final String[] plugins = mods.plugin.getLoadedPlugins();
			final StringBuilder output = new StringBuilder(general ? "General options: " : "User options: ");
			for (final String pluginName : plugins)
			{
				final String[] options = general ? _getGeneralOptions(pluginName) : _getUserOptions(pluginName);
				final String[] defaults = general ? _getGeneralOptionDefaults(pluginName) : _getUserOptionDefaults(pluginName);

				if (options == null)
					continue;

				if (!first)
					output.append("; ");
				first = false;

				_listOptions(output, pluginName, options, defaults);
			}
			output.append(".");
			irc.sendContextReply(mes, output.toString());
		}
		else
		{
			// List one plugin's options.
			final String pluginName = params[1];
			final String[] options = general ? _getGeneralOptions(pluginName) : _getUserOptions(pluginName);
			final String[] defaults = general ? _getGeneralOptionDefaults(pluginName) : _getUserOptionDefaults(pluginName);

			if (options == null)
			{
				irc.sendContextReply(mes, "Plugin " + pluginName + " does not exist or has no options.");
				return;
			}

			final StringBuilder output = new StringBuilder(general ? "General options for " : "User options for ");
			output.append(pluginName);
			output.append(": ");
			_listOptions(output, pluginName, options, defaults);
			output.append(".");
			irc.sendContextReply(mes, output.toString());
		}
	}

	private void _listOptions(final StringBuilder output, final String pluginName, final String[] options, final String[] defaults)
	{
		for (int i = 0; i < options.length; i++)
		{
			output.append(pluginName);
			output.append(".");
			output.append(options[i]);
			if (defaults != null && defaults[i] != null)
			{
				output.append(" (default: ");
				output.append(defaults[i]);
				output.append(")");
			}
			if (i == options.length - 2)
				output.append(" and ");
			else if (i != options.length - 1)
				output.append(", ");
		}
	}

	public String[] helpCommandSet = {
		"Set an option for a plugin for just you.",
		"<Plugin>.<Name>=[<Value>]",
		"<Plugin> is the name of the plugin to set for",
		"<Name> is the name of the option to set",
		"<Value> is the value to set the option to (omit to unset)"
	};
	public void commandSet(final Message mes)
	{
		final String nickName = mes.getNick();
		mods.security.checkAuth(mes);
		String userName = mods.security.getUserAuthName(nickName);
		userName = mods.security.getRootUser(userName);

		if (userName == null)
		{
			final String primaryNick = mods.nick.getBestPrimaryNick(nickName);
			final String rootNick = mods.security.getRootUser(primaryNick);
			if (nickName.equals(primaryNick))
				// They can't have registered their nick at all.
				irc.sendContextReply(mes, "Sorry, you need to register your username with the bot first. See Help.Help Security.AddUser.");
			else if (rootNick != null)
				// Registered but not linked.
				irc.sendContextReply(mes, "Sorry, you need to link your username with " + rootNick + " first. See Help.Help Security.UsingLink.");
			else
				// Not registered, and not primary.
				irc.sendContextReply(mes, "Sorry, you need to register your username (" + primaryNick + ") with the bot first. See Help.Help Security.AddUser.");
			return;
		}

		final String[] params = mods.util.getParamArray(mes);
		if (params.length != 2)
			throw new ChoobBadSyntaxError();

		final String[] option = params[1].split("\\.", 2);
		if (option.length != 2)
			throw new ChoobBadSyntaxError();

		final String[] vals = option[1].split("=", 2);
		if (vals.length != 2)
			throw new ChoobBadSyntaxError();

		final String[] opts = _getUserOptions(option[0]);
		boolean found = false;
		if (opts != null)
		{
			final String opt = vals[0].toLowerCase();
			for (final String opt2 : opts)
			{
				if (opt2.toLowerCase().equals(opt))
					found = true;
			}
		}
		if (!found)
		{
			irc.sendContextReply(mes, "User option " + option[0] + "." + vals[0] + " is not known.");
			return;
		}
		
		// Check the option is OK
		if (vals[1].length() > 0)
		{
			final String err = _checkUserOption(option[0], vals[0].toLowerCase(), vals[1], userName);
			if (err != null)
			{
				irc.sendContextReply(mes, "Error setting the user option " + option[0] + "." + vals[0] + ": " + err);
				return;
			}
		}

		// OK, have an option.
		final List<UserOption> options = mods.odb.retrieve(UserOption.class,
			"WHERE userName = '" + mods.odb.escapeString(userName) + "' AND"
			+ " pluginName = '" + mods.odb.escapeString(option[0]) + "' AND"
			+ " optionName = '" + mods.odb.escapeString(vals[0]) + "'");

		if (options.size() >= 1)
		{
			final UserOption userOption = options.get(0);
			if (vals[1].length() > 0)
			{
				userOption.optionValue = vals[1];
				mods.odb.update(userOption);
			}
			else
			{
				mods.odb.delete(userOption);
			}
		}
		else if (vals[1].length() > 0)
		{
			final UserOption userOption = new UserOption();
			userOption.userName = userName;
			userOption.pluginName = option[0];
			userOption.optionName = vals[0];
			userOption.optionValue = vals[1];
			mods.odb.save(userOption);
		}

		if (vals[1].length() > 0)
			irc.sendContextReply(mes, "Set user option " + option[0] + "." + vals[0] + " to '" + vals[1] + "'.");
		else
			irc.sendContextReply(mes, "Unset user option " + option[0] + "." + vals[0] + ".");
	}

	public String[] helpCommandSetGeneral = {
		"Set an option for a plugin that will apply to the plugin itself.",
		"<Plugin>.<Name>=[<Value>]",
		"<Plugin> is the name of the plugin to set for",
		"<Name> is the name of the option to set",
		"<Value> is the value to set the option to (omit to unset)"
	};
	public void commandSetGeneral(final Message mes)
	{
		final String[] params = mods.util.getParamArray(mes);
		if (params.length != 2)
			throw new ChoobBadSyntaxError();

		final String[] option = params[1].split("\\.", 2);
		if (option.length != 2)
			throw new ChoobBadSyntaxError();

		final String[] vals = option[1].split("=", 2);
		if (vals.length != 2)
			throw new ChoobBadSyntaxError();

		// TODO - make plugin owners always able to set this. Or something.
		mods.security.checkNickPerm(new ChoobPermission("plugin.options.set." + option[0]), mes);

		final String[] opts = _getGeneralOptions(option[0]);
		boolean found = false;
		if (opts != null)
		{
			final String opt = vals[0].toLowerCase();
			for (final String opt2 : opts)
			{
				if (opt2.toLowerCase().equals(opt))
					found = true;
			}
		}
		if (!found)
		{
			irc.sendContextReply(mes, "General option " + option[0] + "." + vals[0] + " is not known.");
			return;
		}
		
		// Check the option is OK
		if (vals[1].length() > 0)
		{
			final String err = _checkGeneralOption(option[0], vals[0].toLowerCase(), vals[1]);
			if (err != null)
			{
				irc.sendContextReply(mes, "Error setting the general option " + option[0] + "." + vals[0] + ": " + err);
				return;
			}
		}

		// OK, have an option.
		final List<GeneralOption> options = mods.odb.retrieve(GeneralOption.class,
			"WHERE pluginName = '" + mods.odb.escapeString(option[0]) + "' AND"
			+ " optionName = '" + mods.odb.escapeString(vals[0]) + "'");

		if (options.size() >= 1)
		{
			final GeneralOption generalOption = options.get(0);
			if (vals[1].length() > 0)
			{
				generalOption.optionValue = vals[1];
				mods.odb.update(generalOption);
			}
			else
			{
				mods.odb.delete(generalOption);
			}
		}
		else if (vals[1].length() > 0)
		{
			final GeneralOption generalOption = new GeneralOption();
			generalOption.pluginName = option[0];
			generalOption.optionName = vals[0];
			generalOption.optionValue = vals[1];
			mods.odb.save(generalOption);
		}

		if (vals[1].length() > 0)
			irc.sendContextReply(mes, "Set general option " + option[0] + "." + vals[0] + " to '" + vals[1] + "'.");
		else
			irc.sendContextReply(mes, "Unset general option " + option[0] + "." + vals[0] + ".");
	}

	public String[] helpCommandGet = {
		"Get your personal option values for plugins.",
		"[ <Plugin> ]",
		"<Plugin> is the plugin to limit options to (default: All)"
	};
	public void commandGet(final Message mes)
	{
		final String[] params = mods.util.getParamArray(mes);

		if (params.length > 2)
			throw new ChoobBadSyntaxError();

		final String pluginName;
		if (params.length == 2)
			pluginName = params[1];
		else
			pluginName = null;

		String userName;
		if (mods.security.hasAuth(mes)) {
			userName = mods.security.getRootUser(mods.security.getUserAuthName(mes.getNick()));
		} else {
			userName = mods.security.getRootUser(mods.nick.getBestPrimaryNick(mes.getNick()));
		}
		if (userName == null)
			userName = mes.getNick();

		final List<UserOption> options;
		if (pluginName != null)
		{
			options = mods.odb.retrieve(UserOption.class,
				"WHERE userName = '" + mods.odb.escapeString(userName) + "'"
				+ " AND pluginName = '" + mods.odb.escapeString(pluginName) + "'");
		}
		else
		{
			options = mods.odb.retrieve(UserOption.class,
				"WHERE userName = '" + mods.odb.escapeString(userName) + "'");
		}

		if (options.size() == 0)
		{
			irc.sendContextReply(mes, "No user options set.");
		}
		else
		{
			final StringBuilder output = new StringBuilder();
			if (pluginName == null)
				output.append("User options:");
			else
				output.append("User options for " + pluginName + ":");

			for (final UserOption option: options)
			{
				if (pluginName == null)
					output.append(" " + option.pluginName + "." + option.optionName + "=" + option.optionValue);
				else
					output.append(" " + option.optionName + "=" + option.optionValue);
			}
			irc.sendContextReply(mes, output.toString());
		}
	}

	public String[] helpCommandGetGeneral = {
		"Get all global option values for all plugins.",
		"[ <Plugin> ]",
		"<Plugin> is the plugin to limit options to (warning: some plugins contain passwords!)"
	};
	public void commandGetGeneral(final Message mes)
	{
		final String[] params = mods.util.getParamArray(mes);

		final String pluginName;
		if (params.length != 2)
		{
			// Make sure we only show ALL plugin general options in private.
			if (params.length != 1 || !(mes instanceof PrivateEvent))
				throw new ChoobBadSyntaxError();
			pluginName = null;
		}
		else
		{
			pluginName = params[1];
		}

		// TODO - make plugin owners always able to set this. Or something.
		mods.security.checkNickPerm(new ChoobPermission("plugin.options.get"), mes);

		final List<GeneralOption> options;
		if (pluginName != null)
		{
			options = mods.odb.retrieve(GeneralOption.class,
				"WHERE pluginName = '" + mods.odb.escapeString(pluginName) + "'");
		}
		else
		{
			options = mods.odb.retrieve(GeneralOption.class,
				"WHERE 1");
		}

		if (options.size() == 0)
		{
			irc.sendContextReply(mes, "No general options set.");
		}
		else
		{
			final StringBuilder output = new StringBuilder();
			if (pluginName == null)
				output.append("General options:");
			else
				output.append("General options for " + pluginName + ":");

			for(final GeneralOption option: options)
			{
				if (pluginName == null)
					output.append(" " + option.pluginName + "." + option.optionName + "=" + option.optionValue);
				else
					output.append(" " + option.optionName + "=" + option.optionValue);
			}
			irc.sendContextReply(mes, output.toString());
		}
	}

	public String[] helpCommandHelp = {
		"Get help on an option.",
		"<Plugin>.<Name>",
		"<Plugin> is the name of the plugin the option lives in",
		"<Name> is the name of the option"
	};
	public void commandHelp(final Message mes)
	{
		final String[] params = mods.util.getParamArray(mes);

		if (params.length != 2)
			throw new ChoobBadSyntaxError();

		final String[] bits = params[1].split("\\.", 2);
		if (bits.length != 2)
			throw new ChoobBadSyntaxError();

		final String pluginName = bits[0];
		final String optionName = bits[1];

		try
		{
			irc.sendContextReply(mes, (String[])mods.plugin.callAPI("Help", "GetHelp", pluginName + ".option." + optionName ));
		}
		catch (final ChoobNoSuchCallException e)
		{
			irc.sendContextReply(mes, "Sorry, the Help plugin isn't loaded!");
		}
	}

	public String apiGetGeneralOption( final String optionName )
	{
		return apiGetGeneralOption(optionName, null);
	}

	public String apiGetGeneralOption( final String optionName, final String defult )
	{
		String pluginName = ChoobThread.getPluginName(1);
		if (pluginName == null)
			pluginName = "*Choob*"; // Hopefully an invalid plugin name. :)

		final List<GeneralOption> options = mods.odb.retrieve( GeneralOption.class,
			  "WHERE optionName = '" + mods.odb.escapeString(optionName) + "' AND"
			+ " pluginName = '" + mods.odb.escapeString(pluginName) + "'");

		if (options.size() == 0)
			return defult;
		return options.get(0).optionValue;
	}

	public String apiGetUserOption( final String nickName, final String optionName )
	{
		return apiGetUserOption( nickName, optionName, null );
	}

	public String apiGetUserOption( final String nickName, final String optionName, final String defult )
	{
		String userName = mods.security.getUserAuthName(nickName);
		userName = mods.security.getRootUser( mods.nick.getBestPrimaryNick(userName));
		if (userName == null)
			userName = mods.security.getUserAuthName(nickName);

		String pluginName = ChoobThread.getPluginName(1);
		if (pluginName == null)
			pluginName = "*Choob*"; // Hopefully an invalid plugin name. :)

		final List<UserOption> options = mods.odb.retrieve( UserOption.class,
			  "WHERE optionName = '" + mods.odb.escapeString(optionName) + "' AND"
			+ " userName = '" + mods.odb.escapeString(userName) + "' AND"
			+ " pluginName = '" + mods.odb.escapeString(pluginName) + "'");

		if (options.size() == 0)
			return defult;
		return options.get(0).optionValue;
	}

	public void apiSetGeneralOption( final String optionName, final String value )
	{
		String pluginName = ChoobThread.getPluginName(1);
		if (pluginName == null)
			pluginName = "*Choob*"; // Hopefully an invalid plugin name. :)

		final List<GeneralOption> options = mods.odb.retrieve( GeneralOption.class,
			  "WHERE optionName = '" + mods.odb.escapeString(optionName) + "' AND"
			+ " pluginName = '" + mods.odb.escapeString(pluginName) + "'");

		if (options.size() == 0)
		{
			if (value == null)
				return;
			final GeneralOption option = new GeneralOption();
			option.pluginName = pluginName;
			option.optionName = optionName;
			option.optionValue = value;
			mods.odb.save(option);
		}
		else
		{
			final GeneralOption option = options.get(0);
			if (value == null)
			{
				mods.odb.delete(option);
			}
			else
			{
				option.optionValue = value;
				mods.odb.update(option);
			}
		}
	}

	// WARNING: Run getBestPrimaryNick on this, and check NickServ first in your plugin!
	public void apiSetUserOption( String userName, final String optionName, final String value )
	{
		final String rootUser = mods.security.getRootUser(mods.security.getUserAuthName(userName));
		if (rootUser != null)
			userName = rootUser;

		String pluginName = ChoobThread.getPluginName(1);
		if (pluginName == null)
			pluginName = "*Choob*"; // Hopefully an invalid plugin name. :)

		final List<UserOption> options = mods.odb.retrieve( UserOption.class,
			  "WHERE optionName = '" + mods.odb.escapeString(optionName) + "' AND"
			+ " userName = '" + mods.odb.escapeString(userName) + "' AND"
			+ " pluginName = '" + mods.odb.escapeString(pluginName) + "'");

		if (options.size() == 0)
		{
			if (value == null)
				return;
			final UserOption option = new UserOption();
			option.pluginName = pluginName;
			option.userName = userName;
			option.optionName = optionName;
			option.optionValue = value;
			mods.odb.save(option);
		}
		else
		{
			final UserOption option = options.get(0);
			if (value == null)
			{
				mods.odb.delete(option);
			}
			else
			{
				option.optionValue = value;
				mods.odb.update(option);
			}
		}
	}

	private String[] _getUserOptions( final String pluginName )
	{
		try
		{
			return (String[])mods.plugin.callGeneric(pluginName, "options", "User");
		}
		catch (final ChoobNoSuchCallException e)
		{
			return null;
		}
	}

	private String[] _getUserOptionDefaults( final String pluginName )
	{
		try
		{
			return (String[])mods.plugin.callGeneric(pluginName, "options", "UserDefaults");
		}
		catch (final ChoobNoSuchCallException e)
		{
			return null;
		}
	}

	private String[] _getGeneralOptions( final String pluginName )
	{
		try
		{
			return (String[])mods.plugin.callGeneric(pluginName, "options", "General");
		}
		catch (final ChoobNoSuchCallException e)
		{
			return null;
		}
	}

	private String[] _getGeneralOptionDefaults( final String pluginName )
	{
		try
		{
			return (String[])mods.plugin.callGeneric(pluginName, "options", "GeneralDefaults");
		}
		catch (final ChoobNoSuchCallException e)
		{
			return null;
		}
	}

	private String _checkUserOption( final String pluginName, final String optionName, final String optionValue, final String userName )
	{
		try
		{
			if( ((Boolean)mods.plugin.callGeneric(pluginName, "option", "CheckUser", optionName, optionValue, userName)).booleanValue() )
				return null;
			return "Invalid option value!";
		}
		catch (final ChoobNoSuchCallException e)
		{
			try
			{
				if( ((Boolean)mods.plugin.callGeneric(pluginName, "option", "CheckUser" + optionName, optionValue, userName)).booleanValue() )
					return null;
				return "Invalid option value!";
			}
			catch (final ChoobNoSuchCallException f)
			{
				return null;
			}
		}
		catch (final ClassCastException e)
		{
			return "Invalid option check return value!";
		}
	}

	private String _checkGeneralOption( final String pluginName, final String optionName, final String optionValue )
	{
		try
		{
			if( ((Boolean)mods.plugin.callGeneric(pluginName, "option", "CheckGeneral", optionName, optionValue)).booleanValue() )
				return null;
			return "Invalid option value!";
		}
		catch (final ChoobNoSuchCallException e)
		{
			try
			{
				if( ((Boolean)mods.plugin.callGeneric(pluginName, "option", "CheckGeneral" + optionName, optionValue)).booleanValue() )
					return null;
				return "Invalid option value!";
			}
			catch (final ChoobNoSuchCallException f)
			{
				return null;
			}
		}
		catch (final ClassCastException e)
		{
			return "Invalid option check return value!";
		}
	}
}
