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
	Modules mods;
	IRCInterface irc;

	public Options(Modules mods, IRCInterface irc)
	{
		this.irc = irc;
		this.mods = mods;
	}

	public String[] helpCommandSet = {
		"Set an option for a plugin for just you.",
		"<Plugin> <Name>=<Value>",
		"<Plugin> is the name of the plugin to set for",
		"<Name> is the name of the option to set",
		"<Value> is the value to set the option to"
	};
	public void commandSet( Message mes )
	{
		List<String> params = mods.util.getParams( mes, 2 );

		String nickName = mes.getNick();

		mods.security.checkNS(nickName);
		String userName = mods.security.getRootUser( mods.nick.getBestPrimaryNick( nickName ) );

		if (userName == null)
		{
			irc.sendContextReply( mes, "Sorry, you need to register your username with the bot first. See Help.Help Security.AddUser." );
			return;
		}

		// Parse input
		if (params.size() != 3)
		{
			irc.sendContextReply( mes, "Syntax: Options.Set <Plugin> <Name>=<Value>" );
			return;
		}

		String[] vals = params.get(2).split("=");
		if (vals.length != 2)
		{
			irc.sendContextReply( mes, "Syntax: Options.Set <Plugin> <Name>=<Value>" );
			return;
		}

		// OK, have an option.
		List<UserOption> options = mods.odb.retrieve( UserOption.class,
			  "WHERE optionName = '" + vals[0].replaceAll("(['\\\\])", "\\\\$1") + "' AND "
			+ " userName = '" + userName.replaceAll("(['\\\\])", "\\\\$1") + "' AND "
			+ " pluginName = '" + params.get(1).replaceAll("(['\\\\])", "\\\\$1") + "'");

		if ( options.size() >= 1 )
		{
			UserOption option = options.get(0);
			option.optionValue = vals[1];
			mods.odb.update(option);
		}
		else
		{
			UserOption option = new UserOption();
			option.pluginName = params.get(1);
			option.userName = userName;
			option.optionName = vals[0];
			option.optionValue = vals[1];
			mods.odb.save(option);
		}

		irc.sendContextReply( mes, "OK, set " + vals[0] + " in " + params.get(1) + " for " + userName + " to " + vals[1] + "." );
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
		List<String> params = mods.util.getParams( mes, 2 );

		// Parse input
		if (params.size() != 3)
		{
			irc.sendContextReply( mes, "Syntax: Options.Set <Plugin> <Name>=<Value>" );
			return;
		}

		String[] vals = params.get(2).split("=");
		if (vals.length != 2)
		{
			irc.sendContextReply( mes, "Syntax: Options.Set <Plugin> <Name>=<Value>" );
			return;
		}

		// TODO - make plugin owners always able to set this. Or something.
		mods.security.checkNickPerm(new ChoobPermission("plugin.options.set." + params.get(1)), mes.getNick());

		// OK, have an option.
		List<GeneralOption> options = mods.odb.retrieve( GeneralOption.class,
			  "WHERE optionName = '" + vals[0].replaceAll("(['\\\\])", "\\\\$1") + "' AND "
			+ " pluginName = '" + params.get(1).replaceAll("(['\\\\])", "\\\\$1") + "'");

		if ( options.size() >= 1 )
		{
			GeneralOption option = options.get(0);
			option.optionValue = vals[1];
			mods.odb.update(option);
		}
		else
		{
			GeneralOption option = new GeneralOption();
			option.pluginName = params.get(1);
			option.optionName = vals[0];
			option.optionValue = vals[1];
			mods.odb.save(option);
		}

		irc.sendContextReply( mes, "OK, set " + vals[0] + " in " + params.get(1) + " to " + vals[1] + "." );
	}

	public String[] helpCommandGet = {
		"Get your personal option values for plugins."
	};
	public void commandGet( Message mes )
	{
		String userName = mods.security.getRootUser( mods.nick.getBestPrimaryNick( mes.getNick() ) );

		if (userName == null)
			userName = mes.getNick();

		List<UserOption> options = mods.odb.retrieve( UserOption.class,
			  "WHERE userName = '" + userName.replaceAll("(['\\\\])", "\\\\$1") + "'");

		if (options.size() == 0)
		{
			irc.sendContextReply( mes, "No options set!" );
		}
		else
		{
			StringBuilder out = new StringBuilder("Options:");
			for(UserOption option: options)
			{
				out.append(" " + option.pluginName + "." + option.optionName + "=" + option.optionValue);
			}
			irc.sendContextReply( mes, out.toString() );
		}
	}

	public String[] helpCommandGetGeneral = {
		"Get all global option values for all plugins."
	};
	public void commandGetGeneral( Message mes )
	{
		// TODO - make plugin owners always able to set this. Or something.
		mods.security.checkNickPerm(new ChoobPermission("plugin.options.get"), mes.getNick());

		List<GeneralOption> options = mods.odb.retrieve( GeneralOption.class, "WHERE 1");

		if (options.size() == 0)
		{
			irc.sendContextReply( mes, "No options set!" );
		}
		else
		{
			StringBuilder out = new StringBuilder("Options:");
			for(GeneralOption option: options)
			{
				out.append(" " + option.pluginName + "." + option.optionName + "=" + option.optionValue);
			}
			irc.sendContextReply( mes, out.toString() );
		}
	}

	public String apiGetGeneralOption( String optionName )
	{
		String pluginName = ChoobThread.getPluginName(1);
		if (pluginName == null)
			pluginName = "*Choob*"; // Hopefully an invalid plugin name. :)

		List<GeneralOption> options = mods.odb.retrieve( GeneralOption.class,
			  "WHERE optionName = '" + optionName.replaceAll("(['\\\\])", "\\\\$1") + "' AND"
			+ " pluginName = '" + pluginName.replaceAll("(['\\\\])", "\\\\$1") + "'");

		if (options.size() == 0)
			return null;
		else
			return options.get(0).optionValue;
	}

	public String apiGetUserOption( String nickName, String optionName )
	{
		String userName = mods.security.getRootUser( mods.nick.getBEstPrimaryNick( nickName ) );
		if (userName == null)
			userName = nickName;

		String pluginName = ChoobThread.getPluginName(1);
		if (pluginName == null)
			pluginName = "*Choob*"; // Hopefully an invalid plugin name. :)

		List<UserOption> options = mods.odb.retrieve( UserOption.class,
			  "WHERE optionName = '" + optionName.replaceAll("(['\\\\])", "\\\\$1") + "' AND"
			+ " userName = '" + userName.replaceAll("(['\\\\])", "\\\\$1") + "' AND"
			+ " pluginName = '" + pluginName.replaceAll("(['\\\\])", "\\\\$1") + "'");

		if (options.size() == 0)
			return null;
		else
			return options.get(0).optionValue;
	}

	public void apiSetGeneralOption( String optionName, String value )
	{
		String pluginName = ChoobThread.getPluginName(1);
		if (pluginName == null)
			pluginName = "*Choob*"; // Hopefully an invalid plugin name. :)

		List<GeneralOption> options = mods.odb.retrieve( GeneralOption.class,
			  "WHERE optionName = '" + optionName.replaceAll("(['\\\\])", "\\\\$1") + "' AND"
			+ " pluginName = '" + pluginName.replaceAll("(['\\\\])", "\\\\$1") + "'");

		if (options.size() == 0)
		{
			GeneralOption option = new GeneralOption();
			option.pluginName = pluginName;
			option.optionName = optionName;
			option.optionValue = value;
			mods.odb.save(option);
		}
		else
		{
			GeneralOption option = options.get(0);
			option.optionValue = value;
			mods.odb.update(option);
		}
	}

	public void apiSetUserOption( String nickName, String optionName, String value )
	{
		String userName = mods.security.getRootUser( mods.nick.getBestPrimaryNick( nickName ) );
		if (userName == null)
			userName = nickName;

		String pluginName = ChoobThread.getPluginName(1);
		if (pluginName == null)
			pluginName = "*Choob*"; // Hopefully an invalid plugin name. :)

		List<UserOption> options = mods.odb.retrieve( UserOption.class,
			  "WHERE optionName = '" + optionName.replaceAll("(['\\\\])", "\\\\$1") + "' AND"
			+ " userName = '" + userName.replaceAll("(['\\\\])", "\\\\$1") + "' AND"
			+ " pluginName = '" + pluginName.replaceAll("(['\\\\])", "\\\\$1") + "'");

		if (options.size() == 0)
		{
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
			option.optionValue = value;
			mods.odb.update(option);
		}
	}
}
