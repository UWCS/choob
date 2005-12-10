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
	public String[] info()
	{
		return new String[] {
			"Plugin to allow one to obtain help.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			mods.util.getVersion()
		};
	}

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

	/**  ____ ___  __  __ __  __    _    _   _ ____  ____
	 *  / ___/ _ \|  \/  |  \/  |  / \  | \ | |  _ \/ ___|
	 * | |  | | | | |\/| | |\/| | / _ \ |  \| | | | \___ \
	 * | |__| |_| | |  | | |  | |/ ___ \| |\  | |_| |___) |
	 *  \____\___/|_|  |_|_|  |_/_/   \_\_| \_|____/|____/
	 */
	// All these just call API stuff (or do* stuff)
	public String[] helpCommandHelp = {
		"Get long help on a topic.",
		"<Topic> [<Params>]",
		"<Topic> is either a plugin name or of the form <Plugin>.<Name> or <Plugin>.<Type>.<Name>",
		"<Params> is an optional parameter to pass to the help"
	};
	public void commandHelp(Message mes)
	{
		irc.sendContextReply(mes, apiGetHelp(mods.util.getParamString(mes), true));
	}

	public String[] helpCommandLongHelp = {
		"Get long help on a topic.",
		"<Topic> [<Params>]",
		"<Topic> is either a plugin name or of the form <Plugin>.<Name> or <Plugin>.<Type>.<Name>",
		"<Params> is an optional parameter to pass to the help"
	};
	public void commandLongHelp(Message mes)
	{
		irc.sendContextReply(mes, apiGetHelp(mods.util.getParamString(mes), true));
	}

	public String[] helpCommandBlockHelp = {
		"Cram help on a topic onto as few lines as possible.",
		"<Topic> [<Params>]",
		"<Topic> is either a plugin name or of the form <Plugin>.<Name> or <Plugin>.<Type>.<Name>",
		"<Params> is an optional parameter to pass to the help"
	};
	public void commandBlockHelp(Message mes)
	{
		irc.sendContextReply(mes, apiGetHelp(mods.util.getParamString(mes), false));
	}

	public String[] helpCommandSyntax = {
		"Get the syntax for a command.",
		"<Topic> [<Params>]",
		"<Topic> is either a plugin name or of the form <Plugin>.<Name> or <Plugin>.<Type>.<Name>",
		"<Params> is an optional parameter to pass to the help"
	};
	public void commandSyntax(Message mes)
	{
		// TODO check
		irc.sendContextReply(mes, apiGetSyntax(mods.util.getParamString(mes)));
	}

	public String[] helpCommandSummary = {
		"Get the summary (ie. first line) of some help.",
		"<Topic> [<Params>]",
		"<Topic> is either a plugin name or of the form <Plugin>.<Name> or <Plugin>.<Type>.<Name>",
		"<Params> is an optional parameter to pass to the help"
	};
	public void commandSummary(Message mes)
	{
		irc.sendContextReply(mes, apiGetSummary(mods.util.getParamString(mes)));
	}

	// TODO - should these be here or in Plugin?
	public String[] helpCommandPlugins = {
		"Get a list of loaded plugins.",
	};
	public void commandPlugins( Message mes )
	{
		irc.sendContextReply(mes, apiGetPluginList());
	}

	public String[] helpCommandCommands = {
		"Get a list of commands in a plugin.",
		"<Plugin>",
		"<Plugin> is the name of a loaded plugin."
	};
	public void commandCommands( Message mes )
	{
		List<String> params = mods.util.getParams(mes, 2);

		if (params.size() == 1)
			irc.sendContextReply(mes, apiGetCommandList(true));

		else if (params.size() == 2)
			irc.sendContextReply(mes, apiGetCommandList(params.get(1), true));

		else
			irc.sendContextReply(mes, apiGetSyntax(params.get(0)));
	}

	private static final int
		MODE_ALL = 0,
		MODE_SYNTAX = 1,
		MODE_SUMMARY = 2;

	/**    _    ____ ___      _    _     ___    _    ____  _____ ____
	 *    / \  |  _ \_ _|    / \  | |   |_ _|  / \  / ___|| ____/ ___|
	 *   / _ \ | |_) | |    / _ \ | |    | |  / _ \ \___ \|  _| \___ \
	 *  / ___ \|  __/| |   / ___ \| |___ | | / ___ \ ___) | |___ ___) |
	 * /_/   \_\_|  |___| /_/   \_\_____|___/_/   \_\____/|_____|____/
	 */
	// These are used for core help bits, and just alias to other bits of code.
	public String[] apiGetHelp( String topic, Boolean isLong )
	{
		return _apiGetHelp(topic, isLong, MODE_ALL);
	}

	public String[] apiGetSyntax( String topic )
	{
		return _apiGetHelp(topic, true, MODE_SYNTAX);
	}

	public String[] apiGetSummary( String topic )
	{
		return _apiGetHelp(topic, true, MODE_SUMMARY);
	}

	private String[] _apiGetHelp( String helpStr, Boolean isLong, Integer mode )
	{
		String[] params = helpStr.split("\\s+", 2);

		// Parse input.
		String fullTopic, topicParams;
		if (params.length == 0 || params[0].length() == 0)
		{
			fullTopic = "help.using";
			topicParams = "";
		}
		else if (params.length == 1)
		{
			fullTopic = params[0];
			if (mode == MODE_ALL)
			{
				if (fullTopic.equalsIgnoreCase("plugins"))
				{
					return apiGetPluginList();
				}
				else if (fullTopic.equalsIgnoreCase("commands"))
				{
					return apiGetCommandList(isLong);
				}
			}

			topicParams = "";
		}
		else
		{
			fullTopic = params[0];
			topicParams = params[1];
		}

		// Break up the topic string.
		Matcher topicMatcher = Pattern.compile("([^\\s.]+)(?:\\.([^\\s.]+)(?:\\.([^\\s.]+))?)?").matcher(fullTopic);
		if (!topicMatcher.matches())
		{
			return new String[] { "Help topics must be of the form <Plugin>, <Plugin>.<Name>, or <Plugin>.<Type>.<Name>." };
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
				Object ret = null;
				try
				{
					ret = mods.plugin.callAPI("Alias", "GetHelp", plugin);
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
						allHelp.add( formatCommand(plugin) + " appears to be aliased to an invalid alias: " + formatAlias(alias) );
					}
					else
					{
						String newPlugin = alias.substring(0, dotPos);
						String newCommand = alias.substring(dotPos + 1, spacePos);

						String[] help = _getCallHelpLines( newPlugin, "command", newCommand, null );
						allHelp.addAll(formatCallHelp( plugin, "command", alias, help, isLong, mode ));
					}
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
					allHelp.addAll(formatCallHelp( plugin, "command", aliasCommand, help, isLong, mode ));
				}
				else
				{
					List<String> retList = new ArrayList<String>();
					allHelp.add( "Sorry, the Alias plugin isn't behaving and sent invalid help." );
				}
				didAlias = true;
			}
			else
				didAlias = false;

			// We're in a plugin, too.
			topic = "topics";
		}

		if (topic.equalsIgnoreCase("topics"))
		{
			String[] help = _getPluginHelpLines( plugin, type, topicParams );

			if (help != null)
				allHelp.addAll(formatPluginHelp( plugin, help, isLong, didAlias ));
			else if (!didAlias)
				allHelp.add("Sorry, plugin " + formatPlugin(plugin) + " does not exist!");
		}
		else
		{
			String[] help = null;
			if (type == null)
			{
				// Is it a command or basic help?
				help = _getCallHelpLines( plugin, "command", topic, topicParams );
				if (help != null)
					type = "command";
			}
			else
				help = _getCallHelpLines( plugin, type, topic, topicParams );

			
			if (help != null)
				// Yay, we found help!
				allHelp.addAll(formatCallHelp( plugin + "." + topic, type, null, help, isLong, mode ));

			else if (type == null)
			{
				// Probably basic help on something.
				help = _getBasicHelpLines( plugin, topic, topicParams );

				if (help != null)
					allHelp.addAll(formatBasicHelp(help, isLong, mode));
				else
					return new String[] { "Sorry, can't find topic " + formatTopic(topic) + " in plugin " + formatPlugin(plugin) + "." };
			}
			else
				return new String[] { "Sorry, can't find topic " + formatTopic(type + "." + topic) + " in plugin " + formatPlugin(plugin) + "." };
		}

		String[] ret = new String[0];
		return allHelp.toArray(ret);
	}

	/**    _    ____ ___
	 *    / \  |  _ \_ _|
	 *   / _ \ | |_) | |
	 *  / ___ \|  __/| |
	 * /_/   \_\_|  |___|
	 */
	// These actually generate outputtable help.
	public String[] apiGetCommandList(String pluginName, Boolean isLong)
	{
		return new String[] { _apiCommandList(null, isLong) };
	}

	public String[] apiGetCommandList(Boolean isLong)
	{
		// Full command list, then...
		List<String> output = new ArrayList<String>();

		String[] plugins = mods.plugin.getLoadedPlugins();
		if (isLong)
		{
			for (int j=0; j<plugins.length; j++)
				output.add(_apiCommandList(plugins[j], true));
		}
		else
		{
			StringBuilder buf = new StringBuilder();
			for (int j=0; j<plugins.length; j++)
			{
				buf.append(formatPlugin(plugins[j]) + ": " + _apiCommandList(plugins[j], false));
				if (j != plugins.length - 1)
					buf.append("; ");
			}
			buf.append(".");
			output.add(buf.toString());
		}
		String[] ret = new String[0];
		return output.toArray(ret);
	}

	private String _apiCommandList(String pluginName, Boolean isLong)
	{
		String[] commands;
		try
		{
			commands = mods.plugin.getPluginCommands(pluginName);
		}
		catch (ChoobNoSuchPluginException e)
		{
			if (isLong)
				return "Plugin " + formatPlugin(pluginName) + " does not exist.";
			else
				return "does not exist";
		}

		if (commands.length == 0)
		{
			if (isLong)
				return "Plugin " + formatPlugin(pluginName) + " has no commands.";
			else
				return "None";
		}
		else
		{
			StringBuilder buf = new StringBuilder();
			if (isLong)
				buf.append("Commands in " + formatPlugin(pluginName) + ": ");
			for(int i=0; i<commands.length; i++)
			{
				buf.append(formatCommand(commands[i]));
				if (i < commands.length - 2)
					buf.append(", ");
				else if (i == commands.length - 2)
					buf.append(" and ");
			}
			if (isLong)
				buf.append(".");

			return buf.toString();
		}
	}

	public String[] apiGetPluginList()
	{
		String[] plugins = mods.plugin.getLoadedPlugins();

		StringBuilder buf = new StringBuilder("Plugins: ");
		for(int i=0; i<plugins.length; i++)
		{
			if (i != 0)
				buf.append(", ");
			if (i == plugins.length - 1)
				buf.append("and ");
			buf.append(formatPlugin(plugins[i]));
		}
		buf.append(".");

		return new String[] { buf.toString() };
	}

	/** _____ ___  ____  __  __    _  _____ _____ _____ ____  ____
	 * |  ___/ _ \|  _ \|  \/  |  / \|_   _|_   _| ____|  _ \/ ___|
	 * | |_ | | | | |_) | |\/| | / _ \ | |   | | |  _| | |_) \___ \
	 * |  _|| |_| |  _ (| |  | |/ ___ \| |   | | | |___|  _ ( ___) |
	 * |_|   \___/|_| \_\_|  |_/_/   \_\_|   |_| |_____|_| \_\____/
	 */
	// These do the actual formatting of help for the aliases.
	private List<String> formatBasicHelp( String[] help, boolean isLong, int mode )
	{
		List<String> lines = new ArrayList<String>();

		int start, finish;
		if (mode == MODE_ALL)
		{
			start = 0;
			finish = help.length;
		}
		else // (mode == MODE_SUMMARY)
		{
			start = 0;
			finish = 1;
		}

		if (isLong)
		{
			for(int i=start; i<finish; i++)
				lines.add(formatGeneral(help[i]));
		}
		else
		{
			// Short.
			StringBuilder buf = new StringBuilder();
			for(int i=start; i<finish; i++)
				buf.append(formatGeneral(help[i]) + " ");
			lines.add(buf.toString());
		}
		return lines;
	}

	private List<String> formatPluginHelp( String plugin, String[] help, boolean isLong, boolean didAlias )
	{
		List<String> lines = new ArrayList<String>();

		if (help == null)
		{
			if (!didAlias)
				lines.add( "Sorry, plugin " + formatPlugin(plugin) + " doesn't exist!" );

			return lines;
		}

		if (isLong)
		{
			if (didAlias)
				lines.add( "Help for plugin " + formatPlugin(plugin) + ":" );
			if (help[0] != null)
				lines.add( "Commands for " + formatPlugin(plugin) + ": " + help[0] );
			if (help[1] != null)
				lines.add( "Extra topics for " + formatPlugin(plugin) + ": " + help[1] );
		}
		else
		{
			// Short.
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
		return lines;
	}

	private List<String> formatCallHelp( String command, String type, String alias, String[] help, boolean isLong, int mode )
	{
		List<String> lines = new ArrayList<String>();
		boolean simpleAlias = true;
		if (alias != null)
		{
			int pos = alias.indexOf(' ');
			simpleAlias = pos == -1;
		}

		int start, finish;
		if (mode == MODE_ALL)
		{
			start = 0;
			finish = help.length;
		}
		else if (mode == MODE_SUMMARY)
		{
			start = 0;
			finish = 1;
		}
		else // (mode == MODE_SYNTAX)
		{
			start = 1;
			finish = 2;
		}

		if (isLong)
		{
			// Preamble
			if (mode != MODE_SYNTAX)
			{
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

						lines.add( formatCommand(command) + " is an alias to '" + formatAlias(alias) + "'; help for '" + formatCommand(aliasCommand) + "' follows:" );

						command = aliasCommand;
						lines.add( formatCommand(command) + ": " + formatGeneral(help[0]) );
					}
					else
						lines.add( formatCommand(command) + ": " + formatGeneral(help[0]) + " This is an alias to '" + formatCommand(alias) + "'.");
				}
				else
					lines.add( formatCommand(command) + ": " + formatGeneral(help[0]) );
			}

			if (mode != MODE_SUMMARY)
			{
				if (type.equalsIgnoreCase("command"))
				{
					// Command help has defined form
					if (help.length == 2 || mode == MODE_SYNTAX)
					{
						lines.add( formatSyntax(command + " " + help[1]) + "." );
					}
					else if (help.length > 2)
					{
						lines.add( formatSyntax(command + " " + help[1]) + ", where:" );
						for(int i=2; i < help.length; i++) {
							if ( i == help.length - 1)
								lines.add( "   " + formatParam(help[i]) + "." );
							else if ( i == help.length - 2 )
								lines.add( "   " + formatParam(help[i]) + " and" );
							else
								lines.add( "   " + formatParam(help[i]) + "," );
						}
					}
				}
				else
				{
					// No defined way of dealing with this...
					for(int i=1; i < help.length; i++)
						lines.add( formatGeneral(help[i]) );
				}
			}
		}
		else
		{
			// Short
			StringBuilder buf = new StringBuilder();
			if (mode != MODE_SYNTAX)
			{
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

						buf.append( formatCommand(command) + " is an alias to '" + formatAlias(alias) + "'; help for '" + formatCommand(aliasCommand) + "': " );

						command = aliasCommand;
						buf.append( formatGeneral(help[0]) );
					}
					else
						buf.append( formatGeneral(help[0]) + " Alias: '" + formatCommand(alias) + "'.");
				}
				else
					buf.append( formatGeneral(help[0]) );
			}

			if (mode != MODE_SUMMARY)
			{
				if (type.equalsIgnoreCase("command"))
				{
					if (help.length == 2 || mode == MODE_SYNTAX)
						buf.append( " " + formatSyntax(command + " " + help[1]) + "." );
					else if (help.length > 2)
					{
						buf.append( " " + formatSyntax(command + " " + help[1]) + ", where " );
						for(int i=2; i < help.length; i++)
						{
							if ( i == help.length - 1)
								buf.append( formatParam(help[i]) + "." );
							else if ( i == help.length - 2 )
								buf.append( formatParam(help[i]) + " and " );
							else
								buf.append( formatParam(help[i]) + ", " );
						}
					}
				}
				else
				{
					// No defined way of dealing with this...
					for(int i=1; i < help.length; i++)
					{
						if (i == help.length - 1)
							buf.append( formatGeneral(help[i]) );
						else
							buf.append( formatGeneral(help[i]) + " " );
					}
				}
			}
			lines.add( buf.toString() );
		}
		return lines;
	}

	Pattern generalSpotter = Pattern.compile(
			  "(?i:"
				+ "(see [\\w.]+)"
			+ "|"
				+ "(<\\w+>)"
			+ ")"
	);
	private String formatGeneral(String text)
	{
		StringBuilder result = new StringBuilder();

		// This'll be the hardest, then...
		Matcher match = generalSpotter.matcher(text);

		int pos = 0;
		while(match.find())
		{
			result.append(text.substring(pos, match.start()));
			if (match.group(1) != null)
			{
				// ref
				result.append(formatRef(text.substring(match.start(), match.end())));
			}
			else if (match.group(2) != null)
			{
				// param
				result.append(formatParamName(text.substring(match.start(), match.end())));
			}
			pos = match.end();
		}
		result.append(text.substring(pos));
		return result.toString();
	}

	private String formatSyntax(String text)
	{
		return "Syntax: '" + formatSyntaxInternal(text, true) + "'";
	}

	Pattern paramPattern = Pattern.compile("<\\w+>");
	private String formatSyntaxInternal(String text, boolean resolve)
	{
		StringBuilder result = new StringBuilder();

		String[] bits = text.split(" ", 2);

		result.append(irc.getTrigger());

		if (resolve)
			result.append(formatFullCommand(bits[0]));
		else
			result.append(formatCommand(bits[0]));

		if (bits.length == 2)
		{
			result.append(" ");

			Matcher match = paramPattern.matcher(bits[1]);

			int pos = 0;
			while(match.find())
			{
				result.append(bits[1].substring(pos, match.start()));
				result.append(formatParamName(bits[1].substring(match.start(), match.end())));
				pos = match.end();
			}
			result.append(bits[1].substring(pos));
		}
		return result.toString();
	}

	private String formatAlias(String text)
	{
		return formatSyntaxInternal(text, false);
	}

	private String formatPlugin(String text)
	{
		return Colors.BOLD + text + Colors.NORMAL;
	}

	private String formatCommand(String text)
	{
		return Colors.BOLD + text + Colors.NORMAL;
	}

	private String formatFullCommand(String text)
	{
		// TODO: Core alias here.
		try
		{
			String core = (String)mods.plugin.callAPI("Alias", "GetCoreAlias", text);
			if (core == null)
				return Colors.BOLD + text + Colors.NORMAL;
			else
				return Colors.BOLD + core + Colors.NORMAL;
		}
		catch (ChoobNoSuchCallException e)
		{
			return Colors.BOLD + text + Colors.NORMAL;
		}
	}

	// "See <x>"
	private String formatRef(String text)
	{
		String[] bits = text.split(" ", 2);
		return bits[0] + " '" + formatSyntaxInternal("Help.Help " + bits[1], true) + "'";
	}

	private String formatTopic(String text)
	{
		return Colors.BOLD + text + Colors.NORMAL;
	}

	// "<Name> explanation"
	private String formatParam(String text)
	{
		String[] bits = text.split(" ", 2);
		if (bits.length == 2)
			return formatParamName(bits[0]) + " " + formatGeneral(bits[1]);
		else
			return formatParamName(bits[0]);
	}

	// "<Name>"
	private String formatParamName(String text)
	{
		return Colors.UNDERLINE + text + Colors.NORMAL;
	}

	/**  ____ ____      _    ____  ____  _____ ____  ____
	 *  / ___|  _ \    / \  | __ )| __ )| ____|  _ \/ ___|
	 * | |  _| |_) |  / _ \ |  _ \|  _ \|  _| | |_) \___ \
	 * | |_| |  _ (  / ___ \| |_) | |_) | |___|  _ ( ___) |
	 *  \____|_| \_\/_/   \_\____/|____/|_____|_| \_\____/
	 */
	// These do the dirty work of actually pulling up help on stuff.

	// 0 = commands, 1 = topics
	private String[] _getPluginHelpLines( String plugin, String type, String topicParams )
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

	private String[] _getBasicHelpLines( String plugin, String topic, String topicParams )
	{
		try
		{
			Object ret = callMethod(plugin, topic, topicParams);

			if (ret instanceof String)
				return new String[] { (String)ret };
			else if ( ret instanceof String[] )
				return (String[])ret;
			else
				return new String[] { "Help for " + topic + " was of an unknown format." };
		}
		catch (ChoobNoSuchCallException e)
		{
			return null;
		}
	}

	private String[] _getCallHelpLines( String plugin, String type, String topic, String topicParams )
	{
		try
		{
			String help;

			// Help on some specific thingy.
			Object ret;
			if (type == null)
				type = "command";

			ret = callMethod(plugin, type + topic, topicParams);

			if (ret instanceof String[] && ((String[])ret).length > 0)
			{
				String[] helpArr = (String[])ret;
				String[] returnArr = new String[helpArr.length];
				return helpArr;
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

	// Helpers
	private String titleCase(String s)
	{
		StringBuilder sb = new StringBuilder(s.toLowerCase());
		sb.setCharAt(0,s.substring(0,1).toUpperCase().charAt(0));
		return sb.toString();
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
