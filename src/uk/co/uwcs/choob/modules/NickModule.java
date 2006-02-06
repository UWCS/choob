/*
 * NickModule.java
 *
 * Created on August 1, 2005, 01:26 PM
 */

package uk.co.uwcs.choob.modules;

import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.plugins.*;
import uk.co.uwcs.choob.modules.*;
import java.sql.*;
import uk.co.uwcs.choob.support.*;
import java.util.*;
import java.util.regex.*;
import org.jibble.pircbot.User;

/**
 * Helper functions for dealing with users' nicks.
 */

public final class NickModule
{
	private DbConnectionBroker dbBroker;

	static final Pattern jbNickPattern = Pattern.compile("^([a-zA-Z0-9_-]+?)(?:\\||\\`).*$");
	static final Pattern irssiNickPattern = Pattern.compile("^_*([a-zA-Z0-9_-]+?)_*$");
	private Choob bot;

	NickModule( Choob bot )
	{
		this.bot = bot;
	}

	/**
	 * Guesses the user's primary nick.
	 * @param nick String representing the Nick.
	 * @return User's primary nick, a guess at their primary nick, or the supplied nick.
	 */
	public String getBestPrimaryNick(String nick)
	{
		Matcher ma = jbNickPattern.matcher(nick);

		if (ma.matches())
			return ma.group(1);

		ma=irssiNickPattern.matcher(nick);

		if (ma.matches())
			return ma.group(1);

		return nick;
	}


	/**
	 * Checks if the user is in the specified channel.
	 * @param nick String representing the Nick.
	 * @param channel String representing the Channel.
	 * @return User's primary nick, a guess at their primary nick, or the supplied nick.
	 */

	public Boolean isInChannel(String nick,String channel)
	{
		User[] usersInChannel = bot.getUsers(channel);
		
		try{
		for (int i = 0; i < usersInChannel.length; i++)
		{
			if (usersInChannel[i].getNick().equals(nick)) { return new Boolean(true); }
		}
		}
		catch (NullPointerException e)	{ return new Boolean(false);}

		return new Boolean(false);
	}
}
