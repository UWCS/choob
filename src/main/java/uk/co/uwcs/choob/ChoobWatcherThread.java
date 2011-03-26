/*
 * ChoobWatcherThread.java
 *
 * Created on August 5, 2005, 10:34 PM
 */

package uk.co.uwcs.choob;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.Interval;

/**
 * Continually monitors the list of active callbacks, executing them as required.
 */
public final class ChoobWatcherThread extends Thread
{
	private final List<Interval> intervalList;
	private boolean running;
	private final Modules mods;
	private final ChoobThreadManager ctm;

	/** Creates a new instance of ChoobWatcherThread */
	ChoobWatcherThread(final List<Interval> intervalList, final IRCInterface irc,
			final Modules mods, final ChoobThreadManager ctm) {
		this.intervalList = intervalList;
		this.mods = mods;
		this.ctm = ctm;
	}

	@Override
	public void run()
	{
		running = true;

		do
		{
			final long timeNow = new Date().getTime();
			long next = timeNow + 1000;
			List<Interval> requeuedIntervals;

			synchronized( intervalList )
			{
				Iterator<Interval> tempIt = intervalList.iterator();
				requeuedIntervals = new ArrayList<Interval>();

				while( tempIt.hasNext() )
				{
					final Interval tempInterval = tempIt.next();

					if (tempInterval.getTrigger() <= timeNow)
					{
						tempIt.remove();
						final ChoobTask t = mods.plugin.doInterval(tempInterval.getPlugin(), tempInterval.getParameter());
						if (t != null)
						{
							try
							{
								ctm.queueTask(t);
							}
							catch (final RejectedExecutionException e)
							{
								// Plugin is at concurreny limit. Requeue task for later.
								tempInterval.setTrigger(tempInterval.getTrigger() + 1000);
								requeuedIntervals.add(tempInterval);
							}
							catch (final Exception e)
							{
								System.err.println("Plugin " + tempInterval.getPlugin() + " got exception queuing task.");
								System.err.println(e);
								e.printStackTrace();
							}
						}
						else
						{
							System.err.println("Plugin manager for plugin " + tempInterval.getPlugin() + " returned a null doInterval ChoobTask.");
						}
					}
					else if (next > tempInterval.getTrigger())
						next = tempInterval.getTrigger();
				}

				// Re-queue any items here.
				// We need to kill the iterator before this will work.
				tempIt = requeuedIntervals.iterator();
				while (tempIt.hasNext())
				{
					final Interval tempInterval = tempIt.next();
					intervalList.add(tempInterval);
				}
			}

			final long delay = next - timeNow;

			synchronized( this )
			{
				try
				{
					wait(delay);
				}
				catch( final InterruptedException e )
				{
					// Well shucks Batman, I guess that _was_ a gay bar.
				}
			}
		}
		while( running );
	}
}
