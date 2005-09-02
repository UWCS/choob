/*
 * ChoobIntervalThread.java
 *
 * Created on August 5, 2005, 10:43 PM
 */

package org.uwcs.choob;

import org.uwcs.choob.support.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.plugins.*;

/**
 *
 * @author	sadiq
 */
public class ChoobIntervalThread extends Thread
{
	Interval interval;
	Object plugin;
	Modules mods;
	IRCInterface irc;

	/** Creates a new instance of ChoobIntervalThread */
	public ChoobIntervalThread( Interval interval, Object plugin, Modules mods, IRCInterface irc )
	{
		this.interval = interval;
		this.plugin = plugin;
		this.irc = irc;
		this.mods = mods;
	}

	public void run()
	{
		BeanshellPluginUtils.doInterval(plugin, interval.getParameter(), mods, irc);
	}
}
