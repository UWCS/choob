/*
 * IntervalModule.java
 *
 * Created on August 5, 2005, 11:25 PM
 */

package org.uwcs.choob.modules;

import java.util.*;
import org.uwcs.choob.support.*;

/**
 *
 * @author	sadiq
 */
public class IntervalModule
{
	List intervalList;

	/** Creates a new instance of IntervalModule */
	public IntervalModule( List intervalList )
	{
		this.intervalList = intervalList;
	}

	public void callBack( Object plugin, Object parameter, Date interval )
	{
		Interval newInt = new Interval();

		newInt.setPlugin(plugin.getClass().getName());
		newInt.setTrigger( interval );
		newInt.setParameter( parameter );

		intervalList.add( newInt );
	}
}
