/*
 * ChoobWatcherThread.java
 *
 * Created on August 5, 2005, 10:34 PM
 */

package org.uwcs.choob;

import org.uwcs.choob.support.*;
import java.util.*;
import org.uwcs.choob.modules.*;

/**
 *
 * @author	sadiq
 */
public class ChoobWatcherThread extends Thread
{
	List intervalList;
	IRCInterface irc;
	boolean running;
	Map pluginMap;
	Modules mods;

	/** Creates a new instance of ChoobWatcherThread */
	public ChoobWatcherThread( List intervalList, IRCInterface irc, Map pluginMap, Modules mods )
	{
		this.intervalList = intervalList;
		this.irc = irc;
		this.pluginMap = pluginMap;
		this.mods = mods;
	}

	public void run()
	{
		running = true;

		do
		{
			List<Interval> toRun = new ArrayList();

			synchronized( intervalList )
			{
				Date timeNow = new Date();
				Iterator tempIt = intervalList.iterator();

				while( tempIt.hasNext() )
				{
					Interval tempInterval = (Interval)tempIt.next();

					if( Math.abs( tempInterval.getTrigger().getTime() / 1000 ) == Math.abs( timeNow.getTime() / 1000 ) )
					{
						toRun.add( tempInterval );
						tempIt.remove();
					}
				}
			}

			Iterator<Interval> runIt = toRun.iterator();

			while( runIt.hasNext() )
			{
				Interval runningInterval = runIt.next();

				ChoobTask t = mods.plugin.doInterval(runningInterval.getPlugin(), runningInterval.getParameter());
				ChoobThreadManager.queueTask(t);
			}

			synchronized( this )
			{
				try
				{
					wait(500);
				}
				catch( InterruptedException e )
				{
					// Well shucks Batman, I guess that _was_ a gay bar.
				}
			}
		}
		while( running );
	}
}
