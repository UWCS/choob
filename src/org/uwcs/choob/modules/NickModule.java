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
	 * Gets the User's ID number from a (possibly linked) nick.
	 * @param nick String representing the Nick.
	 * @return User's ID, or 0 if not found.
	 */

	public int getUserID(String nick) throws Exception
	{
		try
		{
			Connection dbConnection = dbBroker.getConnection();

			PreparedStatement insertLine = dbConnection.prepareStatement("SELECT `UserID` FROM `AddNicks` WHERE ? LIKE `Nick` LIMIT 1;");

			insertLine.setString(1, nick);

			ResultSet nickSet = insertLine.executeQuery();

			if ( nickSet.first() )
			{
				dbBroker.freeConnection( dbConnection );
				return nickSet.getInt("UserID");
			}

			dbBroker.freeConnection( dbConnection );

			return 0; // Better way to handle this?
		}
		catch( Exception e )
		{
			throw new Exception("Could not read nick information from database.", e);
		}

	}

	/**
	 * Gets the User's primary nick from a (possibly linked) nick.
	 * @param nick String representing the Nick.
	 * @return User's primary nick, or "" if not found.
	 */
	public String getPrimaryNick(String nick) throws Exception
	{
		try
		{
			Connection dbConnection = dbBroker.getConnection();

			PreparedStatement insertLine = dbConnection.prepareStatement("SELECT `User`.`UserNick` FROM `AddNicks`,`User` WHERE ? LIKE `Nick` AND `User`.`UserID`=`AddNicks`.`UserId` LIMIT 1;");

			insertLine.setString(1, nick);

			ResultSet nickSet = insertLine.executeQuery();

			if ( nickSet.first() )
			{
				dbBroker.freeConnection( dbConnection );
				return nickSet.getString("UserNick");
			}

			dbBroker.freeConnection( dbConnection );

			return ""; // Better way to handle this?
		}
		catch( Exception e )
		{
			throw new Exception("Could not read nick information from database.", e);
		}
	}

	/**
	 * Guesses the user's primary nick.
	 * @param nick String representing the Nick.
	 * @return User's primary nick, a guess at their primary nick, or the supplied nick.
	 */
	public String getBestPrimaryNick(String nick) throws Exception
	{
		try
		{
			String gpn=getPrimaryNick(nick);
			if (!gpn.equals(""))
				return gpn;
		}
		catch (Exception e) {}

		Pattern pa = Pattern.compile("^([a-zA-Z0-9_-]+?)(?:\\||\\`).*$");
		Matcher ma = pa.matcher(nick);

		if (ma.matches())
			return ma.group(1);

		return nick;
	}
}
