/*
 * ObjectDbTest.java
 *
 * Created on August 6, 2005, 9:10 PM
 */

package org.uwcs.choob.modules;

import org.uwcs.choob.*;
import org.uwcs.choob.support.*;
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

	public List retrive(Class storedClass, String... clauses) throws Exception
	{
		ArrayList objects = new ArrayList();

		Connection dbConnection = broker.getConnection();

		StringBuffer sqlQuery = new StringBuffer("SELECT ObjectStore.ClassID FROM ObjectStore ");

		Pattern andSplitter = Pattern.compile("and\\s*", Pattern.CASE_INSENSITIVE);

		Pattern expParser = Pattern.compile("(\\p{Alpha}+)\\s*([=><~])\\s*((?:\\'.+\\')|(?:\\d+))\\s*", Pattern.CASE_INSENSITIVE);

		for( int c = 0; c < clauses.length; c++ )
		{
			Matcher expMatcher = expParser.matcher( clauses[c] );

			if( expMatcher.matches() )
			{
				String variable = expMatcher.group(1);
				String operator = expMatcher.group(2);
				String value = expMatcher.group(3);

				sqlQuery.append("INNER JOIN ObjectStoreData o" + c + " ON o"+c+".ObjectID = ObjectStore.ObjectID AND o"+c+".FieldName = '" + variable + "' AND o"+c+".FieldValue " + operator + " " + value + " ");
			}
			else
			{
				throw new Exception("Clause: " + clauses[c] + " is syntactically incorrect. Please check your pants.");
			}
		}

		Statement objStat = dbConnection.createStatement();

		ResultSet results = objStat.executeQuery( sqlQuery.toString() + " WHERE ClassName = '" + storedClass.toString() + "';");

		if( results.first() )
		{
			do
			{
				objects.add( retriveById( storedClass, results.getInt("ClassID")) );
			}
			while(results.next());
		}

		return objects;
	}

	public Object retriveById(Class storedClass, int id) throws Exception
	{
		Object tempObject = null;

		Connection dbConnection = broker.getConnection();

		PreparedStatement retriveObject = dbConnection.prepareStatement("SELECT * FROM ObjectStore LEFT JOIN ObjectStoreData ON ObjectStore.ObjectID = ObjectStoreData.ObjectID WHERE ClassName = ? AND ClassID = ?;");

		retriveObject.setString(1, storedClass.toString() );
		retriveObject.setInt(2, id);

		ResultSet objSet = retriveObject.executeQuery();

		if( objSet.first() )
		{

			tempObject = storedClass.newInstance();

			populateObject( tempObject, objSet );
		}

		return tempObject;
	}

	private void populateObject( Object tempObject, ResultSet result ) throws Exception
	{
		Field idField = tempObject.getClass().getDeclaredField("id");

		idField.setInt( tempObject, result.getInt("ClassID") );

		do
		{
			try
			{
				Field tempField = tempObject.getClass().getDeclaredField( result.getString("FieldName") );

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
		}
		while( result.next() );
	}

	public void delete( Object strObject ) throws Exception
	{
		Connection dbCon = broker.getConnection();

		try
		{
			dbCon.setAutoCommit( false );

			delete( dbCon, strObject );

			dbCon.commit();
		}
		catch( Exception e )
		{
			dbCon.rollback();

			broker.freeConnection( dbCon );
			throw new Exception("An error occured while trying to delete.", e);
		}
	}

	private void delete( Connection dbCon, Object strObj ) throws Exception
	{
		Field idField;

		try
		{
			idField = strObj.getClass().getDeclaredField("id");
		}
		catch( NoSuchFieldException e )
		{
			throw new Exception("Class " + strObj.getClass() + " does not have a unique 'id' property. Please add one.", e);
		}

		int id = idField.getInt( strObj );

		PreparedStatement retriveID = dbCon.prepareStatement("SELECT ObjectID FROM ObjectStore WHERE ClassName = ? AND ClassID = ?;");

		retriveID.setString(1, strObj.getClass().toString() );
		retriveID.setInt(2, id);

		ResultSet resultID = retriveID.executeQuery();

		int objectID;

		if( resultID.first() )
		{
			objectID = resultID.getInt("ObjectID");
		}
		else
		{
			System.out.println(retriveID.toString());

			throw new Exception("Object for deletion does not exist.");
		}

		PreparedStatement delete = dbCon.prepareStatement("DELETE FROM ObjectStore WHERE ObjectID = ?");

		delete.setInt(1, objectID);

		PreparedStatement deleteData = dbCon.prepareStatement("DELETE FROM ObjectStoreData WHERE ObjectID = ?");

		deleteData.setInt(1, objectID);

		delete.executeUpdate();

		deleteData.executeUpdate();
	}

	public void update( Object strObject ) throws Exception
	{
		Connection dbCon = broker.getConnection();

		try
		{
			dbCon.setAutoCommit( false );

			delete( dbCon, strObject );
			save( dbCon, strObject );

			dbCon.commit();
		}
		catch( Exception e )
		{
			dbCon.rollback();

			broker.freeConnection( dbCon );
			throw new Exception("An error occured while trying to update.", e);
		}
	}

	public void save( Object strObject ) throws Exception
	{
		Connection dbCon = broker.getConnection();

		try
		{
			dbCon.setAutoCommit(false);

			save( dbCon, strObject );

			dbCon.commit();
		}
		catch( Exception e )
		{
			dbCon.rollback();

			broker.freeConnection( dbCon );

			throw new Exception("An error occured while trying to save.", e);
		}
	}

	private void save( Connection dbCon, Object strObj ) throws Exception
	{
		Field idField;

		try
		{
			idField = strObj.getClass().getDeclaredField("id");
		}
		catch( NoSuchFieldException e )
		{
			throw new Exception("Class " + strObj.getClass() + " does not have a unique 'id' property. Please add one.", e);
		}

		if( idField.getInt(strObj) == 0 )
		{
			PreparedStatement highestID = dbCon.prepareStatement("SELECT MAX(ClassID) FROM ObjectStore WHERE ClassName = ?;");

			highestID.setString(1, strObj.getClass().toString());

			ResultSet ids = highestID.executeQuery();

			if( ids.first() )
			{
				idField.setInt( strObj, ids.getInt(1)+1 );
			}
			else
			{
				idField.setInt( strObj, 1 );
			}
		}

		PreparedStatement insertObject = dbCon.prepareStatement("INSERT INTO ObjectStore VALUES(NULL,?,?,?);");

		insertObject.setInt(1, 1); // CHANGE THIS
		insertObject.setString(2, strObj.getClass().toString());
		insertObject.setInt(3, idField.getInt( strObj ));

		insertObject.execute();

		ResultSet generatedKeys = insertObject.getGeneratedKeys();

		generatedKeys.first();

		int generatedID = generatedKeys.getInt(1);

		PreparedStatement insertField = dbCon.prepareStatement("INSERT INTO ObjectStoreData VALUES(?,?,?,?);");

		Field[] fields = strObj.getClass().getDeclaredFields();

		for( int c = 0 ; c < fields.length ; c++ )
		{
			Field tempField = fields[c];

			if( !tempField.getName().equals("id") )
			{
				boolean foundType = false;

				insertField.setInt(1, generatedID);

				if( tempField.getType() == java.lang.Integer.TYPE )
				{
					foundType = true;
					insertField.setString(2, tempField.getName());
					insertField.setString(3, "int");
					insertField.setString(4, Integer.toString(tempField.getInt(strObj)));

					//System.out.println("Found integer field: " + tempField);
				}

				if( tempField.getType() == java.lang.Float.TYPE )
				{
					foundType = true;
					insertField.setString(2, tempField.getName());
					insertField.setString(3, "float");
					insertField.setString(4, Float.toString(tempField.getFloat(strObj)));

					//System.out.println("Found float field: " + tempField);
				}

				if( tempField.getType() == String.class )
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
		}
	}
}
