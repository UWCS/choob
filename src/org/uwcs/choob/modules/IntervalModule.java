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
		String plugin = mods.security.getPluginName(0);
		Interval newInt = new Interval( plugin, parameter, interval.getTime() );
		intervalList.add( newInt );
	}

	/**
	 * Calls the 'interval' function in the calling plugin, with the specified parameter and after the specified interval.
	 * @param parameter The paramater that you want passed along to the interval function.
	 * @param delay The delay after which you want the event to occour.
	 */
	public void callBack( Object parameter, long delay )
	{
		String plugin = mods.security.getPluginName(0);
		Interval newInt = new Interval( plugin, parameter, System.currentTimeMillis() + delay );
		intervalList.add( newInt );
	}
}
