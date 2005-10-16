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
import java.util.List;
import java.util.ArrayList;

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

		PreparedStatement insertLine = null;
		try
		{
			insertLine = dbConnection.prepareStatement("INSERT INTO History VALUES(NULL,?,?,?,?,?,?,?)");

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
			insertLine.setInt(7, mes.getRandom());

			insertLine.executeUpdate();
		}
		catch( SQLException e )
		{
			System.err.println("Could not write history line to database: " + e);
			// I think this exception doesn't need to be propogated... --bucko
		}
		finally
		{
			try
			{
				if (insertLine != null)
					insertLine.close();
			}
			catch (SQLException e)
			{
				System.err.println("Could not close SQL connection: " + e);
			}
			finally
			{
				dbBroker.freeConnection( dbConnection );
			}
		}
	}

	/**
	 * Get the ID of a message object.
	 * @param mes The message object to find.
	 * @return Either a message ID, or -1 if the message didn't exist.
	 */
	public int getMessageID( Message mes ) throws ChoobException
	{
		Connection dbCon = dbBroker.getConnection();
		PreparedStatement stat = null;
		try {
			stat = dbCon.prepareStatement("SELECT LineID FROM History WHERE Type = ? AND Nick = ? AND Hostmask = ? AND Channel = ? AND Text = ? AND Time = ? AND Random = ?");
			stat.setString(1, mes.getClass().getName());
			stat.setString(2, mes.getNick());
			stat.setString(3, mes.getNick()+"@"+mes.getHostname());
			String chan = null;
			if (mes instanceof ChannelEvent)
			{
				chan = ((ChannelEvent)mes).getChannel();
			}
			stat.setString(4, chan);
			stat.setString(5, mes.getMessage());
			stat.setLong(6, mes.getMillis());
			stat.setInt(7, mes.getRandom());

			ResultSet result = stat.executeQuery();

			if ( result.first() )
				return result.getInt(1);
			else
				return -1;
		}
		catch( SQLException e )
		{
			System.err.println("Could not read history line from database: " + e);
			throw new ChoobException("SQL Error reading from database.");
		}
		finally
		{
			try
			{
				if (stat != null)
					stat.close();
			}
			catch (SQLException e)
			{
				System.err.println("Could not read history line from database: " + e);
				throw new ChoobException("SQL Error reading from database.");
			}
			finally
			{
				dbBroker.freeConnection( dbCon );
			}
		}
	}

	/**
	 * Get a historic message object.
	 * @param messageID The message ID, as returned from getMessageID.
	 * @return The message object or null if it didn't exist.
	 */
	public Message getMessage( int messageID ) throws ChoobException
	{
		Connection dbCon = dbBroker.getConnection();
		PreparedStatement stat = null;
		try {
			stat = dbCon.prepareStatement("SELECT * FROM History WHERE LineID = ?");
			stat.setInt(1, messageID);

			final ResultSet result = stat.executeQuery();

			if ( result.first() )
			{
				final String type = result.getString(2);
				int pos = result.getString(4).indexOf('@');
				final String login = result.getString(4).substring(0,pos);
				final String host = result.getString(4).substring(pos+1);
				final String channel = result.getString(5);

				Message mes;
				try
				{
					// Need privs to create events...
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
					System.err.println("Invalid event type: " + type);

				return mes;
			}
			else
				return null;
		}
		catch( SQLException e )
		{
			System.err.println("Could not read history line from database: " + e);
			throw new ChoobException("SQL Error reading from database.");
		}
		finally
		{
			try
			{
				if (stat != null)
					stat.close();
			}
			catch (SQLException e)
			{
				System.err.println("Could not read history line from database: " + e);
				throw new ChoobException("SQL Error reading from database.");
			}
			finally
			{
				dbBroker.freeConnection( dbCon );
			}
		}
	}

	/**
	 * Get as the most recent Message from the history of the channel in which cause occurred.
	 * @param cause The "cause" - only messages that occurred before this are processed
	 * @return A list of message objects, the first being the most recent.
	 */
	public Message getLastMessage( Message cause ) throws ChoobException
	{
		if (cause instanceof ChannelEvent)
			return getLastMessage(((ChannelEvent)cause).getChannel(), cause);
		throw new IllegalArgumentException("Message passed to getLastMessage was not a ChannelEvent.");
	}

	/**
	 * Get as the most recent Message from the history of channel.
	 * @param channel The channel to read
	 * @return A list of message objects, the first being the most recent.
	 */
	public Message getLastMessage( String channel ) throws ChoobException
	{
		return getLastMessage(channel, null);
	}

	/**
	 * Get as the most recent Message from the history of channel.
	 * @param channel The channel to read
	 * @param cause The "cause" - only messages that occurred before this are processed
	 * @return A list of message objects, the first being the most recent.
	 */
	public Message getLastMessage( String channel, Message cause ) throws ChoobException
	{
		List<Message> ret = getLastMessages(channel, cause, 1);
		if (ret.size() == 1)
			return ret.get(0);
		else
			return null;
	}

	/**
	 * Get as many as possible up to count Messages from the history of the channel in which cause occurred.
	 * @param channel The channel to read
	 * @param count The maximal number of messages to return.
	 * @return A list of message objects, the first being the most recent.
	 */
	public List<Message> getLastMessages( String channel, int count ) throws ChoobException
	{
		return getLastMessages(channel, null, count);
	}

	/**
	 * Get as many as possible up to count Messages from the history of the channel in which cause occurred.
	 * @param cause The "cause" - only messages that occurred before this are processed
	 * @param count The maximal number of messages to return.
	 * @return A list of message objects, the first being the most recent.
	 */
	public List<Message> getLastMessages( Message cause, int count ) throws ChoobException
	{
		if (cause instanceof ChannelEvent)
			return getLastMessages(((ChannelEvent)cause).getChannel(), cause, count);
		throw new IllegalArgumentException("Message passed to getLastMessages was not a ChannelEvent.");
	}

	/**
	 * Get as many as possible up to count Messages from the history of channel.
	 * @param channel The channel to read
	 * @param cause The "cause" - only messages that occurred before this are processed
	 * @param count The maximal number of messages to return.
	 * @return A list of message objects, the first being the most recent.
	 */
	public List<Message> getLastMessages( final String channel, Message cause, int count ) throws ChoobException
	{
		Connection dbCon = dbBroker.getConnection();
		PreparedStatement stat = null;
		try {
			stat = dbCon.prepareStatement("SELECT * FROM History WHERE Channel = ? AND Time < ? ORDER BY Time DESC LIMIT ?");
			stat.setString(1, channel);
			stat.setLong(2, cause == null ? System.currentTimeMillis() : cause.getMillis() );
			stat.setInt(3, count);

			final ResultSet result = stat.executeQuery();

			List<Message> results = new ArrayList<Message>(count);

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
						// Need privs to create events...
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

					/* Sanity check; since we use <, this shouldn't happen...
					if (mes.equals(cause))
					{
						System.out.println("Skipping event...");
						continue;
					}//*/

					results.add(mes);
				}
				while( result.next() );
			}
			return results;
		}
		catch( SQLException e )
		{
			System.err.println("Could not read history line from database: " + e);
			throw new ChoobException("SQL Error reading from database.");
		}
		finally
		{
			try
			{
				if (stat != null)
					stat.close();
			}
			catch (SQLException e)
			{
				System.err.println("Could not read history line from database: " + e);
				throw new ChoobException("SQL Error reading from database.");
			}
			finally
			{
				dbBroker.freeConnection( dbCon );
			}
		}
	}
}
