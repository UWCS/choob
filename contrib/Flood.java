import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.security.*;
import org.jibble.pircbot.Colors;
import java.util.regex.*;

/**
 * Choob antiflood plugin. Provides generic flood protection for plugins.
 *
 * @author bucko
 */

// Keeps track of the most recent messages from people.
public class FloodObj
{
	private long lastmes[];
	private int tailback; // Number of message times to remember.
	private int lastOffset;
	private long nextWarn; // Only warn when last message time > this.
	private int limit;

	static final int DELAY = 5000; // Warn once every (this) ms.

	public FloodObj(int limit, int tailback)
	{
		this.tailback = tailback;
		this.limit = limit;

		lastmes = new long[tailback];
		for(int i=0; i<tailback; i++)
			lastmes[i] = 0;

		lastOffset = 0;
		nextWarn = 0; // Always warn on first offense.
	}

	// This uses tailback - 1 as factor. It could conceivably use just tailback, but then it counts the blank period before the first message as having a message at the start. Or something.
	// Consider 2 messages in 4 secs. That's 2 messages within 4 secs, hence 2 messages per second average. But we're talking more about rate-over-time, so we'd like this to be more like 4s.
	public long average()
	{
		return (lastmes[lastOffset] - lastmes[(lastOffset + 1) % tailback]) / (tailback - 1);
	}

	public boolean shouldWarn()
	{
		long time = System.currentTimeMillis();
		if (time > nextWarn)
		{
			nextWarn = time + DELAY;
			return true;
		}
		else
			return false;
	}

	public boolean isFlooding()
	{
		lastOffset = (lastOffset + 1) % tailback;
		lastmes[lastOffset] = System.currentTimeMillis();
		if (average() < limit)
			return true;
		else
			return false;
	}

	public boolean canCull(long time)
	{
		// If this is true, no messages can possibly have a bearing on stuff...
		return (time - lastmes[lastOffset]) / tailback < limit;
	}
}

public class Flood
{
	public String[] info()
	{
		return new String[] {
			"Flood protection plugin.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private static int TIMEOUT = 10000; // Time between flood culls.

	/**
	 * Map plugin name to Map of (keys to flood objects)
	 */
	private Map<String,Map<String,FloodObj>> floods;
	private Modules mods;
	private IRCInterface irc;

	public Flood(Modules mods, IRCInterface irc)
	{
		this.irc = irc;
		this.mods = mods;
		floods = new HashMap<String,Map<String,FloodObj>>();
	}

	// Loop over everything, splatting as necessary.
	public void interval( Object parameter ) throws ChoobException
	{
		synchronized(floods)
		{
			long time = System.currentTimeMillis();

			Iterator<Map<String,FloodObj>> iter = floods.values().iterator();
			Map<String,FloodObj> plugFloods;
			while((plugFloods = iter.next()) != null)
			{
				Iterator<FloodObj> iter2 = plugFloods.values().iterator();
				FloodObj plugFlood;
				while((plugFlood = iter2.next()) != null)
				{
					if (plugFlood.canCull(time))
						iter.remove();
				}
			}
		}
	}

	// 0 = fine, 1 = yes -- message them, 2 = yes -- but don't message them (already done recently)
	public Integer apiIsFlooding( String key, Integer limit, Integer tailback )
	{
		FloodObj flood = getFloodObj(key.toLowerCase(), limit, tailback);

		if (!flood.isFlooding())
			return 0;
		else if (flood.shouldWarn())
			return 1;
		else
			return 2;
	}

	private FloodObj getFloodObj(String key, int limit, int tailback)
	{
		String pluginName = mods.security.getPluginName(0);
		if (pluginName == null)
			pluginName = "alias"; // XXX?
		else
			pluginName = pluginName.toLowerCase();

		Map<String,FloodObj> plugFloods;
		synchronized(floods)
		{
			plugFloods = floods.get(pluginName);
			if (plugFloods == null)
			{
				plugFloods = new HashMap<String,FloodObj>();
				floods.put(pluginName, plugFloods);
			}
			FloodObj plugFlood = plugFloods.get(key);
			if (plugFlood == null)
			{
				plugFlood = new FloodObj(limit, tailback);
				plugFloods.put(key, plugFlood);
			}
			return plugFlood;
		}
	}
}
