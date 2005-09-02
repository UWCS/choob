package org.uwcs.choob.modules;

import java.util.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import org.uwcs.choob.*;

/**
 * Module that currently only exists for shutup, but should be expandable for any other controls that are needed over channels.
 *
 * @author	Faux
 */
public class ProtectedChannels
{
	Vector channels;

	public ProtectedChannels()
	{
		channels=new Vector();
	}

	public Boolean isProtected (String channel)
	{
		return channels.contains(channel);
	}

	public synchronized void addProtected (String channel)
	{
		channels.add(channel);
	}

	public synchronized void removeProtected (String channel)
	{
		channels.remove(channel);
	}
}
