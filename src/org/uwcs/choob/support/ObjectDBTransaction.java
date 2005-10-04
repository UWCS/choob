/*
 * ObjectDbTest.java
 *
 * Created on August 6, 2005, 9:10 PM
 */

package org.uwcs.choob.support;

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
 *
 * @author	sadiq
 */
public class ObjectDBTransaction
{
	private Connection dbConn;

	/** Creates a new instance of ObjectDbTest */
	/*public ObjectDBTransaction()
	{
	}*/

	public final void setConn(Connection dbConn)
	{
		this.dbConn = dbConn;
	}

	public final void begin() throws ChoobException
	{
		try
		{
			dbConn.setAutoCommit(false);
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
	}

	public final void commit() throws ChoobException
	{
		try
		{
			dbConn.commit();
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
	}

	public final void rollback() throws ChoobException
	{
		try
		{
			dbConn.rollback();
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
	}

	public final void finish() throws ChoobException
	{
		try
		{
			rollback(); // If not committed yet, must roll back changes...
		}
		finally
		{
			try
			{
				dbConn.setAutoCommit(true);
			}
			catch (SQLException e)
			{
				// I'd hate to see this happen...
				throw sqlErr(e);
			}
		}
	}

	private final ChoobException sqlErr(SQLException e)
	{
		System.err.println("Ack! SQL Exception: " + e);
		return new ChoobException("An SQL exception occurred while processing this operation.");
	}

	public final List retrieve(Class storedClass, String clause) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("objectdb."+storedClass.getName().toLowerCase()));
		String sqlQuery;

		if ( clause != null )
		{
			try
			{
				sqlQuery = ObjectDbClauseParser.getSQL(clause, storedClass.getName());
			}
			catch (ParseException e)
			{
				// TODO there's some public properties we can use to make a better error message.
				System.err.println("Parse error in string: " + clause);
				System.err.println("Error was: " + e);
				throw new ChoobException("Parse error in clause string.");
			}
		}
		else
		{
			sqlQuery = "SELECT ObjectStore.ClassID FROM ObjectStore WHERE ClassName = '" + storedClass.getName() + "';";
		}

		try
		{
			ArrayList <Object>objects = new ArrayList<Object>();

			Statement objStat = dbConn.createStatement();

			ResultSet results = objStat.executeQuery( sqlQuery );

			if( results.first() )
			{
				do
				{
					objects.add( retrieveById( storedClass, results.getInt("ClassID") ) );
				}
				while(results.next());
			}

			return objects;
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
	}

	public final List<Integer> retrieveInt(Class storedClass, String clause) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("objectdb."+storedClass.getName().toLowerCase()));
		String sqlQuery;

		if ( clause != null )
		{
			try
			{
				sqlQuery = ObjectDbClauseParser.getSQL(clause, storedClass.getName());
			}
			catch (ParseException e)
			{
				// TODO there's some public properties we can use to make a better error message.
				System.err.println("Parse error in string: " + clause);
				System.err.println("Error was: " + e);
				throw new ChoobException("Parse error in clause string.");
			}
		}
		else
		{
			sqlQuery = "SELECT ObjectStore.ClassID FROM ObjectStore WHERE ClassName = '" + storedClass.getName() + "';";
		}

		System.out.println("Query: " + sqlQuery);

		try
		{
			ArrayList<Integer> objects = new ArrayList<Integer>();

			Statement objStat = dbConn.createStatement();

			ResultSet results = objStat.executeQuery( sqlQuery );

			if( results.first() )
			{
				do
				{
					objects.add( results.getInt(1) );
				}
				while(results.next());
			}

			return objects;
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
	}

	private final Object retrieveById(Class storedClass, int id) throws ChoobException
	{
		try
		{
			PreparedStatement retrieveObject = dbConn.prepareStatement("SELECT * FROM ObjectStore LEFT JOIN ObjectStoreData ON ObjectStore.ObjectID = ObjectStoreData.ObjectID WHERE ClassName = ? AND ClassID = ?;");

			retrieveObject.setString(1, storedClass.getName() );
			retrieveObject.setInt(2, id);

			final ResultSet objSet = retrieveObject.executeQuery();

			if( objSet.first() )
			{
				try
				{
					final Object tempObject = storedClass.newInstance();

					AccessController.doPrivileged( new PrivilegedExceptionAction() {
						public Object run() throws ChoobException {
							populateObject( tempObject, objSet );
							return null;
						}
					});

					return tempObject;
				}
				catch (PrivilegedActionException e)
				{
					throw (ChoobException)e.getCause();
				}
				catch (InstantiationException e)
				{
					System.err.println("Error instantiating object of type " + storedClass + ": " + e);
					throw new ChoobException("The object could not be instantiated");
				}
				catch (IllegalAccessException e)
				{
					System.err.println("Access error instantiating object of type " + storedClass + ": " + e);
					throw new ChoobException("The object could not be instantiated");
				}
			}
			else
				// This should never happen...
				throw new ChoobException("An object found in the database could not later be retrieved");
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
	}

	private final void populateObject( Object tempObject, ResultSet result ) throws ChoobException
	{
		try
		{
			setId( tempObject, result.getInt("ClassID") );

			do
			{
				try
				{
					Field tempField = tempObject.getClass().getField( result.getString("FieldName") );

					Type theType = tempField.getType();

					if( theType == String.class )
					{
						tempField.set( tempObject, result.getString("FieldString") );
					}
					else if( theType == Integer.TYPE )
					{
						tempField.setInt( tempObject, (int)result.getLong("FieldBigInt") );
					}
					else if( theType == Long.TYPE )
					{
						tempField.setLong( tempObject, result.getLong("FieldBigInt") );
					}
					else if( theType == Boolean.TYPE )
					{
						tempField.setBoolean( tempObject, result.getLong("FieldBigInt") == 1 );
					}
					else if( theType == Float.TYPE )
					{
						tempField.setFloat( tempObject, (float)result.getDouble("FieldDouble") );
					}
					else if( theType == Double.TYPE )
					{
						tempField.setDouble( tempObject, result.getDouble("FieldDouble") );
					}
				}
				catch( NoSuchFieldException e )
				{
					// Ignore this, as per spec.
				}
				catch( IllegalAccessException e )
				{
					// Should never happen, but if it does, it's because the field
					// was declared private.
				}
			}
			while( result.next() );
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
	}

	private final int getId( Object obj ) throws ChoobException
	{
		try
		{
			final Object obj2 = obj;
			return (Integer)AccessController.doPrivileged(new PrivilegedExceptionAction() {
				public Object run() throws NoSuchFieldException, IllegalAccessException {
					Field f = obj2.getClass().getField("id");
					return f.getInt( obj2 );
				}
			});
		}
		catch( PrivilegedActionException e )
		{
			// Must be a NoSuchFieldException...
			throw new ChoobException("Class " + obj.getClass() + " does not have a unique 'id' property. Please add one.");
		}
	}

	private final void setId( Object obj, int value ) throws ChoobException
	{
		try
		{
			final Object obj2 = obj;
			final int val2 = value;
			AccessController.doPrivileged(new PrivilegedExceptionAction() {
				public Object run() throws NoSuchFieldException, IllegalAccessException {
					Field f = obj2.getClass().getField("id");
					f.setInt( obj2, val2 );
					return null;
				}
			});
		}
		catch( PrivilegedActionException e )
		{
			// Must be a NoSuchFieldException...
			throw new ChoobException("Class " + obj.getClass() + " does not have a unique 'id' property. Please add one.");
		}
	}

	public final void delete( Object strObj ) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("objectdb."+strObj.getClass().getName().toLowerCase()));
		try
		{
			int id = getId( strObj );

			PreparedStatement retrieveID = dbConn.prepareStatement("SELECT ObjectID FROM ObjectStore WHERE ClassName = ? AND ClassID = ?;");

			retrieveID.setString(1, strObj.getClass().getName() );
			retrieveID.setInt(2, id);

			ResultSet resultID = retrieveID.executeQuery();

			int objectID;

			if( resultID.first() )
			{
				objectID = resultID.getInt("ObjectID");
			}
			else
			{
				throw new ChoobException("Object for deletion does not exist.");
			}

			PreparedStatement delete = dbConn.prepareStatement("DELETE FROM ObjectStore WHERE ObjectID = ?");

			delete.setInt(1, objectID);

			PreparedStatement deleteData = dbConn.prepareStatement("DELETE FROM ObjectStoreData WHERE ObjectID = ?");

			deleteData.setInt(1, objectID);

			deleteData.executeUpdate();

			delete.executeUpdate();
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
	}

	public void update( Object strObject ) throws ChoobException
	{
		delete( strObject );
		save( strObject );
	}

	public void run() throws ChoobException
	{
		throw new ChoobException("This transaction has no run() method...");
	}

	public final void save( Object strObj ) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("objectdb."+strObj.getClass().getName().toLowerCase()));
		try
		{
			int id = getId( strObj );

			if( id == 0 )
			{
				PreparedStatement highestID = dbConn.prepareStatement("SELECT MAX(ClassID) FROM ObjectStore WHERE ClassName = ?;");

				highestID.setString(1, strObj.getClass().getName());

				ResultSet ids = highestID.executeQuery();

				if( ids.first() )
					id = ids.getInt(1)+1;
				else
					id = 1;

				setId( strObj, id );
			}

			PreparedStatement insertObject = dbConn.prepareStatement("INSERT INTO ObjectStore VALUES(NULL,?,?);");

			insertObject.setString(1, strObj.getClass().getName());
			insertObject.setInt(2, id);

			insertObject.execute();

			ResultSet generatedKeys = insertObject.getGeneratedKeys();

			generatedKeys.first();

			int generatedID = generatedKeys.getInt(1);

			PreparedStatement insertField = dbConn.prepareStatement("INSERT INTO ObjectStoreData VALUES(?,?,?,?,?);");

			Field[] fields = strObj.getClass().getFields();

			for( int c = 0 ; c < fields.length ; c++ )
			{
				Field tempField = fields[c];

				if( !tempField.getName().equals("id") )
				{
					boolean foundType = true;

					insertField.setInt(1, generatedID);

					Type theType = tempField.getType();

					try
					{
						if( theType == java.lang.Integer.TYPE )
						{
							int theVal = tempField.getInt(strObj);
							insertField.setString(2, tempField.getName());
							insertField.setLong(3, theVal);
							insertField.setDouble(4, theVal);
							insertField.setString(5, Integer.toString(theVal));
						}
						else if( theType == java.lang.Long.TYPE )
						{
							long theVal = tempField.getLong(strObj);
							insertField.setString(2, tempField.getName());
							insertField.setLong(3, theVal);
							insertField.setDouble(4, theVal);
							insertField.setString(5, Long.toString(theVal));
						}
						else if( theType == java.lang.Boolean.TYPE )
						{
							boolean theVal = tempField.getBoolean(strObj);
							insertField.setString(2, tempField.getName());
							insertField.setLong(3, theVal ? 1 : 0);
							insertField.setDouble(4, theVal ? 1 : 0);
							insertField.setString(5, theVal ? "1" : "0");
						}
						else if( theType == java.lang.Float.TYPE )
						{
							float theVal = tempField.getFloat(strObj);
							insertField.setString(2, tempField.getName());
							insertField.setLong(3, (long)theVal);
							insertField.setDouble(4, theVal);
							insertField.setString(5, Float.toString(theVal));
						}
						else if( theType == java.lang.Double.TYPE )
						{
							double theVal = tempField.getDouble(strObj);
							insertField.setString(2, tempField.getName());
							insertField.setLong(3, (long)theVal);
							insertField.setDouble(4, theVal);
							insertField.setString(5, Double.toString(theVal));
						}
						else if( theType == String.class )
						{
							insertField.setString(2, tempField.getName());
							insertField.setLong(3, 0); // XXX - parse these or not parse these?
							insertField.setDouble(4, 0);
							insertField.setString(5, (String)tempField.get(strObj));
						}
						else
							foundType = false;

						if( foundType )
						{
							insertField.executeUpdate();
						}
					}
					catch ( IllegalAccessException e )
					{
						// Should never happen, but if it does, just ignore.
					}
				}
			}
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
	}
}
