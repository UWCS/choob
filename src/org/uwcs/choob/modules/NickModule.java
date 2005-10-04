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
 *
 * @author	Faux
 */

public class NickModule
{
	DbConnectionBroker dbBroker;

	/** Creates a new instance of NickModule */
	public NickModule(DbConnectionBroker dbBroker)
	{
		this.dbBroker = dbBroker;
	}

	/**
	 * Gets the User's primary nick from a (possibly linked) nick.
	 * @param nick String representing the Nick.
	 * @return User's primary nick, or "" if not found.
	 */
	public String getPrimaryNick(String nick) throws ChoobException
	{
		Connection dbConnection = dbBroker.getConnection();

		PreparedStatement insertLine = null;

		try
		{
			insertLine = dbConnection.prepareStatement("SELECT `User`.`UserNick` FROM `AddNicks`,`User` WHERE ? LIKE `Nick` AND `User`.`UserID`=`AddNicks`.`UserId` LIMIT 1;");

			insertLine.setString(1, nick);

			ResultSet nickSet = insertLine.executeQuery();

			if ( nickSet.first() )
			{
				return nickSet.getString("UserNick");
			}

			return "";
		}
		catch( SQLException e )
		{
			System.err.println("SQL exception while reading nick information: " + e);
			throw new ChoobException("Could not read nick information from database.");
		}
		finally
		{
			try {
				if (insertLine != null)
					insertLine.close();
			}
			catch (SQLException e)
			{
				System.err.println("SQL exception while closing connection: " + e);
				throw new ChoobException("Could not read nick information from database.");
			}
			finally
			{
				dbBroker.freeConnection( dbConnection );
			}
		}
	}

	/**
	 * Guesses the user's primary nick.
	 * @param nick String representing the Nick.
	 * @return User's primary nick, a guess at their primary nick, or the supplied nick.
	 */
	public String getBestPrimaryNick(String nick)
	{
		try
		{
			String gpn=getPrimaryNick(nick);
			if (!gpn.equals(""))
				return gpn;
		}
		catch (ChoobException e)
		{}

		Pattern pa = Pattern.compile("^([a-zA-Z0-9_-]+?)(?:\\||\\`).*$");
		Matcher ma = pa.matcher(nick);

		if (ma.matches())
			return ma.group(1);

		return nick;
	}
}
