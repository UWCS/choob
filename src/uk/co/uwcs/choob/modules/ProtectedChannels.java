package uk.co.uwcs.choob.modules;

import java.util.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import uk.co.uwcs.choob.*;

/**
 * Module that currently only exists for shutup, but should be expandable for any other controls that are needed over channels.
 *
 * @author	Faux
 */
public final class ProtectedChannels
{
	List <String>channels=Collections.synchronizedList(new ArrayList<String>());

	public boolean isProtected (String channel)
	{
		return channels.contains(channel);
	}

	public void addProtected (String channel)
	{
		channels.add(channel);
	}

	public void removeProtected (String channel)
	{
		channels.remove(channel);
	}
}
