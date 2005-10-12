/*
 * NickModule.java
 *
 * Created on August 1, 2005, 01:26 PM
 */

package org.uwcs.choob.modules;

import org.uwcs.choob.*;
import org.uwcs.choob.plugins.*;
import org.uwcs.choob.modules.*;
import java.sql.*;
import org.uwcs.choob.support.*;
import java.util.*;
import java.util.regex.*;

/**
 * Helper functions for dealing with users' nicks.
 */

public class NickModule
{
	DbConnectionBroker dbBroker;

	static final Pattern jbNickPattern = Pattern.compile("^([a-zA-Z0-9_-]+?)(?:\\||\\`).*$");
	static final Pattern irssiNickPattern = Pattern.compile("^_*([a-zA-Z0-9_-]+?)_*$");

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
}
