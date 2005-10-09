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

	public String[] helpTopics = { "Help", "Plugins" };

	public String[] helpHelp = { "Get help on a topic.", "Help.Help <topic> <params>", "<topic> is either a plugin name or of the form <plugin>.<name>", "<params> is a parameter to pass to the help." };

	public String[] helpPlugins = { "Get a list of loaded plugins.", "Help.Plugins" };
}
