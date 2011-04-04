/*
 * ObjectDbTest.java
 *
 * Created on August 6, 2005, 9:10 PM
 */

package uk.co.uwcs.choob.modules;

import java.security.AccessController;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import uk.co.uwcs.choob.support.ChoobError;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.ConnectionBroker;
import uk.co.uwcs.choob.support.ObjectDBDeadlockError;
import uk.co.uwcs.choob.support.ObjectDBTransaction;

/**
 * An interface with the ObjectDB, for use by plugins and core code alike.
 *
 * The ObjectDB provides a simple and clean interface for storing, updating,
 * deleting and - most importantly - retrieving generic objects. Objects need
 * not support any particular interface, nor inherit from any particular class;
 * instead, the only requirement is that they have an "id" property of type
 * "int".<p>
 *
 * All public read/write properties of the following types are loaded and
 * saved by ObjectDB for Java objects:
 *
 * <ul>
 *     <li>Boolean</li>
 *     <li>Byte</li>
 *     <li>Short</li>
 *     <li>Integer</li>
 *     <li>Long</li>
 *     <li>Float</li>
 *     <li>Double</li>
 *     <li>String</li>
 * </ul>
 *
 * For JavaScript objects, properties are always public, so the ObjectDB only
 * saves ones that do not start with an underscore (<tt>_</tt>). The following
 * JavaScript types are saved:
 *
 * <ul>
 *     <li>Boolean</li>
 *     <li>Number</li>
 *     <li>String</li>
 * </ul>
 */
public final class ObjectDbModule
{
	private final ConnectionBroker broker;
	private final Modules mods;

	/** Creates a new instance of ObjectDbModule */
	ObjectDbModule(final ConnectionBroker broker, final Modules mods)
	{
		this.broker = broker;
		this.mods = mods;
	}

	/**
	 * Escapes a string safely for use inside single- or double-quoted strings
	 * in an ObjectDB query.
	 */
	public String escapeString(final String text)
	{
		return text.replaceAll("(\\W)", "\\\\$1");
	}

	/**
	 * Escapes a string safely for use inside single- or double-quoted strings
	 * in a <tt>LIKE</tt> comparison in an ObjectDB query.
	 */
	public String escapeForLike(final String text)
	{
		return escapeString(text).replaceAll("([_%])", "\\\\$1");
	}

	/**
	 * Escapes a string safely for use inside single- or double-quoted strings
	 * in a <tt>RLIKE</tt> comparison in an ObjectDB query.
	 */
	public String escapeForRLike(final String text)
	{
		return escapeString(text);
	}

	/**
	 * Retrieve a list of classes matching the specified classtype and clause.
	 * @param storedClass The .class of the object you want to retrieve.
	 * @param clause The clause specifying which objects you want to select.
	 */
	public <T> List<T> retrieve(final Class<T> storedClass, final String clause)
	{
		Connection dbConn = null;
		try
		{
			dbConn = broker.getConnection();
		}
		catch (final SQLException e)
		{
			throw new ChoobError("Sql Exception", e);
		}
		final ObjectDBTransaction trans = new ObjectDBTransaction();
		trans.setConn(dbConn, broker.getFactories());
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
			runTransaction( new ObjectDBTransaction() { @Override
			public void run() {
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
			runTransaction( new ObjectDBTransaction() { @Override
			public void run() {
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
			runTransaction( new ObjectDBTransaction() { @Override
			public void run() {
				save(strObject);
			} } );
		}
	}

	public void runTransaction( final ObjectDBTransaction trans )
	{
		Connection dbConn = null;
		try
		{
			dbConn = broker.getConnection();
		}
		catch (final SQLException e)
		{
			throw new ChoobError("Sql Exception", e);
		}

		trans.setConn(dbConn, broker.getFactories());
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
				catch (final ObjectDBDeadlockError e)
				{
					// Is it time to give up?
					if (i == 20)
						throw new ObjectDBDeadlockError();

					trans.rollback();
					System.err.println("SQL Deadlock occurred. Rolling back and backing off for " + delay + "ms.");
					try { synchronized(trans) { trans.wait(delay); } } catch (final InterruptedException ie) { }
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

	public void runNonRepeatableTransaction( final ObjectDBTransaction trans )
	{
		Connection dbConn = null;
		try
		{
			dbConn = broker.getConnection();
		}
		catch (final SQLException e)
		{
			throw new ChoobError("Sql Exception", e);
		}

		trans.setConn(dbConn, broker.getFactories());
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
		catch (final SQLException e)
		{
			throw new ChoobError("Sql Exception", e);
		}
	}

	public void freeConnection(final Connection c)
	{
		AccessController.checkPermission(new ChoobPermission("db.connection.checkin"));
		broker.freeConnection( c );
	}
}
