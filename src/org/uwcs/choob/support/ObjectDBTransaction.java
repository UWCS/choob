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
			ArrayList objects = new ArrayList();

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

					if( result.getString("FieldType").equals("String") )
					{
						tempField.set( tempObject, result.getString("FieldValue") );
					}
					else if( result.getString("FieldType").equals("int") )
					{
						tempField.setInt( tempObject, Integer.parseInt( result.getString("FieldValue")) );
					}
					else if( result.getString("FieldType").equals("long") )
					{
						tempField.setLong( tempObject, Long.parseLong( result.getString("FieldValue")) );
					}
					else if( result.getString("FieldType").equals("boolean") )
					{
						tempField.setBoolean( tempObject, result.getString("FieldValue").equals("1") );
					}
					else if( result.getString("FieldType").equals("float") )
					{
						tempField.setFloat( tempObject, Float.parseFloat( result.getString("FieldValue")) );
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

			PreparedStatement insertField = dbConn.prepareStatement("INSERT INTO ObjectStoreData VALUES(?,?,?,?);");

			Field[] fields = strObj.getClass().getFields();

			for( int c = 0 ; c < fields.length ; c++ )
			{
				Field tempField = fields[c];

				if( !tempField.getName().equals("id") )
				{
					boolean foundType = false;

					insertField.setInt(1, generatedID);

					try
					{
						if( tempField.getType() == java.lang.Integer.TYPE )
						{
							foundType = true;
							insertField.setString(2, tempField.getName());
							insertField.setString(3, "int");
							insertField.setString(4, Integer.toString(tempField.getInt(strObj)));
						}
						else if( tempField.getType() == java.lang.Long.TYPE )
						{
							foundType = true;
							insertField.setString(2, tempField.getName());
							insertField.setString(3, "long");
							insertField.setString(4, Long.toString(tempField.getLong(strObj)));
						}
						else if( tempField.getType() == java.lang.Boolean.TYPE )
						{
							foundType = true;
							insertField.setString(2, tempField.getName());
							insertField.setString(3, "boolean");
							insertField.setString(4, tempField.getBoolean(strObj) ? "1" : "0");
						}
						else if( tempField.getType() == java.lang.Float.TYPE )
						{
							foundType = true;
							insertField.setString(2, tempField.getName());
							insertField.setString(3, "float");
							insertField.setString(4, Float.toString(tempField.getFloat(strObj)));
						}
						else if( tempField.getType() == String.class )
						{
							foundType = true;
							insertField.setString(2, tempField.getName());
							insertField.setString(3, "String");
							insertField.setString(4, (String)tempField.get(strObj));
						}

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
