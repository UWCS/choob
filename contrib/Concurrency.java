import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;

/**
 * Choob concurrency plugin.
 *
 * @author James Ross <silver@warwickcompsoc.co.uk>
 *
 */

public class PluginConcurrencyLimit {
	public int id;
	public String pluginName;
	public int threadLimit;
}

public class Concurrency {
	public String[] info() {
		return new String[] {
			"Plugin which manages thread limits.",
			"James Ross",
			"silver@warwickcompsoc.co.uk",
			"$Rev$$Date$"
		};
	}
	
	Modules mods;
	IRCInterface irc;
	
	// Default thread limit for any plugin without a specific limit set.
	private static final int DEFAULT_THREAD_LIMIT = 2;
	
	public Concurrency(Modules mods, IRCInterface irc) {
		this.irc = irc;
		this.mods = mods;
	}
	
	public int apiGetThreadLimit(String pluginName)
	{
		int limit = DEFAULT_THREAD_LIMIT;
		
		List<PluginConcurrencyLimit> limits = mods.odb.retrieve(PluginConcurrencyLimit.class, "WHERE pluginName = \"" + mods.odb.escapeString(pluginName) + "\"");
		
		if (limits.size() > 0)
			limit = limits.get(0).threadLimit;
		
		//System.out.println("Concurrency.apiGetThreadLimit   : plugin = " + pluginName + ", limit = " + limit + (limits.size() > 0 ? " [from ODB]" : ""));
		return limit;
	}
	
	public String[] helpCommandGetThreadLimit = {
			"Gets the thread limit for a plugin.",
			"<Plugin>",
			"<Plugin> is the name of the plugin to look up."
		};
	
	public void commandGetThreadLimit(Message mes)
	{
		mods.security.checkAuth(mes);
		
		List params = mods.util.getParams(mes);
		if (params.size() != 2)
			throw new ChoobBadSyntaxError();
		
		String pluginName = (String)params.get(1);
		
		List<PluginConcurrencyLimit> limits = mods.odb.retrieve(PluginConcurrencyLimit.class, "WHERE pluginName = \"" + mods.odb.escapeString(pluginName) + "\"");
		
		if (limits.size() > 0)
			irc.sendContextReply(mes, "Plugin '" + pluginName + "' has a specific thread limit of " + limits.get(0).threadLimit + ".");
		else
			irc.sendContextReply(mes, "Plugin '" + pluginName + "' has the default thread limit of " + DEFAULT_THREAD_LIMIT + ".");
	}
	
	public String[] helpCommandSetThreadLimit = {
			"Sets the thread limit for a plugin.",
			"<Plugin> [<ThreadLimit>]",
			"<Plugin> is the name of the plugin to look up.",
			"<ThreadLimit> is the maximum number of concurrent threads to allow. Set tot 0 to remove the specific setting and revert to the default."
		};
	
	public void commandSetThreadLimit(Message mes)
	{
		mods.security.checkAuth(mes);
		
		if (!mods.security.hasPerm(new ChoobPermission("plugin.threadlimit"), mes)) {
			irc.sendContextReply(mes, "You do not have permission to change the thread limit of plugins.");
			return;
		}
		
		List params = mods.util.getParams(mes);
		if (params.size() != 3)
			throw new ChoobBadSyntaxError();
		
		String pluginName = (String)params.get(1);
		int limit = Integer.parseInt((String)params.get(2));
		
		List<PluginConcurrencyLimit> limits = mods.odb.retrieve(PluginConcurrencyLimit.class, "WHERE pluginName = \"" + mods.odb.escapeString(pluginName) + "\"");
		
		if (limits.size() > 0)
		{
			PluginConcurrencyLimit pcl = limits.get(0);
			
			if (limit <= 0)
			{
				mods.odb.delete(pcl);
				irc.sendContextReply(mes, "Plugin '" + pluginName + "' reverted to default thread limit of " + DEFAULT_THREAD_LIMIT + ". This will not take effect immediately.");
			}
			else
			{
				pcl.threadLimit = limit;
				mods.odb.update(pcl);
				irc.sendContextReply(mes, "Plugin '" + pluginName + "' given a thread limit of " + limit + ". This will not take effect immediately.");
			}
		}
		else
		{
			PluginConcurrencyLimit pcl = new PluginConcurrencyLimit();
			pcl.pluginName = pluginName;
			pcl.threadLimit = limit;
			mods.odb.save(pcl);
			irc.sendContextReply(mes, "Plugin '" + pluginName + "' given a thread limit of " + limit + ". This will not take effect immediately.");
		}
	}
}