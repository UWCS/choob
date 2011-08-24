/*
 * IntervalModule.java
 *
 * Created on August 5, 2005, 11:25 PM
 */

package uk.co.uwcs.choob.modules;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.uwcs.choob.support.Interval;

/**
 * Generates a call-back to a plugin at a specified point in time.
 */
public final class IntervalModule
{
	private static final Logger logger = LoggerFactory.getLogger(IntervalModule.class);

	private final List <Interval> intervalList;
	private final Modules mods;

	/** Creates a new instance of IntervalModule */
	IntervalModule( final List <Interval> intervalList, final Modules mods )
	{
		this.intervalList = intervalList;
		this.mods = mods;
	}

	/**
	 * Calls the 'interval' function in the calling plugin, with the specified parameter and after the specified interval.
	 * @param parameter The paramater that you want passed along to the interval function.
	 * @param interval The Date at which you want the event to occour.
	 */
	public void callBack( final Object parameter, final Date interval )
	{
		callBackReal( parameter, interval.getTime(), 0 );
	}

	/**
	 * Calls the 'interval' function in the calling plugin, with the specified parameter and after the specified interval.
	 * @param parameter The paramater that you want passed along to the interval function.
	 * @param interval The Date at which you want the event to occour.
	 * @param id The unique id of this interval (-1 for no ID).
	 */
	public void callBack( final Object parameter, final Date interval, final int id )
	{
		callBackReal( parameter, interval.getTime(), id );
	}

	/**
	 * Calls the 'interval' function in the calling plugin, with the specified parameter and after the specified interval.
	 * @param parameter The parameter that you want passed along to the interval function.
	 * @param delay The delay after which you want the event to occur, in milliseconds.
	 */
	public void callBack( final Object parameter, final long delay )
	{
		callBackReal( parameter, System.currentTimeMillis() + delay, 0 );
	}

	/**
	 * Calls the 'interval' function in the calling plugin, with the specified parameter and after the specified interval.
	 * @param parameter The paramater that you want passed along to the interval function.
	 * @param delay The delay after which you want the event to occour.
	 * @param id The unique id of this interval (-1 for no ID).
	 */
	public void callBack( final Object parameter, final long delay, final int id )
	{
		callBackReal( parameter, System.currentTimeMillis() + delay, id );
	}

	private void callBackReal (final Object parameter, final long when, final int id)
	{
		final String plugin = mods.security.getPluginName(0);
		if (plugin == null)
		{
			logger.error("A plugin tried to call callBack, but wasn't on the stack...");
			return;
		}
		final Interval newInt = new Interval( plugin, parameter, when, id );
		synchronized(intervalList)
		{
			if ( id != -1 )
			{
				// XXX this is damn inefficient...
				final Iterator<Interval> iterator = intervalList.iterator();
				while(iterator.hasNext())
				{
					final Interval thisInt = iterator.next();
					if ( id == thisInt.getId() && plugin.equals(thisInt.getPlugin()) )
						iterator.remove();
				}
			}
			intervalList.add( newInt );
		}
	}

	/**
	 * Clear all intervals from the calling plugin.
	 */
	public void reset ()
	{
		final String plugin = mods.security.getPluginName(0);
		if (plugin == null)
		{
			logger.error("A plugin tried to call reset, but wasn't on the stack...");
			return;
		}
		synchronized(intervalList)
		{
			// XXX this is damn inefficient...
			final Iterator<Interval> iterator = intervalList.iterator();
			while(iterator.hasNext())
			{
				final Interval thisInt = iterator.next();
				if ( plugin.equals(thisInt.getPlugin()) )
					iterator.remove();
			}
		}
	}
}
