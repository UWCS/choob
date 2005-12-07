import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
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

	public String[] helpTopics = { "Using", "Api" };

	public String[] helpUsing = {
		"Use " + Colors.BOLD + "'Help.Help <Plugin>'" + Colors.NORMAL + " to get a list of help topics for a plugin, if such help exists.",
		"To query a specific help topic use " + Colors.BOLD + "'Help.Help <Plugin>.<Topic>'" + Colors.NORMAL + ". For instance, to view this help directly use " + Colors.BOLD + "'Help.Help Help.Using'" + Colors.NORMAL + ". ",
		Colors.BOLD + "'Help.Plugins'" + Colors.NORMAL + " will give you a list of plugins.",
		Colors.BOLD + "'Help.Commands'" + Colors.NORMAL + " will give you the full command list."
	};

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
	public String[] helpCommandLongHelp = {
		"Get long help on a topic.",
		"<Topic> [<Params>]",
		"<Topic> is either a plugin name or of the form <Plugin>.<Name> or <Plugin>.<Type>.<Name>",
		"<Params> is an optional parameter to pass to the help"
	};
	public void commandLongHelp(Message mes)
	{
		doHelp(mes, true);
	}

	public String[] helpCommandBlockHelp = {
		"Cram help on a topic onto as few lines as possible.",
		"<Topic> [<Params>]",
		"<Topic> is either a plugin name or of the form <Plugin>.<Name> or <Plugin>.<Type>.<Name>",
		"<Params> is an optional parameter to pass to the help"
	};
	public void commandBlockHelp(Message mes)
	{
		doHelp(mes, false);
	}

	public String[] helpCommandSyntax = {
		"Get the syntax for a command.",
		"<Command>",
		"<Command> is either an alias or a command of the form <Plugin>.<Name>"
	};
	public void commandSyntax(Message mes)
	{
		doCapHelp(mes, 2);
	}

	public String[] helpCommandSummary = {
		"Get the summary for a command.",
		"<Command>",
		"<Command> is either an alias or a command of the form <Plugin>.<Name>"
	};
	public void commandSummary(Message mes)
	{
		doCapHelp(mes, 1);
	}

	private void doCapHelp(Message mes, int length)
	{
		List<String> params = mods.util.getParams( mes );
		if (params.size() != 2)
		{
			irc.sendContextReply(mes, "Syntax: 'Help.Syntax " + helpCommandSyntax[1] + "'.");
			return;
		}

		String[] bits = params.get(1).split("\\.");
		if (bits.length == 0 || bits.length > 2)
		{
			irc.sendContextReply(mes, "Command name must be either and alias or of the form '<Plugin>.<Command>'.");
			return;
		}
		else if (bits.length == 2)
		{
			String[] help = apiGetCallHelpLines( bits[0], "command", bits[1], null );
			if (help == null)
			{
				irc.sendContextReply(mes, "Sorry, no help found for " + params.get(1) + ".");
				return;
			}

			if (help.length > length)
			{
				String[] newHelp = new String[length];
				for(int i=0; i<length; i++)
					newHelp[i] = help[i];
				help = newHelp;
			}
			irc.sendContextReply(mes, doCallHelp( params.get(1), "command", true, null, help ));
		}
		else
		{
			// First check for alias...
			String alias = null;
			try
			{
				alias = (String)mods.plugin.callAPI("Alias", "Get", params.get(1));
			}
			catch (ChoobNoSuchCallException e)
			{
				// No alias plugin ==> must be plugin name
			}

			if (alias != null)
				irc.sendContextReply(mes, doAliasHelp( params.get(1), alias, true, length ));
			else
				irc.sendContextReply(mes, "Sorry, the alias " + params.get(1) + " does not exist!");
		}
	}

	private void doHelp( Message mes, boolean isLong )
	{
		List<String> params = mods.util.getParams(mes, 2);

		// Parse input.
		String fullTopic, topicParams;
		if (params.size() == 1)
		{
			fullTopic = "help.using";
			topicParams = "";
		}
		else if (params.size() == 2)
		{
			fullTopic = params.get(1);
			if (fullTopic.equalsIgnoreCase("plugins"))
			{
				commandPlugins(mes);
				return;
			}
			if (fullTopic.equalsIgnoreCase("commands"))
			{
				irc.sendContextReply(mes, doCommandList(isLong));
				return;
			}

			topicParams = "";
		}
		else
		{
			fullTopic = params.get(1);
			topicParams = params.get(2);
		}

		// Break up the topic string.
		Matcher topicMatcher = Pattern.compile("([^\\s.]+)(?:\\.([^\\s.]+)(?:\\.([^\\s.]+))?)?").matcher(fullTopic);
		if (!topicMatcher.matches())
		{
			irc.sendContextReply( mes, "Help topics must be of the form <Plugin>, <Plugin>.<Name>, or <Plugin>.<Type>.<Name>." );
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

		List<String> allHelp = new ArrayList<String>();

		// Do alias related stuff.
		boolean didAlias = false;
		if (topic == null)
		{
			// First check for alias...
			String alias = null;
			try
			{
				alias = (String)mods.plugin.callAPI("Alias", "Get", plugin);
			}
			catch (ChoobNoSuchCallException e)
			{
				// No alias plugin ==> must be plugin name
			}

			if (alias != null)
			{
				allHelp.addAll(doAliasHelp( plugin, alias, isLong ));
				didAlias = true;
			}
			else
				didAlias = false;

			// We're in a plugin, too.
			topic = "topics";
		}

		if (topic.equalsIgnoreCase("topics"))
			allHelp.addAll(doPluginHelp( plugin, type, topicParams, isLong, didAlias ));
		else
		{
			String[] help = null;
			if (type == null)
			{
				// Is it a command or basic help?
				help = apiGetCallHelpLines( plugin, "command", topic, topicParams );
				if (help != null)
					type = "command";
			}
			else
				help = apiGetCallHelpLines( plugin, type, topic, topicParams );

			
			if (help != null)
				// Yay, we found help!
				allHelp.addAll(doCallHelp( plugin + "." + topic, type, isLong, null, help ));

			else if (type == null)
			{
				// Probably basic help on something.
				help = apiGetBasicHelpLines( plugin, topic, topicParams );

				if (help != null)
					allHelp.addAll(doBasicHelp(isLong, help));
				else
					allHelp.add("Sorry, can't find topic " + topic + " in plugin " + plugin + ".");
			}
			else
				allHelp.add("Sorry, can't find topic " + type + "." + topic + " in plugin " + plugin + ".");
		}

		irc.sendContextReply(mes, allHelp);
	}

	private List<String> doBasicHelp( boolean isLong, String[] help )
	{
		List<String> lines = new ArrayList<String>();

		if (isLong)
		{
			for(String line: help)
				lines.add(line);
		}
		else
		{
			// Short.
			StringBuilder buf = new StringBuilder();
			for(String line: help)
				buf.append(line + " ");
			lines.add(buf.toString());
		}
		return lines;
	}

	private List<String> doPluginHelp( String plugin, String type, String topicParams, boolean isLong, boolean didAlias )
	{
		List<String> lines = new ArrayList<String>();

		String[] help = apiGetPluginHelpLines( plugin, type, topicParams );

		if (help == null)
		{
			if (!didAlias)
				lines.add( "Sorry, plugin " + Colors.BOLD + plugin + Colors.NORMAL + " doesn't exist!" );

			return lines;
		}

		if (isLong)
		{
			if (help[0] == null && help[1] == null)
				lines.add( "No help for plugin " + plugin + "." );
			else
			{
				if (didAlias)
					lines.add( "Help for plugin " + Colors.BOLD + plugin + Colors.NORMAL + ":" );
				if (help[0] != null)
					lines.add( "Commands for " + Colors.BOLD + plugin + Colors.NORMAL + ": " + help[0] );
				if (help[1] != null)
					lines.add( "Extra topics for " + Colors.BOLD + plugin + Colors.NORMAL + ": " + help[1] );
			}
		}
		else
		{
			// Short.
			if (help[0] == null && help[1] == null)
				lines.add( "No help for plugin " + plugin + "." );
			else
			{
				StringBuilder buf = new StringBuilder();
				if (didAlias)
					buf.append("Help for " + Colors.BOLD + plugin + Colors.NORMAL + ": ");
				if (help[0] != null)
				{
					buf.append("Commands: " + help[0]);
					if (help[1] != null)
						buf.append("; ");
				}
				if (help[1] != null)
					buf.append("Extra topics: " + help[1]);
				lines.add( buf.toString() );
			}
		}
		return lines;
	}

	private List<String> doCallHelp( String command, String type, boolean isLong, String alias, String[] help )
	{
		List<String> lines = new ArrayList<String>();
		boolean simpleAlias = true;
		if (alias != null)
		{
			int pos = alias.indexOf(' ');
			simpleAlias = pos == -1;
		}

		if (isLong)
		{
			// Preamble
			if (alias != null)
			{
				// Implicitly only for commands.
				if (!simpleAlias)
				{
					String aliasCommand;
					int pos = alias.indexOf(' ');
					if (pos == -1)
						aliasCommand = alias;
					else
						aliasCommand = alias.substring(0, pos);

					lines.add( Colors.BOLD + command + Colors.NORMAL + " is an alias to '" + alias + "'; help for '" + Colors.BOLD + aliasCommand + Colors.NORMAL + "' follows:" );

					command = aliasCommand;
					lines.add( Colors.BOLD + command + Colors.NORMAL + ": " + help[0] );
				}
				else
					lines.add( Colors.BOLD + command + Colors.NORMAL + ": " + help[0] + " This is an alias to '" + Colors.BOLD + alias + Colors.NORMAL + "'.");
			}
			else
				lines.add( Colors.BOLD + command + Colors.NORMAL + ": " + help[0] );

			if (type.equalsIgnoreCase("command"))
			{
				// Command help has defined form
				if (help.length == 2)
				{
					lines.add( "Syntax: '" + command + " " + help[1] + "'." );
				}
				else if (help.length > 2)
				{
					lines.add( "Syntax: '" + command + " " + help[1] + "', where:" );
					for(int i=2; i < help.length; i++) {
						if ( i == help.length - 1)
							lines.add( "   " + help[i] + "." );
						else if ( i == help.length - 2 )
							lines.add( "   " + help[i] + " and" );
						else
							lines.add( "   " + help[i] + "," );
					}
				}
			}
			else
			{
				// No defined way of dealing with this...
				for(int i=1; i < help.length; i++)
					lines.add( help[i] );
			}
		}
		else
		{
			// Short
			StringBuilder buf = new StringBuilder();
			if (alias != null)
			{
				// Implicitly only for commands.
				if (!simpleAlias)
				{
					String aliasCommand;
					int pos = alias.indexOf(' ');
					if (pos == -1)
						aliasCommand = alias;
					else
						aliasCommand = alias.substring(0, pos);

					buf.append( Colors.BOLD + command + Colors.NORMAL + " is an alias to '" + alias + "'; help for '" + Colors.BOLD + aliasCommand + Colors.NORMAL + "': " );

					command = aliasCommand;
					buf.append( help[0] );
				}
				else
					buf.append( help[0] + " Alias: '" + Colors.BOLD + alias + Colors.NORMAL + "'.");
			}
			else
				buf.append( help[0] );

			if (type.equalsIgnoreCase("command"))
			{
				if (help.length == 2)
					buf.append( " Syntax: '" + command + " " + help[1] + "'." );
				else if (help.length > 2)
				{
					buf.append( " Syntax: '" + command + " " + help[1] + "', where " );
					for(int i=2; i < help.length; i++)
					{
						if ( i == help.length - 1)
							buf.append( help[i] + "." );
						else if ( i == help.length - 2 )
							buf.append( help[i] + " and " );
						else
							buf.append( help[i] + ", " );
					}
				}
			}
			else
			{
				// No defined way of dealing with this...
				for(int i=1; i < help.length; i++)
				{
					if (i == help.length - 1)
						buf.append( help[i] );
					else
						buf.append( help[i] + " " );
				}
			}
			lines.add( buf.toString() );
		}
		return lines;
	}

	public List<String> doAliasHelp( String command, String alias, boolean isLong )
	{
		return doAliasHelp(command, alias, isLong, Integer.MAX_VALUE);
	}

	public List<String> doAliasHelp( String command, String alias, boolean isLong, int length )
	{
		Object ret = null;
		try
		{
			ret = mods.plugin.callAPI("Alias", "GetHelp", command);
		}
		catch (ChoobNoSuchCallException e) { } // Leave it null

		if (ret == null)
		{
			// Try to make our own help...
			int spacePos = alias.indexOf(' ');
			if (spacePos == -1)
				spacePos = alias.length();

			int dotPos = alias.indexOf('.');
			if (dotPos == -1 || dotPos >= spacePos - 1)
			{
				// Die! This alias seems invalid!
				List<String> retList = new ArrayList<String>();
				retList.add( command + " appears to be aliased to an invalid alias: " + alias );
				return retList;
			}

			String plugin = alias.substring(0, dotPos);
			String newCommand = alias.substring(dotPos + 1, spacePos);

			String[] help = apiGetCallHelpLines( plugin, "command", newCommand, null );
			if (help.length > length)
			{
				String[] newHelp = new String[length];
				for(int i=0; i<length; i++)
					newHelp[i] = help[i];
				help = newHelp;
			}
			return doCallHelp( command, "command", isLong, alias, help );
		}
		else if (ret instanceof String[])
		{
			String aliasCommand;
			int pos = alias.indexOf(' ');
			if (pos == -1)
				aliasCommand = alias;
			else
				aliasCommand = alias.substring(0, pos);

			String[] help = (String[])ret;
			// Same as for commands.
			if (help.length > length)
			{
				String[] newHelp = new String[length];
				for(int i=0; i<length; i++)
					newHelp[i] = help[i];
				help = newHelp;
			}
			return doCallHelp( command, "command", isLong, aliasCommand, help );
		}
		else
		{
			List<String> retList = new ArrayList<String>();
			retList.add( "Sorry, the Alias plugin isn't behaving and sent invalid help." );
			return retList;
		}
	}

	// 0 = commands, 1 = topics
	public String[] apiGetPluginHelpLines( String plugin, String type, String topicParams )
	{
		// TODO: Put description etc in here too.
		String[] returnArr = new String[2];

		String[] commands;
		try
		{
			commands = mods.plugin.getPluginCommands(plugin);
		}
		catch (ChoobNoSuchPluginException e)
		{
			return null;
		}

		StringBuilder buf = new StringBuilder();
		for(int i=0; i<commands.length; i++)
		{
			buf.append(commands[i]);
			if (i < commands.length - 2)
				buf.append(", ");
			else if (i == commands.length - 2)
				buf.append(" and ");
		}
		returnArr[0] = buf.toString();

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

		if (ret == null)
		{
			returnArr[1] = null;
		}
		else if (ret instanceof String[])
		{
			String[] topics = (String[])ret;
			buf = new StringBuilder();
			for(int i=0; i<topics.length; i++)
			{
				buf.append(topics[i]);
				if (i < topics.length - 2)
					buf.append(", ");
				else if (i == topics.length - 2)
					buf.append(" and ");
			}
			returnArr[1] = buf.toString();
		}
		else
		{
			return new String[] { "Help for " + plugin + " was of an unknown format." };
		}

		return returnArr;
	}

	public String[] apiGetBasicHelpLines( String plugin, String topic, String topicParams )
	{
		try
		{
			Object ret = callMethod(plugin, topic, topicParams);

			if (ret instanceof String)
				return new String[] { (String)ret };
			else if ( ret instanceof String[] )
				return ((String[])ret).clone();
			else
				return new String[] { "Help for " + topic + " was of an unknown format." };
		}
		catch (ChoobNoSuchCallException e)
		{
			return null;
		}
	}

	public String[] apiGetCallHelpLines( String plugin, String type, String topic, String topicParams )
	{
		try
		{
			String help;

			// Help on some specific thingy.
			Object ret;
			if (type == null)
				type = "command";

			ret = callMethod(plugin, type + topic, topicParams);

			if (ret == null)
			{
				System.err.println("Gah! Topic " + topic + " in plugin " + plugin + " had help, but the help seems to be broken." );
				return null;
			}
			else if (ret instanceof String[])
			{
				String[] helpArr = (String[])ret;
				String[] returnArr = new String[helpArr.length];
				if (helpArr.length == 0)
				{
					System.err.println("Gah! Topic " + topic + " in plugin " + plugin + " had help, but the help seems to be broken." );
					return null;
				}
				else
					return helpArr.clone();
			}
			else
			{
				return new String[] { "Help for " + topic + " was of an unknown format." };
			}
		}
		catch (ChoobNoSuchCallException e)
		{
			return null;
		}
	}

	public String[] helpCommandPlugins = {
		"Get a list of loaded plugins.",
	};
	public void commandPlugins( Message mes )
	{
		String[] plugins = mods.plugin.getLoadedPlugins();

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

	private String commandString(String plugin)
	{
		String[] commands;
		try
		{
			commands = mods.plugin.getPluginCommands(plugin);
		}
		catch (ChoobNoSuchPluginException e)
		{
			return null;
		}

		if (commands.length == 0)
		{
			return "None";
		}
		else
		{
			StringBuilder buf = new StringBuilder();
			for(int i=0; i<commands.length; i++)
			{
				buf.append(commands[i]);
				if (i < commands.length - 2)
					buf.append(", ");
				else if (i == commands.length - 2)
					buf.append(" and ");
			}

			return buf.toString();
		}
	}

	public String[] helpCommandCommands = {
		"Get a list of commands in a plugin.",
		"<plugin>",
		"<plugin> is the name of a loaded plugin."
	};

	private List<String> doCommandList(boolean isLong)
	{
		List<String> output = new ArrayList<String>();

		String[] plugins = mods.plugin.getLoadedPlugins();
		if (isLong)
		{
			for (int j=0; j<plugins.length; j++)
			{
				String commands = commandString(plugins[j]);
				output.add("Commands in " + Colors.BOLD + plugins[j] + Colors.NORMAL + ": " + commands);
			}
		}
		else
		{
			StringBuilder buf = new StringBuilder();
			for (int j=0; j<plugins.length; j++)
			{
				String commands = commandString(plugins[j]);
				buf.append(Colors.BOLD + plugins[j] + Colors.NORMAL + ": " + commands);
				if (j != plugins.length - 1)
					buf.append("; ");
			}
			buf.append(".");
			output.add(buf.toString());
		}
		return output;
	}

	public void commandCommands( Message mes )
	{
		List<String> params = mods.util.getParams(mes, 2);

		if (params.size() == 1)
		{
			irc.sendContextReply(mes, doCommandList(false));
		}
		else
		{
			String plugin = params.get(1);
			String commands = commandString(plugin);
			if (commands == null)
				irc.sendContextReply(mes, "Plugin " + plugin + "doesn't exist!");
			else
				irc.sendContextReply(mes, "Commands in " + Colors.BOLD + plugin + Colors.NORMAL + ": " + commands);
		}
	}

	private Object callMethod(String plugin, String topic, String param) throws ChoobNoSuchCallException
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
