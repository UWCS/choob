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
			List toRun = new ArrayList();

			synchronized( intervalList )
			{
				Date timeNow = new Date();
				Iterator tempIt = intervalList.iterator();

				while( tempIt.hasNext() )
				{
					Interval tempInterval = (Interval)tempIt.next();

					System.out.println("Checking against trigger: " + tempInterval);

					if( Math.abs( tempInterval.getTrigger().getTime() / 1000 ) == Math.abs( timeNow.getTime() / 1000 ) )
					{
						toRun.add( tempInterval );
						tempIt.remove();
					}
				}
			}

			Iterator runIt = toRun.iterator();

			while( runIt.hasNext() )
			{
				Interval runningInterval = (Interval)runIt.next();

				if( pluginMap.get( runningInterval.getPlugin() ) != null )
				{
					(new ChoobIntervalThread(runningInterval,pluginMap.get(runningInterval.getPlugin()),mods,irc)).start();
				}
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
