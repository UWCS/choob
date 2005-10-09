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

	/** Creates a new instance of IntervalModule */
	public IntervalModule( List <Interval> intervalList )
	{
		this.intervalList = intervalList;
	}

	/**
	 * Calls the 'interval' function in the specified plugin, with the specified parameter and after the specified interval.
	 * @param plugin The plugin in which to call the function.
	 * @param parameter The paramater that you want passed along to the interval function.
	 * @param interval The Date at which you want the event to occour.
	 */
	public void callBack( Object plugin, Object parameter, Date interval )
	{
		Interval newInt = new Interval();

		newInt.setPlugin(plugin.getClass().getSimpleName());
		newInt.setTrigger( interval );
		newInt.setParameter( parameter );

		intervalList.add( newInt );
	}
}
