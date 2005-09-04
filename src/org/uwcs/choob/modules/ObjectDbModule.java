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
 *
 * @author	sadiq
 */
public class ObjectDbModule
{
	DbConnectionBroker broker;

	/** Creates a new instance of ObjectDbTest */
	public ObjectDbModule(DbConnectionBroker broker)
	{
		this.broker = broker;
	}

	private void tidyUp(Connection dbConn) throws ChoobException
	{
		try
		{
			dbConn.rollback();
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
		finally
		{
			try
			{
				dbConn.setAutoCommit(true);
			}
			catch (SQLException e)
			{
				throw sqlErr(e);
			}
			finally
			{
				broker.freeConnection(dbConn);
			}
		}
	}

	private ChoobException sqlErr(SQLException e)
	{
		System.err.println("Ack! SQL Exception: " + e);
		return new ChoobException("An SQL exception occurred while processing this operation.");
	}

	public List retrieve(Class storedClass, String clause) throws ChoobException
	{
		String sqlQuery = new String("SELECT ObjectStore.ClassID FROM ObjectStore ");

		if ( clause != null )
		{
			try
			{
				sqlQuery += ObjectDbClauseParser.getSQL(clause);
			}
			catch (ParseException e)
			{
				// TODO there's some public properties we can use to make a better error message.
				System.err.println("Parse error in string: " + clause);
				System.err.println("Error was: " + e);
				throw new ChoobException("Parse error in clause string.");
			}
			sqlQuery += " AND ClassName = '" + storedClass.toString() + "';";
		}
		else
		{
			sqlQuery += "WHERE ClassName = '" + storedClass.toString() + "';";
		}

		Connection dbConnection = broker.getConnection();

		try
		{
			ArrayList objects = new ArrayList();

			Statement objStat = dbConnection.createStatement();

			ResultSet results = objStat.executeQuery( sqlQuery );

			if( results.first() )
			{
				do
				{
					objects.add( retrieveById( dbConnection, storedClass, results.getInt("ClassID")) );
				}
				while(results.next());
			}

			return objects;
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
		finally
		{
			broker.freeConnection( dbConnection );
		}
	}

	public Object retrieveById(Class storedClass, int id) throws ChoobException
	{
		Connection dbConn = broker.getConnection();
		try
		{
			return retrieveById(dbConn, storedClass, id);
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
		finally
		{
			broker.freeConnection(dbConn);
		}
	}

	private Object retrieveById(Connection dbConnection, Class storedClass, int id) throws ChoobException, SQLException
	{
		PreparedStatement retriveObject = dbConnection.prepareStatement("SELECT * FROM ObjectStore LEFT JOIN ObjectStoreData ON ObjectStore.ObjectID = ObjectStoreData.ObjectID WHERE ClassName = ? AND ClassID = ?;");

		retriveObject.setString(1, storedClass.toString() );
		retriveObject.setInt(2, id);

		final ResultSet objSet = retriveObject.executeQuery();

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
			return null;
	}

	private void populateObject( Object tempObject, ResultSet result ) throws ChoobException
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

					if( result.getString("FieldType").equals("int") )
					{
						tempField.setInt( tempObject, Integer.parseInt( result.getString("FieldValue")) );
					}

					if( result.getString("FieldType").equals("float") )
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

	public void delete( Object strObject ) throws ChoobException
	{
		Connection dbCon = broker.getConnection();

		try
		{
			dbCon.setAutoCommit( false );

			delete( dbCon, strObject );

			dbCon.commit();
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
		finally
		{
			tidyUp(dbCon);
		}
	}

	private int getId( Object obj ) throws ChoobException
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

	private void setId( Object obj, int value ) throws ChoobException
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

	private void delete( Connection dbCon, Object strObj ) throws SQLException, ChoobException
	{
		int id = getId( strObj );

		PreparedStatement retrieveID = dbCon.prepareStatement("SELECT ObjectID FROM ObjectStore WHERE ClassName = ? AND ClassID = ?;");

		retrieveID.setString(1, strObj.getClass().toString() );
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

		PreparedStatement delete = dbCon.prepareStatement("DELETE FROM ObjectStore WHERE ObjectID = ?");

		delete.setInt(1, objectID);

		PreparedStatement deleteData = dbCon.prepareStatement("DELETE FROM ObjectStoreData WHERE ObjectID = ?");

		deleteData.setInt(1, objectID);

		deleteData.executeUpdate();

		delete.executeUpdate();
	}

	public void update( Object strObject ) throws ChoobException
	{
		Connection dbCon = broker.getConnection();

		try
		{
			dbCon.setAutoCommit( false );

			delete( dbCon, strObject );
			save( dbCon, strObject );

			dbCon.commit();
		}
		catch( SQLException e )
		{
			throw sqlErr(e);
		}
		finally
		{
			tidyUp(dbCon);
		}
	}

	public void save( Object strObject ) throws ChoobException
	{
		Connection dbCon = broker.getConnection();

		try
		{
			dbCon.setAutoCommit(false);

			save( dbCon, strObject );

			dbCon.commit();
		}
		catch( SQLException e )
		{
			throw sqlErr(e);
		}
		finally
		{
			tidyUp(dbCon);
		}
	}

	private void save( Connection dbCon, Object strObj ) throws SQLException, ChoobException
	{
		int id = getId( strObj );

		if( id == 0 )
		{
			PreparedStatement highestID = dbCon.prepareStatement("SELECT MAX(ClassID) FROM ObjectStore WHERE ClassName = ?;");

			highestID.setString(1, strObj.getClass().toString());

			ResultSet ids = highestID.executeQuery();

			if( ids.first() )
				id = ids.getInt(1)+1;
			else
				id = 1;

			setId( strObj, id );
		}

		PreparedStatement insertObject = dbCon.prepareStatement("INSERT INTO ObjectStore VALUES(NULL,?,?,?);");

		insertObject.setInt(1, 1); // CHANGE THIS
		insertObject.setString(2, strObj.getClass().toString());
		insertObject.setInt(3, id);

		insertObject.execute();

		ResultSet generatedKeys = insertObject.getGeneratedKeys();

		generatedKeys.first();

		int generatedID = generatedKeys.getInt(1);

		PreparedStatement insertField = dbCon.prepareStatement("INSERT INTO ObjectStoreData VALUES(?,?,?,?);");

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

						//System.out.println("Found integer field: " + tempField);
					}
					else if( tempField.getType() == java.lang.Float.TYPE )
					{
						foundType = true;
						insertField.setString(2, tempField.getName());
						insertField.setString(3, "float");
						insertField.setString(4, Float.toString(tempField.getFloat(strObj)));

						//System.out.println("Found float field: " + tempField);
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
}
