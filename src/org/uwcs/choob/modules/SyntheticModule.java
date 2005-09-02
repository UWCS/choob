/*
 * IntervalModule.java
 *
 * Created on August 5, 2005, 11:25 PM
 */

package org.uwcs.choob.modules;

import java.util.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import org.uwcs.choob.*;

/**
 *
 * @author	sadiq
 */
public class SyntheticModule
{
	Choob bot;

	/** Creates a new instance of IntervalModule */
	public SyntheticModule( Choob bot )
	{
		this.bot = bot;
	}

	public void doSyntheticMessage( Message mes )
	{
		if( System.getSecurityManager() != null )
		{
			System.getSecurityManager().checkPermission(new ChoobPermission("canCreateEvents"));
		}

		bot.onSyntheticMessage( mes );
	}
}
