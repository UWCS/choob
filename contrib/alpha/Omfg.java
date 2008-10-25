/** @author Faux */

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

public class Omfg
{
	Modules mods;
	IRCInterface irc;

	public Omfg(final Modules mods, final IRCInterface irc)
	{
		this.mods=mods;
		this.irc=irc;
	}

	public final String filterSworeRegex="(?:cheese)|(?:mum)|(?:pants)";

	public void filterSwore( final Message mes )
	{
		mods.interval.callBack(mes.getNick(), 5000);
	}

	public void interval ( final Object o )
	{
		if (o instanceof String)
			irc.sendAction((String)o, "slaps you for swearing! :o");
	}
}
