import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;
import org.jibble.pircbot.Colors;


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
	public String[] helpCommandHelp = {
		"Get help on a topic.",
		"<topic> [<params>]",
		"<topic>", "is either a plugin name or of the form <plugin>.<name> or <plugin>.<type>.<name>",
		"<params>", "is an optional parameter to pass to the help"
	};
	public void commandHelp( Message mes ) throws ChoobException
	{
		List<String> params = mods.util.getParams( mes, 2 );
		String fullTopic, topicParams;
		if (params.size() == 1)
		{
			fullTopic = "help";
			topicParams = "";
		}
		else if (params.size() == 2)
		{
			fullTopic = params.get(1);
			topicParams = "";
		}
		else
		{
			fullTopic = params.get(1);
			topicParams = params.get(2);
		}

		Matcher topicMatcher = Pattern.compile("([^\\s.]+)(?:\\.([^\\s.]+)(?:\\.([^ .]+))?)?").matcher(fullTopic);
		if (!topicMatcher.matches())
		{
			irc.sendContextReply( mes, "Help topics must be of the form <plugin>, <plugin>.<name>, or <plugin>.<type>.<name>." );
			return;
		}

		String plugin = topicMatcher.group(1);
		String topic, type;
		if (topicMatcher.group(3) == null)
		{
			topic = topicMatcher.group(2);
			type = null;
		}
		else
		{
			topic = topicMatcher.group(3);
			type = topicMatcher.group(2);
		}

		String help;
		try
		{
			if (topic != null && !topic.toLowerCase().equals("topics"))
			{
				// Help on some specific thingy.
				Object ret;
				if (type == null)
				{
					try
					{
						ret = callMethod(plugin, "command" + topic, topicParams);
						type = "command";
					}
					catch (ChoobNoSuchCallException e)
					{
						ret = callMethod(plugin, topic, topicParams);
						type = "";
					}
				}
				else
					ret = callMethod(plugin, type + topic, topicParams);

				if (ret == null)
				{
					irc.sendContextReply( mes, "Gah! That topic had help, but the help seems to be broken." );
					return;
				}
				else if (ret instanceof String[])
				{
					String[] helpArr = (String[])ret;
					if (type.toLowerCase().equals("command"))
					{
						if (helpArr.length < 2)
						{
							help = helpArr[0];
						}
						else
						{
							StringBuilder buf = new StringBuilder();
							buf.append( helpArr[0] );
							buf.append( " Syntax: " );
							buf.append( plugin + "." + topic );
							buf.append( helpArr[1] );
							if (helpArr.length > 2)
							{
								buf.append(" where");
								for(int i=2; i<helpArr.length; i++)
								{
									buf.append(helpArr[i]);
									if (i < helpArr.length - 1)
										buf.append(" and ");
								}
							}
							help = buf.toString();
						}
					}
					else // Assume of type API.
					{
						StringBuilder buf = new StringBuilder();
						for(int i=0; i<helpArr.length; i++)
						{
							buf.append( helpArr[i] );
							if (i < helpArr.length - 1)
								buf.append(" ");
						}
						help = buf.toString();
					}
				}
				else
				{
					help = ret.toString();
				}
			}
			else
			{
				Object ret;
				if (type == null)
					ret = callMethod(plugin, topic, topicParams);
				else
					ret = callMethod(plugin, type + topic, topicParams);

				if (ret == null)
				{
					irc.sendContextReply( mes, "Gah! That plugin had help, but the help seems to be broken." );
					return;
				}
				else if (ret instanceof String[])
				{
					StringBuilder buf = new StringBuilder("Help topics:");
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
		catch (ChoobNoSuchCallException e)
		{
			System.out.println(e.toString());
			irc.sendContextReply( mes, "Topic " + topic + " in plugin " + plugin + " didn't exist!" );
			return;
		}
		irc.sendContextReply( mes, help );
	}

	public String[] helpCommandPlugins = {
		"Get a list of loaded plugins.",
	};
	public void commandPlugins( Message mes )
	{
		String[] plugins = mods.plugin.plugins();

		StringBuilder buf = new StringBuilder("Plugins: ");
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

	private String titleCase(String s)
	{
		StringBuilder sb = new StringBuilder(s.toLowerCase());
		sb.setCharAt(0,s.substring(0,1).toUpperCase().charAt(0));
		return sb.toString();
	}

	private String commandString(String pluginOrig, boolean brief)
	{
		String[] commands = mods.plugin.commands(pluginOrig);

		String plugin;
		if (brief)
			plugin = Colors.BOLD + titleCase(pluginOrig) + Colors.NORMAL;
		else
			plugin = titleCase(pluginOrig);

		if (commands == null)
		{
			return "That plugin doesn't exist!";
		}
		else if (commands.length == 0)
		{
			return (brief ? plugin + ": No commands." : "No commands in plugin " + plugin + ".");
		}
		else if (commands.length == 1)
		{
			return (brief ? "" : "1 command in plugin ") + plugin + ": " + commands[0] + ".";
		}
		else if (commands.length == 2)
		{
			return (brief ? "" : "2 commands in plugin " ) + plugin + ": " + commands[0] + " and " + commands[1] + ".";
		}
		else
		{
			StringBuilder buf = new StringBuilder("");
			if (!brief)
				buf.append(commands.length + " commands in plugin ");
			buf.append(plugin);
			buf.append(": ");
			for(int i=0; i<commands.length; i++)
			{
				buf.append(commands[i]);
				if (i < commands.length - 2)
					buf.append(", ");
				else if (i == commands.length - 2)
					buf.append(" and ");
			}
			buf.append(".");

			return buf.toString();
		}
	}

	public String[] helpCommandCommands = {
		"Get a list of commands in a plugin.",
		"<plugin>",
		"<plugin> is the name of a loaded plugin."
	};
	public void commandCommands( Message mes )
	{
		String plugin = mods.util.getParamString(mes);

		if (plugin.trim().equals(""))
		{
			String rep="";

			String[] plugins = mods.plugin.plugins();
			for (int j=0; j<plugins.length; j++)
				rep += commandString(plugins[j], true) + " ";
			irc.sendContextReply(mes, rep);
		}
		else
		{
			irc.sendContextReply(mes, commandString(plugin, false));
		}
	}

	private Object callMethod(String plugin, String topic, String param) throws ChoobException
	{
		try
		{
			return mods.plugin.callGeneric(plugin, "help", topic, param);
		}
		catch (ChoobNoSuchCallException e)
		{
			return mods.plugin.callGeneric(plugin, "help", topic);
		}
	}
}
