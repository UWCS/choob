/*
 * ObjectDbTest.java
 *
 * Created on August 6, 2005, 9:10 PM
 */

package org.uwcs.choob.modules;

import org.uwcs.choob.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.ParseException;
import org.uwcs.choob.modules.*;
import java.util.*;
import bsh.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.reflect.*;
import java.lang.reflect.Constructor;
import java.util.regex.*;
import java.sql.*;
import java.lang.String;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * An interface with the ObjectDB.
 */
public final class ObjectDbModule
{
	private DbConnectionBroker broker;
	private Modules mods;

	/** Creates a new instance of ObjectDbModule */
	public ObjectDbModule(DbConnectionBroker broker, Modules mods)
	{
		this.broker = broker;
		this.mods = mods;
	}

	/**
	 * Retrieve a list of classes matching the specified classtype and clause.
	 * @param storedClass The .class of the object you want to retrieve.
	 * @param clause The clause specifying which objects you want to select.
	 */
	public List retrieve(Class storedClass, String clause) throws ChoobException
	{
		Connection dbConn = null;
		try
		{
			dbConn = broker.getConnection();
		}
		catch (SQLException e)
		{
			throw new ChoobException("Sql Exception", e);
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

	public List<Integer> retrieveInt(Class storedClass, String clause) throws ChoobException
	{
		Connection dbConn = null;
		try
		{
			dbConn = broker.getConnection();
		}
		catch (SQLException e)
		{
			throw new ChoobException("Sql Exception", e);
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

	/**
	 * Delete a specific object from the database.
	 * @param strObject The object to delete.
	 */
	public void delete( Object strObject ) throws ChoobException
	{
		synchronized( strObject.getClass() )
		{
			Connection dbConn = null;
			try
			{
				dbConn = broker.getConnection();
			}
			catch (SQLException e)
			{
				throw new ChoobException("Sql Exception", e);
			}

			ObjectDBTransaction trans = new ObjectDBTransaction();
			trans.setConn(dbConn);
			trans.setMods(mods);
			try
			{
				trans.delete( strObject );
			}
			finally
			{
				broker.freeConnection( dbConn );
			}
		}
	}

	/**
	 * Update a changed object back to the database.
	 * @param strObject The object to update.
	 */
	public void update( Object strObject ) throws ChoobException
	{
		synchronized( strObject.getClass() )
		{
			Connection dbConn = null;
			try
			{
				dbConn = broker.getConnection();
			}
			catch (SQLException e)
			{
				throw new ChoobException("Sql Exception", e);
			}

			ObjectDBTransaction trans = new ObjectDBTransaction();
			trans.setConn(dbConn);
			trans.setMods(mods);
			try
			{
				trans.begin();
				trans.delete( strObject );
				trans.save( strObject );
				trans.commit();
			}
			finally
			{
				trans.finish();
				broker.freeConnection( dbConn );
			}
		}
	}

	/**
	 * Save a new object to the database.
	 * @param strObject The object to save.
	 */
	public void save( Object strObject ) throws ChoobException
	{
		synchronized( strObject.getClass() )
		{
			Connection dbConn = null;
			try
			{
				dbConn = broker.getConnection();
			}
			catch (SQLException e)
			{
				throw new ChoobException("Sql Exception", e);
			}

			ObjectDBTransaction trans = new ObjectDBTransaction();
			trans.setConn(dbConn);
			trans.setMods(mods);
			try
			{
				trans.begin();
				trans.save( strObject );
				trans.commit();
			}
			finally
			{
				trans.finish();
				broker.freeConnection( dbConn );
			}
		}
	}

	public void runTransaction( ObjectDBTransaction trans ) throws ChoobException
	{
		Connection dbConn = null;
		try
		{
			dbConn = broker.getConnection();
		}
		catch (SQLException e)
		{
			throw new ChoobException("Sql Exception", e);
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
}
