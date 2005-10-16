/*
 * IntervalModule.java
 *
 * Created on August 5, 2005, 11:25 PM
 */

package org.uwcs.choob.modules;

import java.util.*;
import org.uwcs.choob.support.*;

/**
 * Generates a call-back to a plugin at a specified point in time.
 */
public class IntervalModule
{
	List <Interval> intervalList;
	Modules mods;

	/** Creates a new instance of IntervalModule */
	public IntervalModule( List <Interval> intervalList, Modules mods )
	{
		this.intervalList = intervalList;
		this.mods = mods;
	}

	/**
	 * Calls the 'interval' function in the calling plugin, with the specified parameter and after the specified interval.
	 * @param parameter The paramater that you want passed along to the interval function.
	 * @param interval The Date at which you want the event to occour.
	 */
	public void callBack( Object parameter, Date interval )
	{
		callBackReal( parameter, interval.getTime(), 0 );
	}

	/**
	 * Calls the 'interval' function in the calling plugin, with the specified parameter and after the specified interval.
	 * @param parameter The paramater that you want passed along to the interval function.
	 * @param interval The Date at which you want the event to occour.
	 * @param id The unique id of this interval (-1 for no ID).
	 */
	public void callBack( Object parameter, Date interval, int id )
	{
		callBackReal( parameter, interval.getTime(), id );
	}

	/**
	 * Calls the 'interval' function in the calling plugin, with the specified parameter and after the specified interval.
	 * @param parameter The paramater that you want passed along to the interval function.
	 * @param delay The delay after which you want the event to occour.
	 */
	public void callBack( Object parameter, long delay )
	{
		callBackReal( parameter, System.currentTimeMillis() + delay, 0 );
	}

	/**
	 * Calls the 'interval' function in the calling plugin, with the specified parameter and after the specified interval.
	 * @param parameter The paramater that you want passed along to the interval function.
	 * @param delay The delay after which you want the event to occour.
	 * @param id The unique id of this interval (-1 for no ID).
	 */
	public void callBack( Object parameter, long delay, int id )
	{
		callBackReal( parameter, System.currentTimeMillis() + delay, id );
	}

	private void callBackReal (Object parameter, long when, int id)
	{
		String plugin = mods.security.getPluginName(0);
		if (plugin == null)
		{
			System.err.println("A plugin tried to call callBack, but wasn't on the stack...");
			return;
		}
		Interval newInt = new Interval( plugin, parameter, when, id );
		synchronized(intervalList)
		{
			if ( id != -1 )
			{
				// XXX this is damn inefficient...
				Iterator<Interval> iterator = intervalList.iterator();
				while(iterator.hasNext())
				{
					Interval thisInt = iterator.next();
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
		String plugin = mods.security.getPluginName(0);
		if (plugin == null)
		{
			System.err.println("A plugin tried to call reset, but wasn't on the stack...");
			return;
		}
		synchronized(intervalList)
		{
			// XXX this is damn inefficient...
			Iterator<Interval> iterator = intervalList.iterator();
			while(iterator.hasNext())
			{
				Interval thisInt = iterator.next();
				if ( plugin.equals(thisInt.getPlugin()) )
					iterator.remove();
			}
		}
	}
}
