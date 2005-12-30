/** @author Faux */

import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;

public class Omfg
{
	Modules mods;
	IRCInterface irc;

	public Omfg(Modules mods, IRCInterface irc)
	{
		this.mods=mods;
		this.irc=irc;
	}

	public final String filterSworeRegex="(?:cheese)|(?:mum)|(?:pants)";

	public void filterSwore( Message mes )
	{
		mods.interval.callBack(mes.getNick(), 5000);
	}

	public void interval ( Object o )
	{
		if (o instanceof String)
			irc.sendAction((String)o, "slaps you for swearing! :o");
	}
}
