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
		"Set an option for a plugin.",
		"<Plugin> <Name>=<Value>",
		"<Plugin> is the name of the plugin to set for",
		"<OptionName> is the name of the option to set",
		"<OptionValue> is the value to set the option to"
	};
	public void commandSet( Message mes )
	{
		List<String> params = mods.util.getParams( mes, 2 );

		String nickName = mes.getNick();

		mods.security.checkNS(nickName);
		String userName = mods.security.getRootUser(nickName);

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
		UserOption option;
		List<UserOption> options = mods.odb.retrieve( UserOption.class,
			  "WHERE optionName = '" + vals[0].replaceAll("(['\\\\])", "\\\\$1") + "' AND "
			+ " userName = '" + userName.replaceAll("(['\\\\])", "\\\\$1") + "' AND "
			+ " pluginName = '" + params.get(1).replaceAll("(['\\\\])", "\\\\$1") + "'");

		if ( options.size() >= 1 )
		{
			option = options.get(0);
			option.optionValue = vals[1];
			mods.odb.update(option);
		}
		else
		{
			option = new UserOption();
			option.pluginName = params.get(1);
			option.userName = userName;
			option.optionName = vals[0];
			option.optionValue = vals[1];
			mods.odb.save(option);
		}

		irc.sendContextReply( mes, "OK, set " + vals[0] + " in " + params.get(1) + " for " + userName + " to " + vals[1] + "." );
	}

	public void commandGet( Message mes )
	{
/*		String nickName = mes.getNick();

		mods.security.checkNS(nickName);*/
		String userName = mods.security.getRootUser( mes.getNick() );

		if (userName == null)
		{
//			irc.sendContextReply( mes, "Sorry, you need to register your username with the bot first. See Help.Help Security.AddUser." );
//			return;
			userName = mes.getNick();
		}

		String pluginName = ChoobThread.getPluginName(1);
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

	public String apiGetUserOption( String nickName, String optionName )
	{
		if (!mods.security.hasNS(nickName))
			return null;

		String userName = mods.security.getRootUser(nickName);
		if (userName == null)
			userName = nickName;

		String pluginName = ChoobThread.getPluginName(1);
		for(int i=0; ChoobThread.getPluginName(i) != null; i++)
			System.out.println("Calling plugin (" + i + "): " + ChoobThread.getPluginName(i));

		List<UserOption> options = mods.odb.retrieve( UserOption.class,
			  "WHERE optionName = '" + optionName.replaceAll("(['\\\\])", "\\\\$1") + "' AND"
			+ " userName = '" + userName.replaceAll("(['\\\\])", "\\\\$1") + "' AND"
			+ " pluginName = '" + pluginName.replaceAll("(['\\\\])", "\\\\$1") + "'");

		if (options.size() == 0)
			return null;
		else
			return options.get(0).optionValue;
	}
}
