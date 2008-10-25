package uk.co.uwcs.choob.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Module that currently only exists for shutup, but should be expandable for any other controls that are needed over channels.
 *
 * @author	Faux
 */
public final class ProtectedChannels
{
	List <String>channels=Collections.synchronizedList(new ArrayList<String>());

	public boolean isProtected (final String channel)
	{
		return channels.contains(channel);
	}

	public void addProtected (final String channel)
	{
		channels.add(channel);
	}

	public void removeProtected (final String channel)
	{
		channels.remove(channel);
	}
}
