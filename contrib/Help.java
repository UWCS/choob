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

	public String[] helpApi = {
		  "Help is a plugin that lets you add help to other plugins. To do so,"
		+ " you simply need to provide generic calls of type 'help' for each"
		+ " of your commands, API procedures etc. For the command 'Foo', for"
		+ " example, the generic call should be named 'commandFoo'.",
		  "The return value of any of these can be either a String, which will"
		+ " simply be rendered verbatim, or a String array whose contents"
		+ " depend upon the help type: For the special name 'Topics', it's just"
		+ " a list of topics; for a command, the first element should be a"
		+ " brief description, the second the syntax, and all subsequent are"
		+ " parameter descriptions."
	};

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
		if (topic == null)
			topic = "topics";

		String help;
		try
		{
			if (!topic.toLowerCase().equals("topics"))
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
							buf.append( " Syntax: '" );
							buf.append( plugin + "." + topic );
							buf.append( " " );
							buf.append( helpArr[1] );
							buf.append( "'" );
							if (helpArr.length > 2)
							{
								buf.append(" where ");
								for(int i=2; i<helpArr.length; i++)
								{
									buf.append(helpArr[i]);
									if (i < helpArr.length - 1)
										buf.append(" and ");
								}
							}
							buf.append(".");
							help = buf.toString();
						}
					}
					else // Assume of type API.
					{
						StringBuilder buf = new StringBuilder();
						for(int i=0; i<helpArr.length; i++)
						{
							buf.append( helpArr[i] );
							if (i < helpArr.length - 2)
								buf.append(", ");
							else if (i == helpArr.length - 2)
								buf.append(" and ");
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
				try
				{
					if (type == null)
						ret = callMethod(plugin, "topics", topicParams);
					else
						ret = callMethod(plugin, type + "topics", topicParams);
				}
				catch (ChoobNoSuchCallException e)
				{
					ret = null;
				}

				StringBuilder buf = new StringBuilder("Commands: ");
				buf.append(commandString(plugin, true, false));
				if (ret == null)
				{
					buf.append(" No extra topics.");
				}
				else if (ret instanceof String[])
				{
					String[] topics = (String[])ret;
					if (topics.length == 1)
					{
						buf.append(" Extra help topic: ");
						buf.append(topics[0]);
					}
					else if (topics.length > 1)
					{
						buf.append(" Extra help topics: ");
						for(int i=0; i<topics.length; i++)
						{
							buf.append(topics[i]);
							if (i < topics.length - 2)
								buf.append(", ");
							else if (i == topics.length - 2)
								buf.append(" and ");
						}
					}
					buf.append(".");
				}
				else
				{
					buf.append(ret.toString());
				}
				help = buf.toString();
			}
		}
		catch (ChoobNoSuchPluginException e)
		{
			System.out.println(e.toString());
			irc.sendContextReply( mes, "Plugin " + plugin + " didn't exist!" );
			return;
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

	private String commandString(String pluginOrig, boolean brief, boolean name)
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
			return name ? (brief ? plugin + ": No commands." : "No commands in plugin " + plugin + ".") : "None";
		}
		else if (commands.length == 1)
		{
			return (name ? (brief ? "" : "1 command in plugin ") + plugin + ": " : "" ) + commands[0] + ".";
		}
		else if (commands.length == 2)
		{
			return (name ? (brief ? "" : "2 commands in plugin " ) + plugin + ": " : "" ) + commands[0] + " and " + commands[1] + ".";
		}
		else
		{
			StringBuilder buf = new StringBuilder("");
			if (name)
			{
				if (!brief)
					buf.append(commands.length + " commands in plugin ");
				buf.append(plugin);
				buf.append(": ");
			}
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

		if (plugin.equals(""))
		{
			String rep="";

			String[] plugins = mods.plugin.plugins();
			for (int j=0; j<plugins.length; j++)
				rep += commandString(plugins[j], true, true) + " ";
			irc.sendContextReply(mes, rep);
		}
		else
		{
			irc.sendContextReply(mes, commandString(plugin, false, true));
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
