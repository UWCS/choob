package uk.co.uwcs.choob.support;

import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.ParseException;
import uk.co.uwcs.choob.modules.*;
import java.util.*;
import bsh.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.sql.*;
import java.lang.String;
import java.lang.reflect.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import org.mozilla.javascript.*;

public class ObjectDBTransaction // Needs to be non-final
{
	private static final int MAXOR = 50; // Max OR statements in a lumped together objectDB query.

	private Connection dbConn;
	private Modules mods;

	public final void setMods(Modules mods)
	{
		this.mods = mods;
	}

	public final void setConn(Connection dbConn)
	{
		this.dbConn = dbConn;
	}

	public final void begin()
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

	public final void commit()
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

	public final void rollback()
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

	public final void finish()
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

	public final void cleanUp(Statement stat)
	{
		try
		{
			if (stat != null)
				stat.close();
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
	}

	private final ChoobError sqlErr(SQLException e)
	{
		// Deadlock hack. XXX MySQL specific code.
		if (e.getErrorCode() == 1213)
		{
			throw new ObjectDBDeadlockError();
		}
		System.err.println("Ack! SQL Exception: " + e);
		e.printStackTrace();
		return new ObjectDBError("An SQL exception occurred while processing this operation.", e);
	}

	public final List<?> retrieve(Object storedClass, String clause)
	{
		return retrieve(NewClassWrapper(storedClass), clause);
	}
	
	public final List<?> retrieve(final ObjectDBClass storedClass, String clause)
	{
		String sqlQuery;

		if ( clause != null )
		{
			ObjectDbClauseParser parser = new ObjectDbClauseParser(clause, storedClass.getName());
			try
			{
				sqlQuery = parser.ParseSelect(null);
			}
			catch (ParseException e)
			{
				// TODO there's some public properties we can use to make a better error message.
				System.err.println("Parse error in string: " + clause);
				System.err.println("Error was: " + e);
				throw new ObjectDBError("Parse error in clause string: " + clause);
			}

			// Make sure it's the right query type... (XXX Do we need to?)
			if (parser.getType() != ObjectDbClauseParser.TYPE_SELECT)
				throw new ObjectDBError("Clause string " + clause + " was not a SELECT clause.");

			// Make sure we can read these classes...
			List<String> classNames = parser.getUsedClasses();
			for(String cls: classNames)
				checkPermission(cls);
		}
		else
		{
			checkPermission(storedClass.getName());
			sqlQuery = "SELECT ObjectStore.ClassID FROM ObjectStore WHERE ClassName = '" + storedClass.getName() + "';";
		}

		Statement objStat = null;
		Statement retrieveStat = null;
		try
		{
			final List<Object> objects = new ArrayList<Object>();

			objStat = dbConn.createStatement();
			retrieveStat = dbConn.createStatement();

			ResultSet allObjects = objStat.executeQuery( sqlQuery );

			String baseQuery = "SELECT ClassID, FieldName, FieldBigInt, FieldDouble, FieldString FROM ObjectStore LEFT JOIN ObjectStoreData ON ObjectStore.ObjectID = ObjectStoreData.ObjectID WHERE ClassName = '" + storedClass.getName() + "' AND (";

			Map<String,Type> fieldTypeCache = new HashMap<String,Type>();

			//FIXME:Field idField;
			//FIXME:try
			//FIXME:{
			//FIXME:	idField = storedClass.getField( "id" );
			//FIXME:}
			//FIXME:catch( NoSuchFieldException e )
			//FIXME:{
			//FIXME:	throw new ObjectDBError("Object of type " + storedClass + " has no id field!");
			//FIXME:}

			if( allObjects.first() )
			{
				boolean allObjsNext = false;
				Map<Integer,Integer> idMap = new HashMap<Integer,Integer>();
				int blockOffset = 0;
				do // Loop over all objects
				{
					// Eat this and maybe some more elements...
					int[] ids = new int[MAXOR];
					int count = 0;
					do
					{
						int thisId = allObjects.getInt(1);
						ids[count] = thisId;
						idMap.put(thisId, blockOffset + count);
						count++;
						allObjsNext = allObjects.next();
						objects.add(null);
					} while (allObjsNext && count < MAXOR - 1);
					blockOffset += count;

					// Build a query to get values for them...
					StringBuffer query = new StringBuffer(baseQuery);
					for(int i=0; i<count; i++)
					{
						query.append("ClassID = " + ids[i]);
						if (i != count - 1)
							query.append(" OR ");
					}
					query.append(");");

					ResultSet result = retrieveStat.executeQuery(query.toString());

					if (!result.first())
					{
						// Ooops. To quote Sadiq: Um, yeah...
						// Actually, this is rather objects not existing in ObjectStore when they, um, existed in ObjectStore.
						throw new ObjectDBError ("Inconsistent database state: One or more objects of type " + storedClass.getName() + " in ObjectStore did not exist in ObjectStoreData.");
					}

					ObjectDBObject tempObject = null; // This will be set immediately, because 0 is not a valid ID.
					int id = 0;

					try
					{
						do // Loop over this block's results
						{
							try
							{
								// Break if we're in the next object
								int newId = result.getInt(1);
								if (newId != id)
								{
									Object newObject = storedClass.newInstance();
									// Store the real object, then...
									objects.set(idMap.get(newId), newObject);
									// ...wrap the object so we are able to use it!
									tempObject = NewObjectWrapper(newObject);
									tempObject.setId(newId);
									id = newId;
								}

								String name = result.getString(2);
								if (name == null)
								{
									// XXX This is forbidden by the schema, yet happens when the DB is broken.
									// Ie there's a null object.
									// Since it already got added, we're safe, but the object will now
									// have all fields initialised to default.
									continue;
								}
								
								Type fieldType = fieldTypeCache.get(name);
								if (fieldType == null)
								{
									fieldType = tempObject.getFieldType(name);
									fieldTypeCache.put(name, fieldType);
								}

								if (fieldType == String.class)
								{
									tempObject.setFieldValue(name, result.getString(5));
								}
								else if (fieldType == Integer.TYPE)
								{
									tempObject.setFieldValue(name, (int)result.getLong(3));
								}
								else if (fieldType == Long.TYPE)
								{
									tempObject.setFieldValue(name, result.getLong(3));
								}
								else if (fieldType == Boolean.TYPE)
								{
									tempObject.setFieldValue(name, result.getLong(3) == 1);
								}
								else if (fieldType == Float.TYPE)
								{
									tempObject.setFieldValue(name, (float)result.getDouble(4));
								}
								else if (fieldType == Double.TYPE)
								{
									tempObject.setFieldValue(name, result.getDouble(4));
								}
							}
							catch (NoSuchFieldException e)
							{
								e.printStackTrace();
								// Ignore this, as per spec.
							}
						}
						while( result.next() ); // Looping over fields
					}
					catch (InstantiationException e)
					{
						System.err.println("Error instantiating object of type " + storedClass + ": " + e);
						throw new ObjectDBError("The object could not be instantiated.");
					}
					catch (IllegalAccessException e)
					{
						System.err.println("Access error instantiating object of type " + storedClass + ": " + e);
						throw new ObjectDBError("The object could not be instantiated.");
					}
				} while ( allObjsNext ); // Looping over blocks of IDs
			}

			return objects;
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
		finally
		{
			cleanUp(retrieveStat);
			cleanUp(objStat);
		}
	}

	public final List<Integer> retrieveInt(Object storedClass, String clause)
	{
		return retrieveInt(NewClassWrapper(storedClass), clause);
	}
	
	public final List<Integer> retrieveInt(final ObjectDBClass storedClass, String clause)
	{
		String sqlQuery;

		if ( clause != null )
		{
			ObjectDbClauseParser parser = new ObjectDbClauseParser(clause, storedClass.getName());
			try
			{
				sqlQuery = parser.ParseSelect(null);
			}
			catch (ParseException e)
			{
				// TODO there's some public properties we can use to make a better error message.
				System.err.println("Parse error in string: " + clause);
				System.err.println("Error was: " + e);
				throw new ObjectDBError("Parse error in clause string: " + clause);
			}

			// Make sure it's the right query type... (XXX Do we need to?)
			if (parser.getType() != ObjectDbClauseParser.TYPE_SELECT)
				throw new ObjectDBError("Clause string " + clause + " was not a SELECT clause.");

			// Make sure we can read these classes...
			List<String> classNames = parser.getUsedClasses();
			for(String cls: classNames)
				checkPermission(cls);
		}
		else
		{
			checkPermission(storedClass.getName());
			sqlQuery = "SELECT ObjectStore.ClassID FROM ObjectStore WHERE ClassName = '" + storedClass.getName() + "';";
		}

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
				throw new ObjectDBError("Parse error in clause string.");
			}
		}
		else
		{
			sqlQuery = "SELECT ObjectStore.ClassID FROM ObjectStore WHERE ClassName = '" + storedClass.getName() + "';";
		}

		Statement objStat = null;
		try
		{
			ArrayList<Integer> objects = new ArrayList<Integer>();

			objStat = dbConn.createStatement();

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
		finally
		{
			cleanUp(objStat);
		}
	}
	
	public final void delete(Object strObj)
	{
		delete(NewObjectWrapper(strObj));
	}
	
	public final void delete(ObjectDBObject strObj)
	{
		checkPermission(strObj.getClass().getName());
		PreparedStatement delete = null, deleteData = null;
		try
		{
			int id = strObj.getId();
			
			PreparedStatement retrieveID = dbConn.prepareStatement("SELECT ObjectID FROM ObjectStore WHERE ClassName = ? AND ClassID = ?;");
			
			retrieveID.setString(1, strObj.getClassName());
			retrieveID.setInt(2, id);
			
			ResultSet resultID = retrieveID.executeQuery();
			
			int objectID;
			
			if( resultID.first() )
			{
				objectID = resultID.getInt("ObjectID");
			}
			else
			{
				throw new ObjectDBError("Object for deletion does not exist.");
			}
			
			delete = dbConn.prepareStatement("DELETE FROM ObjectStore WHERE ObjectID = ?");
			
			delete.setInt(1, objectID);
			
			deleteData = dbConn.prepareStatement("DELETE FROM ObjectStoreData WHERE ObjectID = ?");
			
			deleteData.setInt(1, objectID);
			
			deleteData.executeUpdate();
			
			delete.executeUpdate();
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
		finally
		{
			cleanUp(delete);
			cleanUp(deleteData);
		}
	}

	public final void update(Object strObj)
	{
		update(NewObjectWrapper(strObj));
	}
	
	public final void update(ObjectDBObject strObj)
	{
		_store(strObj, true);
	}

	public void run()
	{
		throw new ObjectDBError("This transaction has no run() method...");
	}

	public final void save(Object strObj)
	{
		save(NewObjectWrapper(strObj));
	}
	
	public final void save(ObjectDBObject strObj)
	{
		_store(strObj, false);
	}

	private final void _store(ObjectDBObject strObj, boolean replace)
	{
		checkPermission(strObj.getClassName());
		PreparedStatement stat = null, field;
		try
		{
			int id = strObj.getId();
			
			boolean setId = false;
			
			if( id == 0 )
			{
				stat = dbConn.prepareStatement("SELECT MAX(ClassID) FROM ObjectStore WHERE ClassName = ?;");
				
				stat.setString(1, strObj.getClassName());
				
				ResultSet ids = stat.executeQuery();
				
				if( ids.first() )
					id = ids.getInt(1)+1;
				else
					id = 1;
				
				stat.close();
				
				setId = true;
			}
			
			int objId = 0;
			
			// If there might be a collision, we need the old object ID.
			if (replace)
			{
				stat = dbConn.prepareStatement("SELECT ObjectID FROM ObjectStore WHERE ClassName = ? AND ClassID = ?;");
				
				stat.setString(1, strObj.getClassName());
				stat.setInt(2, id);
				
				stat.execute();
				
				ResultSet ids = stat.executeQuery();
				
				if( ids.first() )
					objId = ids.getInt(1);
				
				stat.close();
			}
			
			// There's no collision (more specifically, if there is one, we're boned).
			if (objId == 0)
			{
				stat = dbConn.prepareStatement("INSERT INTO ObjectStore VALUES(NULL,?,?);");

				stat.setString(1, strObj.getClassName());
				stat.setInt(2, id);

				stat.execute();

				ResultSet generatedKeys = stat.getGeneratedKeys();

				generatedKeys.first();

				objId = generatedKeys.getInt(1);
			}

			stat.close();

			if (replace)
				stat = dbConn.prepareStatement("REPLACE INTO ObjectStoreData VALUES(?,?,?,?,?);");
			else
				stat = dbConn.prepareStatement("INSERT INTO ObjectStoreData VALUES(?,?,?,?,?);");

			String[] fields = strObj.getFields();

			for( int c = 0 ; c < fields.length ; c++ )
			{
				String fieldName = fields[c];

				if( !fieldName.equals("id") )
				{
					boolean foundType = true;
					
					stat.setInt(1, objId);
					
					try
					{
						Type theType = strObj.getFieldType(fieldName);
						
						if( theType == java.lang.Integer.TYPE )
						{
							int theVal = ((Integer)strObj.getFieldValue(fieldName)).intValue();
							stat.setString(2, fieldName);
							stat.setLong(3, theVal);
							stat.setDouble(4, theVal);
							stat.setString(5, Integer.toString(theVal));
						}
						else if( theType == java.lang.Long.TYPE )
						{
							long theVal = ((Long)strObj.getFieldValue(fieldName)).longValue();
							stat.setString(2, fieldName);
							stat.setLong(3, theVal);
							stat.setDouble(4, theVal);
							stat.setString(5, Long.toString(theVal));
						}
						else if( theType == java.lang.Boolean.TYPE )
						{
							boolean theVal = ((Boolean)strObj.getFieldValue(fieldName)).booleanValue();
							stat.setString(2, fieldName);
							stat.setLong(3, theVal ? 1 : 0);
							stat.setDouble(4, theVal ? 1 : 0);
							stat.setString(5, theVal ? "1" : "0");
						}
						else if( theType == java.lang.Float.TYPE )
						{
							float theVal = ((Float)strObj.getFieldValue(fieldName)).floatValue();
							stat.setString(2, fieldName);
							stat.setLong(3, (long)theVal);
							stat.setDouble(4, theVal);
							stat.setString(5, Float.toString(theVal));
						}
						else if( theType == java.lang.Double.TYPE )
						{
							double theVal = ((Double)strObj.getFieldValue(fieldName)).doubleValue();
							stat.setString(2, fieldName);
							stat.setLong(3, (long)theVal);
							stat.setDouble(4, theVal);
							stat.setString(5, Double.toString(theVal));
						}
						else if( theType == String.class )
						{
							stat.setString(2, fieldName);
							stat.setLong(3, 0); // XXX - parse these or not parse these?
							stat.setDouble(4, 0);
							stat.setString(5, (String)strObj.getFieldValue(fieldName));
						}
						else
							foundType = false;

						if( foundType )
						{
							stat.executeUpdate();
						}
					}
					catch (NoSuchFieldException e)
					{
						// Should never happen, but if it does, just ignore.
					}
					catch (IllegalAccessException e)
					{
						// Should never happen, but if it does, just ignore.
					}
				}
			}

			// Set the ID only AFTER we store!
			if (setId)
				strObj.setId(id);
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
		finally
		{
			cleanUp(stat);
		}
	}

	private final Map<String,Object> permCache = new HashMap<String,Object>(); // Doesn't need sync.
	private final void checkPermission(String objClass)
	{
		String plugin = mods.security.getPluginName(0);
		String clsName = objClass.toLowerCase();
		if (plugin != null)
		{
			String plugName = "plugins." + plugin.toLowerCase() + ".";
			if ( clsName.startsWith(plugName) )
				return;
		}
		Object cache = permCache.get(clsName);
		if (cache == null)
		{
			AccessController.checkPermission(new ChoobPermission("objectdb."+clsName));
			permCache.put(clsName, new Object());
		}
		// Non-null cache ==> we passed this check before.
	}
	
	private final ObjectDBClass NewClassWrapper(Object obj)
	{
		// Create the correct wrapper here.
		if (obj instanceof Class) {
			return new ObjectDBClassJavaWrapper(obj);
		}
		if (obj instanceof Function) {
			return new ObjectDBClassJSWrapper(obj);
		}
		return null;
	}
	
	private final ObjectDBObject NewObjectWrapper(Object obj)
	{
		// Create the correct wrapper here.
		try {
			if (obj instanceof org.mozilla.javascript.NativeObject) {
				return new ObjectDBObjectJSWrapper(obj);
			}
			return new ObjectDBObjectJavaWrapper(obj);
		} catch (ChoobException e) {
		} catch (NoSuchFieldException e) {
			// Do nothing and let it fail?
		}
		return null;
	}
}
