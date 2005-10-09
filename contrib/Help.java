import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;

/**
 * Choob help plugin
 *
 * @author bucko
 *
 * This module works by calling the generic function of type "help", name
 * (the command to be queried) and string parameter of anything after the
 * command name. The call is expected to return a non-null object. If the
 * return value is a string array, elements should be in the order: Brief
 * description, Syntax, Parameter explanation 1, Parameter explanation 2.
 * Anything other than a String array just has toString() called upon it.
 *
 * In the special case of the topics help, the array should simply be an
 * array of valid topics.
 */

public class Help
{
	Modules mods;
	IRCInterface irc;

	public Help(Modules mods, IRCInterface irc)
	{
		this.irc = irc;
		this.mods = mods;
	}

	public String[] helpTopics = { "Help", "Plugins" };

	/**
	 * Get help!
	 */
	public String[] helpHelp = {
		"Get help on a topic.",
		"Help.Help <topic> <params>",
		"<topic> is either a plugin name or of the form <plugin>.<name>",
		"<params> is a parameter to pass to the help."
	};
	public void commandHelp( Message mes ) throws ChoobException
	{
		List<String> params = mods.util.getParams( mes, 2 );
		String topic, topicParams;
		if (params.size() == 1)
		{
			topic = "help";
			topicParams = "";
		}
		else if (params.size() == 2)
		{
			topic = params.get(1);
			topicParams = "";
		}
		else
		{
			topic = params.get(1);
			topicParams = params.get(2);
		}

		Matcher ma = Pattern.compile("(\\w+)(?:\\.(\\w+))?").matcher(topic);
		if (!ma.matches())
		{
			irc.sendContextReply( mes, "Help topics must be of the form <name> or <plugin>.<name>." );
			return;
		}

		String plugin = ma.group(1);
		topic = ma.group(2);

		String help;
		try
		{
			if (topic != null && !topic.toLowerCase().equals("topics"))
			{
				Object ret = callMethod(plugin, topic, topicParams);
				if (ret == null)
				{
					irc.sendContextReply( mes, "Gah! That topic had help, but the help seems to be broken." );
					return;
				}
				else if (ret instanceof String[])
				{
					String[] helpArr = (String[])ret;
					if (helpArr.length < 2)
					{
						irc.sendContextReply( mes, "Gah! That topic had help, but the help seems to be broken (it returned an array of 1 string)." );
						return;
					}
					StringBuffer buf = new StringBuffer();
					buf.append( helpArr[0] );
					buf.append( " Syntax: " );
					buf.append( helpArr[1] );
					if (helpArr.length > 2)
					{
						buf.append(" where");
						for(int i=2; i<helpArr.length; i++)
						{
							buf.append(" ");
							if (i != 2)
								buf.append("and ");
							buf.append(helpArr[i]);
						}
					}
					help = buf.toString();
				}
				else
				{
					help = ret.toString();
				}
			}
			else
			{
				Object ret = callMethod(plugin, "Topics", topicParams);
				if (ret == null)
				{
					irc.sendContextReply( mes, "Gah! That plugin had help, but the help seems to be broken." );
					return;
				}
				else if (ret instanceof String[])
				{
					StringBuffer buf = new StringBuffer("Help topics:");
					String[] topics = (String[])ret;
					for(int i=0; i<topics.length; i++)
						buf.append(" " + topics[i]);
					help = buf.toString();
				}
				else
				{
					help = ret.toString();
				}
			}
		}
		catch (NoSuchPluginException e)
		{
			System.out.println(e.toString());
			irc.sendContextReply( mes, "Topic " + topic + " in plugin " + plugin + " didn't exist!" );
			return;
		}
		irc.sendContextReply( mes, help );
	}

	public String[] helpPlugins = {
		"Get a list of loaded plugins.",
		"Help.Plugins"
	};
	public void commandPlugins( Message mes )
	{
		String[] plugins = mods.plugin.plugins();

		StringBuffer buf = new StringBuffer("Plugins: ");
		for(int i=0; i<plugins.length; i++)
		{
			if (i != 0)
				buf.append(", ");
			if (i == plugins.length - 1)
				buf.append("and ");
			buf.append(plugins[i]);
		}
		buf.append(".");

		irc.sendContextReply(mes, buf.toString());
	}

	public String[] helpCommands = {
		"Get a list of commands in a plugin.",
		"Help.Commands <plugin>",
		"<plugin> is the name of a loaded plugin."
	};
	public void commandCommands( Message mes )
	{
		String plugin = mods.util.getParamString(mes);
		String[] commands = mods.plugin.commands(plugin);

		if (commands == null)
		{
			irc.sendContextReply(mes, "That plugin doesn't exist!");
		}
		else if (commands.length == 0)
		{
			irc.sendContextReply(mes, "No commands in plugin " + plugin + ".");
		}
		else if (commands.length == 1)
		{
			irc.sendContextReply(mes, "1 command in plugin " + plugin + ": " + commands[0] + ".");
		}
		else if (commands.length == 2)
		{
			irc.sendContextReply(mes, "2 commands in plugin " + plugin + ": " + commands[0] + " and " + commands[1] + ".");
		}
		else
		{
			StringBuffer buf = new StringBuffer("" + commands.length);
			buf.append(" commands in plugin ");
			buf.append(plugin);
			buf.append(": ");
			for(int i=0; i<commands.length; i++)
			{
				if (i != 0)
					buf.append(", ");
				if (i == commands.length - 1)
					buf.append("and ");
				buf.append(commands[i]);
			}
			buf.append(".");

			irc.sendContextReply(mes, buf.toString());
		}
	}

	private Object callMethod(String plugin, String topic, String param) throws ChoobException
	{
		try
		{
			return mods.plugin.callGeneric(plugin, "help", topic, param);
		}
		catch (NoSuchPluginException e)
		{
			return mods.plugin.callGeneric(plugin, "help", topic);
		}
	}


}
