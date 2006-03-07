import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.text.*;

/**
 * Options plugin - allows other plugins to access options which users can set.
 *
 * @author bucko
 */

public class UserOption
{
	public int id;
	public String pluginName;
	public String userName;
	public String optionName;
	public String optionValue;
}

public class GeneralOption
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

	public Options(Modules mods, IRCInterface irc)
	{
		this.irc = irc;
		this.mods = mods;
	}

	public String[] helpCommandList = {
		"List available user plugin options.",
		"[ <Plugin> ]",
		"<Plugin> is the optional name of the plugin to list for (default: All)"
	};
	public void commandList( Message mes )
	{
		String[] params = mods.util.getParamArray( mes );

		// Parse input
		if (params.length > 2)
		{
			throw new ChoobBadSyntaxError();
		}
		else if (params.length == 1)
		{
			// List all.
			StringBuilder output = new StringBuilder("Options: ");

			String[] plugins = mods.plugin.getLoadedPlugins();
			boolean first = true;
			for(int j=0; j<plugins.length; j++)
			{
				String[] options = _getUserOptions(plugins[j]);
				String[] defaults = _getUserOptionDefaults(plugins[j]);

				if (options == null)
					continue;

				if (!first)
					output.append("; ");
				first = false;
				output.append(plugins[j] + ": ");

				for(int i=0; i<options.length; i++)
				{
					output.append(options[i]);
					if (defaults != null && defaults[i] != null)
						output.append(" (default: " + defaults[i] + ")");
					if (i == options.length - 2)
						output.append(" and ");
					else if (i != options.length - 1)
						output.append(", ");
				}
			}
			output.append(".");
			irc.sendContextReply(mes, output.toString());
		}
		else
		{
			// Passed plugin name.
			String pluginName = params[1];

			String[] options = _getUserOptions(pluginName);
			String[] defaults = _getUserOptionDefaults(pluginName);

			if (options == null)
			{
				irc.sendContextReply(mes, "Either plugin " + pluginName + " did not exist, or it has no options!");
				return;
			}

			StringBuilder output = new StringBuilder("Options for " + pluginName + ": ");
			for(int i=0; i<options.length; i++)
			{
				output.append(options[i]);
				if (defaults != null && defaults[i] != null)
					output.append(" (default: " + defaults[i] + ")");
				if (i == options.length - 2)
					output.append(" and ");
				else if (i != options.length - 1)
					output.append(", ");
			}
			output.append(".");
			irc.sendContextReply(mes, output.toString());
		}
	}

	public String[] helpCommandListGeneral = {
		"List available general plugin options.",
		"[ <Plugin> ]",
		"<Plugin> is the optional name of the plugin to list for (default: All)"
	};
	public void commandListGeneral( Message mes )
	{
		String[] params = mods.util.getParamArray( mes );

		// Parse input
		if (params.length > 2)
		{
			throw new ChoobBadSyntaxError();
		}
		else if (params.length == 1)
		{
			// List all.
			StringBuilder output = new StringBuilder("Options: ");

			String[] plugins = mods.plugin.getLoadedPlugins();
			boolean first = true;
			for(int j=0; j<plugins.length; j++)
			{
				String[] options = _getGeneralOptions(plugins[j]);
				String[] defaults = _getGeneralOptionDefaults(plugins[j]);

				if (options == null)
					continue;

				if (!first)
					output.append("; ");
				first = false;
				output.append(plugins[j] + ": ");

				for(int i=0; i<options.length; i++)
				{
					output.append(options[i]);
					if (defaults != null && defaults[i] != null)
						output.append(" (default: " + defaults[i] + ")");
					if (i == options.length - 2)
						output.append(" and ");
					else if (i != options.length - 1)
						output.append(", ");
				}
			}
			output.append(".");
			irc.sendContextReply(mes, output.toString());
		}
		else
		{
			// Passed plugin name.
			String pluginName = params[1];

			String[] options = _getGeneralOptions(pluginName);
			String[] defaults = _getGeneralOptionDefaults(pluginName);

			if (options == null)
			{
				irc.sendContextReply(mes, "Either plugin " + pluginName + " did not exist, or it has no options!");
				return;
			}

			StringBuilder output = new StringBuilder("Options for " + pluginName + ": ");
			for(int i=0; i<options.length; i++)
			{
				output.append(options[i]);
				if (defaults != null && defaults[i] != null)
					output.append(" (default: " + defaults[i] + ")");
				if (i == options.length - 2)
					output.append(" and ");
				else if (i != options.length - 1)
					output.append(", ");
			}
			output.append(".");
			irc.sendContextReply(mes, output.toString());
		}
	}

	public String[] helpCommandSet = {
		"Set an option for a plugin for just you.",
		"<Plugin> <Name>=[<Value>]",
		"<Plugin> is the name of the plugin to set for",
		"<Name> is the name of the option to set",
		"<Value> is the value to set the option to (omit to unset)"
	};
	public void commandSet( Message mes )
	{
		String[] params = mods.util.getParamArray( mes, 2 );

		String nickName = mes.getNick();

		mods.security.checkNS(mes);
		String userName = mods.security.getRootUser( nickName );

		if (userName == null)
		{
			String primaryNick = mods.nick.getBestPrimaryNick( nickName );
			String rootNick = mods.security.getRootUser(primaryNick);
			if (nickName.equals( primaryNick ))
				// They can't have registered their nick at all.
				irc.sendContextReply( mes, "Sorry, you need to register your username with the bot first. See Help.Help Security.AddUser." );

			else if ( rootNick != null )
				// Registered but not linked.
				irc.sendContextReply( mes, "Sorry, you need to link your username with " + rootNick + " first. See Help.Help Security.UsingLink." );

			else
				// Not registered, and not primary.
				irc.sendContextReply( mes, "Sorry, you need to register your username (" + primaryNick + ") with the bot first. See Help.Help Security.AddUser." );

			return;
		}

		// Parse input
		if (params.length != 3)
		{
			throw new ChoobBadSyntaxError();
		}

		String[] vals = params[2].split("=", -1);
		if (vals.length != 2)
		{
			throw new ChoobBadSyntaxError();
		}

		// Check the option is OK
		if (vals[1].length() > 0)
		{
			String err = _checkUserOption( params[1], vals[0].toLowerCase(), vals[1], userName );
			if (err != null)
			{
				irc.sendContextReply( mes, "Could not set the option! Error: " + err );
				return;
			}
		}
		else
		{
			String[] opts = _getUserOptions( params[1] );
			boolean found = false;
			if (opts != null)
			{
				String opt = vals[0].toLowerCase();
				for(int i=0; i<opts.length; i++)
				{
					if (opts[i].toLowerCase().equals(opt))
						found = true;
				}
			}
			if (!found)
			{
				irc.sendContextReply( mes, "Unknown option: " + params[1] + "." + vals[0] );
				return;
			}
		}

		// OK, have an option.
		List<UserOption> options = mods.odb.retrieve( UserOption.class,
			  "WHERE optionName = '" + mods.odb.escapeString(vals[0]) + "' AND "
			+ " userName = '" + mods.odb.escapeString(userName) + "' AND "
			+ " pluginName = '" + mods.odb.escapeString(params[1]) + "'");

		if ( options.size() >= 1 )
		{
			UserOption option = options.get(0);
			if (vals[1].length() > 0)
			{
				option.optionValue = vals[1];
				mods.odb.update(option);
			}
			else
			{
				mods.odb.delete(option);
			}
		}
		else if (vals[1].length() > 0)
		{
			UserOption option = new UserOption();
			option.pluginName = params[1];
			option.userName = userName;
			option.optionName = vals[0];
			option.optionValue = vals[1];
			mods.odb.save(option);
		}

		irc.sendContextReply( mes, "OK, set " + vals[0] + " in " + params[1] + " for " + userName + " to '" + vals[1] + "'." );
	}

	public String[] helpCommandSetGeneral = {
		"Set an option for a plugin that will apply to the plugin itself.",
		"<Plugin> <Name>=<Value>",
		"<Plugin> is the name of the plugin to set for",
		"<Name> is the name of the option to set",
		"<Value> is the value to set the option to"
	};
	public void commandSetGeneral( Message mes )
	{
		String[] params = mods.util.getParamArray( mes, 2 );

		// Parse input
		if (params.length != 3)
		{
			throw new ChoobBadSyntaxError();
		}

		String[] vals = params[2].split("=", -1);
		if (vals.length != 2)
		{
			throw new ChoobBadSyntaxError();
		}

		// TODO - make plugin owners always able to set this. Or something.
		mods.security.checkNickPerm(new ChoobPermission("plugin.options.set." + params[1]), mes);

		if (vals[1].length() > 0)
		{
			String err = _checkGeneralOption( params[1], vals[0].toLowerCase(), vals[1] );
			if (err != null)
			{
				irc.sendContextReply( mes, "Could not set the option! Error: " + err );
				return;
			}
		}
		else
		{
			String[] opts = _getGeneralOptions( params[1] );
			boolean found = false;
			if ( opts != null )
			{
				String opt = vals[0].toLowerCase();
				for(int i=0; i<opts.length; i++)
				{
					if (opts[i].toLowerCase().equals(opt))
						found = true;
				}
			}
			if (!found)
			{
				irc.sendContextReply( mes, "Unknown option: " + params[1] + "." + vals[0] );
				return;
			}
		}

		// OK, have an option.
		List<GeneralOption> options = mods.odb.retrieve( GeneralOption.class,
			  "WHERE optionName = '" + mods.odb.escapeString(vals[0]) + "' AND "
			+ " pluginName = '" + mods.odb.escapeString(params[1]) + "'");

		if ( options.size() >= 1 )
		{
			GeneralOption option = options.get(0);
			if (vals[1].length() > 0)
			{
				option.optionValue = vals[1];
				mods.odb.update(option);
			}
			else
			{
				mods.odb.delete(option);
			}
		}
		else if (vals[1].length() > 0)
		{
			GeneralOption option = new GeneralOption();
			option.pluginName = params[1];
			option.optionName = vals[0];
			option.optionValue = vals[1];
			mods.odb.save(option);
		}

		irc.sendContextReply( mes, "OK, set " + vals[0] + " in " + params[1] + " to '" + vals[1] + "'." );
	}

	public String[] helpCommandGet = {
		"Get your personal option values for plugins.",
		"[ <Plugin> ]",
		"<Plugin> is the plugin to limit options to (default: All)"
	};
	public void commandGet( Message mes )
	{
		String[] params = mods.util.getParamArray(mes);

		String pluginName;
		if (params.length > 2)
		{
			throw new ChoobBadSyntaxError();
		}
		else if (params.length == 2)
		{
			pluginName = params[1];
		}
		else
		{
			pluginName = null;
		}

		String userName = mods.security.getRootUser( mods.nick.getBestPrimaryNick( mes.getNick() ) );

		if (userName == null)
			userName = mes.getNick();

		List<UserOption> options;
		if (pluginName != null)
			options = mods.odb.retrieve( UserOption.class,
			  "WHERE userName = '" + mods.odb.escapeString(userName) + "'"
			+ " AND pluginName = '" + mods.odb.escapeString(pluginName) + "'");
		else
			options = mods.odb.retrieve( UserOption.class,
			  "WHERE userName = '" + mods.odb.escapeString(userName) + "'");

		if (options.size() == 0)
		{
			irc.sendContextReply( mes, "No options set!" );
		}
		else
		{
			StringBuilder out = new StringBuilder();
			if (pluginName == null)
				out.append("Options:");
			else
				out.append("Options for " + pluginName + ":");

			for(UserOption option: options)
			{
				if (pluginName == null)
					out.append(" " + option.pluginName + "." + option.optionName + "=" + option.optionValue);
				else
					out.append(" " + option.optionName + "=" + option.optionValue);
			}
			irc.sendContextReply( mes, out.toString() );
		}
	}

	public String[] helpCommandGetGeneral = {
		"Get all global option values for all plugins.",
		"<Plugin>",
		"<Plugin> is the plugin to limit options to (warning: some plugins contain passwords!)"
	};
	public void commandGetGeneral( Message mes )
	{
		String[] params = mods.util.getParamArray(mes);

		String pluginName;
		if (params.length != 2)
		{
			if (params.length == 1 && mes instanceof PrivateEvent)
			{
				pluginName = null;
			}
			else
			{
				throw new ChoobBadSyntaxError();
			}
		}
		else
		{
			pluginName = params[1];
		}

		// TODO - make plugin owners always able to set this. Or something.
		mods.security.checkNickPerm(new ChoobPermission("plugin.options.get"), mes);

		List<GeneralOption> options;
		if (pluginName != null)
			options = mods.odb.retrieve( GeneralOption.class,
			  "WHERE pluginName = '" + mods.odb.escapeString(pluginName) + "'");
		else
			options = mods.odb.retrieve( GeneralOption.class, "WHERE 1");

		if (options.size() == 0)
		{
			irc.sendContextReply( mes, "No options set!" );
		}
		else
		{
			StringBuilder out = new StringBuilder();
			if (pluginName == null)
				out.append("Options:");
			else
				out.append("Options for " + pluginName + ":");

			for(GeneralOption option: options)
			{
				if (pluginName == null)
					out.append(" " + option.pluginName + "." + option.optionName + "=" + option.optionValue);
				else
					out.append(" " + option.optionName + "=" + option.optionValue);
			}
			irc.sendContextReply( mes, out.toString() );
		}
	}

	public String[] helpCommandHelp = {
		"Get help on an option.",
		"<Plugin> <Name>",
		"<Plugin> is the name of the plugin the option lives in",
		"<Name> is the name of the option"
	};
	public void commandHelp( Message mes )
	{
		String[] params = mods.util.getParamArray(mes);

		String pluginName, optionName;
		if (params.length == 2)
		{
			String[] bits = params[1].split("\\.");
			if (bits.length != 2)
				throw new ChoobBadSyntaxError();

			pluginName = bits[0];
			optionName = bits[1];
		}
		else if (params.length == 3)
		{
			pluginName = params[1];
			optionName = params[2];
		}
		else
			throw new ChoobBadSyntaxError();

		try
		{
			irc.sendContextReply(mes, (String[])mods.plugin.callAPI("Help", "GetHelp", pluginName + ".option." + optionName ));
		}
		catch (ChoobNoSuchCallException e)
		{
			irc.sendContextReply(mes, "Sorry, the help plugin isn't loaded!");
		}
	}

	public String apiGetGeneralOption( String optionName )
	{
		return apiGetGeneralOption(optionName, null);
	}

	public String apiGetGeneralOption( String optionName, String defult )
	{
		String pluginName = ChoobThread.getPluginName(1);
		if (pluginName == null)
			pluginName = "*Choob*"; // Hopefully an invalid plugin name. :)

		List<GeneralOption> options = mods.odb.retrieve( GeneralOption.class,
			  "WHERE optionName = '" + mods.odb.escapeString(optionName) + "' AND"
			+ " pluginName = '" + mods.odb.escapeString(pluginName) + "'");

		if (options.size() == 0)
			return defult;
		else
			return options.get(0).optionValue;
	}

	public String apiGetUserOption( String nickName, String optionName )
	{
		return apiGetUserOption( nickName, optionName, null );
	}

	public String apiGetUserOption( String nickName, String optionName, String defult )
	{
		String userName = mods.security.getRootUser( mods.nick.getBestPrimaryNick( nickName ) );
		if (userName == null)
			userName = nickName;

		String pluginName = ChoobThread.getPluginName(1);
		if (pluginName == null)
			pluginName = "*Choob*"; // Hopefully an invalid plugin name. :)

		List<UserOption> options = mods.odb.retrieve( UserOption.class,
			  "WHERE optionName = '" + mods.odb.escapeString(optionName) + "' AND"
			+ " userName = '" + mods.odb.escapeString(userName) + "' AND"
			+ " pluginName = '" + mods.odb.escapeString(pluginName) + "'");

		if (options.size() == 0)
			return defult;
		else
			return options.get(0).optionValue;
	}

	public void apiSetGeneralOption( String optionName, String value )
	{
		String pluginName = ChoobThread.getPluginName(1);
		if (pluginName == null)
			pluginName = "*Choob*"; // Hopefully an invalid plugin name. :)

		List<GeneralOption> options = mods.odb.retrieve( GeneralOption.class,
			  "WHERE optionName = '" + mods.odb.escapeString(optionName) + "' AND"
			+ " pluginName = '" + mods.odb.escapeString(pluginName) + "'");

		if (options.size() == 0)
		{
			if (value == null)
				return;
			GeneralOption option = new GeneralOption();
			option.pluginName = pluginName;
			option.optionName = optionName;
			option.optionValue = value;
			mods.odb.save(option);
		}
		else
		{
			GeneralOption option = options.get(0);
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
	public void apiSetUserOption( String userName, String optionName, String value )
	{
		String rootUser = mods.security.getRootUser( userName );
		if (rootUser != null)
			userName = rootUser;

		String pluginName = ChoobThread.getPluginName(1);
		if (pluginName == null)
			pluginName = "*Choob*"; // Hopefully an invalid plugin name. :)

		List<UserOption> options = mods.odb.retrieve( UserOption.class,
			  "WHERE optionName = '" + mods.odb.escapeString(optionName) + "' AND"
			+ " userName = '" + mods.odb.escapeString(userName) + "' AND"
			+ " pluginName = '" + mods.odb.escapeString(pluginName) + "'");

		if (options.size() == 0)
		{
			if (value == null)
				return;
			UserOption option = new UserOption();
			option.pluginName = pluginName;
			option.userName = userName;
			option.optionName = optionName;
			option.optionValue = value;
			mods.odb.save(option);
		}
		else
		{
			UserOption option = options.get(0);
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

	private String[] _getUserOptions( String pluginName )
	{
		try
		{
			return (String[])mods.plugin.callGeneric(pluginName, "options", "User");
		}
		catch (ChoobNoSuchCallException e)
		{
			return null;
		}
	}

	private String[] _getUserOptionDefaults( String pluginName )
	{
		try
		{
			return (String[])mods.plugin.callGeneric(pluginName, "options", "UserDefaults");
		}
		catch (ChoobNoSuchCallException e)
		{
			return null;
		}
	}

	private String[] _getGeneralOptions( String pluginName )
	{
		try
		{
			return (String[])mods.plugin.callGeneric(pluginName, "options", "General");
		}
		catch (ChoobNoSuchCallException e)
		{
			return null;
		}
	}

	private String[] _getGeneralOptionDefaults( String pluginName )
	{
		try
		{
			return (String[])mods.plugin.callGeneric(pluginName, "options", "GeneralDefaults");
		}
		catch (ChoobNoSuchCallException e)
		{
			return null;
		}
	}

	private String _checkUserOption( String pluginName, String optionName, String optionValue, String userName )
	{
		try
		{
			if( (Boolean)mods.plugin.callGeneric(pluginName, "option", "CheckUser", optionName, optionValue, userName) )
				return null;
			else
				return "Invalid option value!";
		}
		catch (ChoobNoSuchCallException e)
		{
			try
			{
				if( (Boolean)mods.plugin.callGeneric(pluginName, "option", "CheckUser" + optionName, optionValue, userName) )
					return null;
				else
					return "Invalid option value!";
			}
			catch (ChoobNoSuchCallException f)
			{
				return "Unknown option!";
			}
		}
		catch (ClassCastException e)
		{
			return "Invalid option check return value!";
		}
	}

	private String _checkGeneralOption( String pluginName, String optionName, String optionValue )
	{
		try
		{
			if( (Boolean)mods.plugin.callGeneric(pluginName, "option", "CheckGeneral", optionName, optionValue) )
				return null;
			else
				return "Invalid option value!";
		}
		catch (ChoobNoSuchCallException e)
		{
			try
			{
				if( (Boolean)mods.plugin.callGeneric(pluginName, "option", "CheckGeneral" + optionName, optionValue) )
					return null;
				else
					return "Invalid option value!";
			}
			catch (ChoobNoSuchCallException f)
			{
				return "Unknown option!";
			}
		}
		catch (ClassCastException e)
		{
			return "Invalid option check return value!";
		}
	}
}
