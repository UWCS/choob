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
 * command name. The call is expected to return a String.
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

	/**
	 * Get help!
	 */
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
			if (topic != null)
			{
				Object ret = mods.plugin.callGeneric(plugin, "help", topic, topicParams);
				if (ret == null)
				{
					irc.sendContextReply( mes, "Gah! That topic had help, but the help seems to be broken." );
					return;
				}
				help = ret.toString();
			}
			else
			{
				Object ret = mods.plugin.callGeneric(plugin, "help", "Topics");
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

	public String[] helpTopics( )
	{
		return new String[] { "Help", "Plugins" };
	}

	public String helpHelp( String params )
	{
		return "Syntax: \"Help.Help <topic> <params>\" where <topic> is either a plugin name or of the form <plugin>.<name>, and <params> is a parameter to pass to the help.";
	}
}
