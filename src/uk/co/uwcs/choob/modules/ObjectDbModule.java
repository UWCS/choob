/*
 * ObjectDbTest.java
 *
 * Created on August 6, 2005, 9:10 PM
 */

package uk.co.uwcs.choob.modules;

import uk.co.uwcs.choob.support.*;
import java.util.*;
import java.sql.*;
import java.lang.String;
import java.security.AccessController;

/**
 * An interface with the ObjectDB.
 */
public final class ObjectDbModule
{
	private DbConnectionBroker broker;
	private Modules mods;

	/** Creates a new instance of ObjectDbModule */
	ObjectDbModule(DbConnectionBroker broker, Modules mods)
	{
		this.broker = broker;
		this.mods = mods;
	}

	public String escapeString(String text)
	{
		return text.replaceAll("(\\W)", "\\\\$1");
	}

	public String escapeForLike(String text)
	{
		return escapeString(text).replaceAll("([_%])", "\\\\$1");
	}

	public String escapeForRLike(String text)
	{
		return escapeString(text);
	}

	/**
	 * Retrieve a list of classes matching the specified classtype and clause.
	 * @param storedClass The .class of the object you want to retrieve.
	 * @param clause The clause specifying which objects you want to select.
	 */
	public List retrieve(Class storedClass, String clause)
	{
		return retrieve((Object)storedClass, clause);
	}

	public List retrieve(Object storedClass, String clause)
	{
		Connection dbConn = null;
		try
		{
			dbConn = broker.getConnection();
		}
		catch (SQLException e)
		{
			throw new ChoobError("Sql Exception", e);
		}
		ObjectDBTransaction trans = new ObjectDBTransaction();
		trans.setConn(dbConn);
		trans.setMods(mods);
		try
		{
			return trans.retrieve(storedClass, clause);
		}
		finally
		{
			broker.freeConnection( dbConn );
		}
	}

	/**
	 * This doesn't work.

	public List<Integer> retrieveInt(Class storedClass, String clause)
	{
		return retrieveInt((Object)storedClass, clause);
	}

	public List<Integer> retrieveInt(Object storedClass, String clause)
	{
		Connection dbConn = null;
		try
		{
			dbConn = broker.getConnection();
		}
		catch (SQLException e)
		{
			throw new ChoobError("Sql Exception", e);
		}
		ObjectDBTransaction trans = new ObjectDBTransaction();
		trans.setConn(dbConn);
		trans.setMods(mods);
		try
		{
			return trans.retrieveInt(storedClass, clause);
		}
		finally
		{
			broker.freeConnection( dbConn );
		}
	}

	 */

	/**
	 * Delete a specific object from the database.
	 * @param strObject The object to delete.
	 */
	public void delete( final Object strObject )
	{
		synchronized( strObject.getClass() )
		{
			runTransaction( new ObjectDBTransaction() { public void run() {
				delete(strObject);
			} } );
		}
	}

	/**
	 * Update a changed object back to the database.
	 * @param strObject The object to update.
	 */
	public void update( final Object strObject )
	{
		synchronized( strObject.getClass() )
		{
			runTransaction( new ObjectDBTransaction() { public void run() {
				update(strObject);
			} } );
		}
	}

	/**
	 * Save a new object to the database.
	 * @param strObject The object to save.
	 */
	public void save( final Object strObject )
	{
		synchronized( strObject.getClass() )
		{
			runTransaction( new ObjectDBTransaction() { public void run() {
				save(strObject);
			} } );
		}
	}

	public void runTransaction( ObjectDBTransaction trans )
	{
		Connection dbConn = null;
		try
		{
			dbConn = broker.getConnection();
		}
		catch (SQLException e)
		{
			throw new ChoobError("Sql Exception", e);
		}

		trans.setConn(dbConn);
		trans.setMods(mods);
		try
		{
			// Attempt up to 20 backoffs with initial delay 100ms and exponent 1.3.
			int delay = 100;
			for(int i=0; i<21; i++)
			{
				try
				{
					trans.begin();
					trans.run();
					trans.commit();
					break;
				}
				catch (ObjectDBDeadlockError e)
				{
					// Is it time to give up?
					if (i == 20)
						throw new ObjectDBDeadlockError();

					trans.rollback();
					System.err.println("SQL Deadlock occurred. Rolling back and backing off for " + delay + "ms.");
					try { synchronized(trans) { trans.wait(delay); } } catch (InterruptedException ie) { }
					delay *= 1.3; // XXX Makes baby LucidIon cry.
				}
			}
		}
		finally
		{
			trans.finish();
			broker.freeConnection( dbConn );
		}
	}

	public void runNonRepeatableTransaction( ObjectDBTransaction trans )
	{
		Connection dbConn = null;
		try
		{
			dbConn = broker.getConnection();
		}
		catch (SQLException e)
		{
			throw new ChoobError("Sql Exception", e);
		}

		trans.setConn(dbConn);
		trans.setMods(mods);
		try
		{
			trans.begin();
			trans.run();
			trans.commit();
		}
		finally
		{
			trans.finish();
			broker.freeConnection( dbConn );
		}
	}

	public Connection getConnection()
	{
		AccessController.checkPermission(new ChoobPermission("db.connection.checkout"));

		try
		{
			return broker.getConnection();
		}
		catch (SQLException e)
		{
			throw new ChoobError("Sql Exception", e);
		}
	}

	public void freeConnection(Connection c)
	{
		AccessController.checkPermission(new ChoobPermission("db.connection.checkin"));
		broker.freeConnection( c );
	}
}
