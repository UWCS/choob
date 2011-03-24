import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Map;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.GetContentsCached;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

public class Reflect
{
	private final Modules mods;
	private final IRCInterface irc;

	public Reflect(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	@SuppressWarnings("unchecked")
	public void commandScraper(final Message mes) throws IllegalArgumentException, IllegalAccessException, IOException
	{
		if (mods.security.hasNickPerm(new ChoobPermission("admin.ponies"), mes))
		{
			final Field fields[] = mods.scrape.getClass().getDeclaredFields();
			for (final Field f : fields)
			{
				if (!"sites".equals(f.getName()))
					continue;

				f.setAccessible(true);
				final Map <URL, GetContentsCached> m = (Map<URL, GetContentsCached>) f.get(mods.scrape);
				long dat = 0;
				for (final GetContentsCached g : m.values())
					dat += g.getContents().length();
				irc.sendContextReply(mes, "Om nom " + m.size() + ": " + dat);
			}
		}
	}
}
