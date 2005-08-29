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
 * @author  sadiq
 */
public class ObjectDbTest
{
    DbConnectionBroker broker;
    
    /** Creates a new instance of ObjectDbTest */
    public ObjectDbTest() throws Exception
    {
	broker = new DbConnectionBroker("com.mysql.jdbc.Driver", "jdbc:mysql://localhost/choob?autoReconnect=true&autoReconnectForPools=true&initialTimeout=1", "choob", "choob", 10, 20, "/tmp/db.log", 1, true, 60, 3) ;
    }
    
    public void init() throws Exception
    {
	/*for( int c = 0; c < 20; c++ )
	{
	    StoreObject strObj = new StoreObject();
	 
	    strObj.setID( (int)(Math.random() * 100000) );
	 
	    strObj.setName( Integer.toHexString((int)(Math.random() * 100)) );
	    strObj.setPosition( "Moo!" );
	    save( strObj );
	 
	    System.out.println("Added " + c + " objects..");
	}
	 
	StoreObject storedObject = (StoreObject)retriveById(StoreObject.class, 86895);
	 
	System.out.println("Retrived object! "  + storedObject.toString());*/
	
	List results = retrive( StoreObject.class, "position = 'soviet'");
	
	System.out.println("Returned " + results.size() + " results.");
    }
    
    public List retrive(Class storedClass, String... clauses) throws Exception
    {
	ArrayList objects = new ArrayList();
	
	Connection dbConnection = broker.getConnection();
	
	StringBuffer sqlQuery = new StringBuffer("SELECT ObjectStore.ClassID FROM ObjectStore ");
	
	Pattern andSplitter = Pattern.compile("and\\s*", Pattern.CASE_INSENSITIVE);
	
	Pattern expParser = Pattern.compile("(\\p{Alpha}+)\\s*([=><~])\\s*(\\'\\w+\\')\\s*", Pattern.CASE_INSENSITIVE);
	
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
	
	System.out.println( sqlQuery.toString() );
	
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
	Connection dbConnection = broker.getConnection();
	
	PreparedStatement retriveObject = dbConnection.prepareStatement("SELECT * FROM ObjectStore LEFT JOIN ObjectStoreData ON ObjectStore.ObjectID = ObjectStoreData.ObjectID WHERE ClassName = ? AND ClassID = ?;");
	
	retriveObject.setString(1, storedClass.toString() );
	retriveObject.setInt(2, id);
	
	ResultSet objSet = retriveObject.executeQuery();
	
	if( objSet.first() )
	{
	    
	    Object tempObject = storedClass.newInstance();
	    
	    populateObject( tempObject, objSet );
	    
	    broker.freeConnection( dbConnection );
	    
	    return tempObject;
	}
	else
	{
	    broker.freeConnection( dbConnection );
	    
	    return null;
	}
    }
    
    private void populateObject( Object tempObject, ResultSet result ) throws Exception
    {
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
	
    }
    
    public void update( Object strObject ) throws Exception
    {
	delete( strObject );
	save( strObject );
    }
    
    public void save( Object strObj ) throws Exception
    {
	Field idField;
	
	try
	{
	    idField = strObj.getClass().getDeclaredField("id");
	}
	catch( NoSuchFieldException e )
	{
	    throw new Exception("Class " + strObj.getClass() + " does not have a unique 'id' property. Please add one.");
	}
	
	Connection dbConnection = broker.getConnection();
	
	PreparedStatement insertObject = dbConnection.prepareStatement("INSERT INTO ObjectStore VALUES(NULL,?,?,?);");
	
	insertObject.setInt(1, 1); // CHANGE THIS
	insertObject.setString(2, strObj.getClass().toString());
	insertObject.setInt(3, idField.getInt( strObj ));
	
	insertObject.execute();
	
	ResultSet generatedKeys = insertObject.getGeneratedKeys();
	
	generatedKeys.first();
	
	int generatedID = generatedKeys.getInt(1);
	
	PreparedStatement insertField = dbConnection.prepareStatement("INSERT INTO ObjectStoreData VALUES(?,?,?,?);");
	
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
		    
		    System.out.println("Found string field: " + tempField + " with value " + tempField.get(strObj));
		}
		
		if( foundType )
		{
		    System.out.println("Inserting field: " + tempField.getName());
		    insertField.executeUpdate();
		}
	    }
	}
	
	broker.freeConnection( dbConnection );
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception
    {
	(new ObjectDbTest()).init();
    }
}
