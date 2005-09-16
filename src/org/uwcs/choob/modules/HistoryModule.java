/*
 * LoggerModule.java
 *
 * Created on June 25, 2005, 10:29 PM
 */

package org.uwcs.choob.modules;

import org.uwcs.choob.*;
import java.sql.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.security.*;

/**
 * Logs lines from IRC to the database.
 * @author sadiq
 */
public class HistoryModule
{
	DbConnectionBroker dbBroker;

	/** Creates a new instance of LoggerModule */
	public HistoryModule(DbConnectionBroker dbBroker)
	{
		this.dbBroker = dbBroker;
	}

	/**
	 * Logs a line from IRC to the database.
	 * @param mes {@link Message} object representing the line from IRC.
	 * @throws Exception Thrown from the database access, potential SQL or IO exceptions.
	 */
	public void addLog( Message mes )
	{
		AccessController.checkPermission(new ChoobPermission("history.add"));

		Connection dbConnection = dbBroker.getConnection();

		try
		{
			PreparedStatement insertLine = dbConnection.prepareStatement("INSERT INTO History VALUES(NULL,?,?,?,?,?,?,?)");

			insertLine.setString(1, mes.getClass().getName());
			insertLine.setString(2, mes.getNick());
			insertLine.setString(3, mes.getNick()+"@"+mes.getHostname());
			String chan = null;
			if (mes instanceof ChannelEvent)
			{
				chan = ((ChannelEvent)mes).getChannel();
			}
			insertLine.setString(4, chan);
			insertLine.setString(5, mes.getMessage());
			insertLine.setLong(6, mes.getMillis());
			insertLine.setInt(7, mes.getRandom()); // XXX Why?

			insertLine.executeUpdate();
		}
		catch( SQLException e )
		{
			System.err.println("Could not write history line to database: " + e);
			// I think this exception doesn't need to be propogated... --bucko
		}
		finally
		{
			dbBroker.freeConnection( dbConnection );
		}
	}

	public Message getLastMessage( String channel )
	{
		return getLastMessage(channel, null);
	}

	public Message getLastMessage( final String channel, Message skip )
	{
		Connection dbCon = dbBroker.getConnection();
		try {
			// XXX Race condition: We should probably check for only messages
			// BEFORE skip. Otherwise all hell might break loose under heavy
			// load.
			PreparedStatement stat = dbCon.prepareStatement("SELECT * FROM History WHERE Channel = ? ORDER BY Time DESC LIMIT 2");
			stat.setString(1, channel);

			final ResultSet result = stat.executeQuery();

			if ( result.first() )
			{
				do
				{
					final String type = result.getString(2);
					int pos = result.getString(4).indexOf('@');
					final String login = result.getString(4).substring(0,pos);
					final String host = result.getString(4).substring(pos+1);

					Message mes;
					try
					{
						mes = (Message)AccessController.doPrivileged( new PrivilegedExceptionAction() {
							public Object run() throws SQLException {
								if (type.equals(ChannelAction.class.getName()))
									return new ChannelAction("onAction", result.getLong(7), result.getInt(8), result.getString(6), result.getString(3), login, host, channel, channel);
								else if (type.equals(ChannelMessage.class.getName()))
									return new ChannelMessage("onMessage", result.getLong(7), result.getInt(8), result.getString(6), result.getString(3), login, host, channel, channel);
								return null;
							}
						});
					}
					catch (PrivilegedActionException e)
					{
						throw (SQLException)e.getCause();
						// Is an SQL exception...
					}

					if (mes == null)
					{
						System.err.println("Invalid event type: " + type);
						continue;
					}
					if (!mes.equals(skip))
						return mes;
					System.out.println("Skipping event...");
				}
				while( result.next() );
			}
			return null;
		}
		catch( SQLException e )
		{
			System.err.println("Could not read history line from database: " + e);
			return null; // I guess we can ignore this...
			//throw new ChoobException("SQL Exception while fetching line from the database...");
		}
		finally
		{
			dbBroker.freeConnection( dbCon );
		}
	}
}
