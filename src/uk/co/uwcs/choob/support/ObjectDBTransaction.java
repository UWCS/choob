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
	private static final boolean USEMANYTABLES = true; // true for one table per object type; false otherwise.

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

	private final void cleanUp(Statement stat)
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

	static final int
		TYPE_TINYINT = 0,
		TYPE_SMALLINT = 1,
		TYPE_INT = 2,
		TYPE_BIGINT = 3,
		TYPE_FLOAT = 4,
		TYPE_DOUBLE = 5,
		TYPE_TEXT = 6;
	// Unused as yet.
	static final int
		FLAG_UNSIGNED = 128;
	static final Map<String,Integer> dbTypeMap = new HashMap<String,Integer>();
	static final Map<Type,Integer> clsTypeMap = new HashMap<Type,Integer>();
	static final Map<Integer,String> dbReverseTypeMap = new HashMap<Integer,String>();
	static final Map<Integer,String> dbIndexLenMap = new HashMap<Integer,String>();
	static
	{
		dbTypeMap.put("tinyint", TYPE_TINYINT); // byte
		clsTypeMap.put(Byte.TYPE, TYPE_TINYINT);
		clsTypeMap.put(Boolean.TYPE, TYPE_TINYINT);
		dbReverseTypeMap.put(TYPE_TINYINT, "TINYINT");
		dbIndexLenMap.put(TYPE_TINYINT, "");

		dbTypeMap.put("smallint", TYPE_SMALLINT); // short
		clsTypeMap.put(Short.TYPE, TYPE_SMALLINT);
		dbReverseTypeMap.put(TYPE_SMALLINT, "SMALLINT");
		dbIndexLenMap.put(TYPE_SMALLINT, "");

		dbTypeMap.put("int", TYPE_INT); // int
		clsTypeMap.put(Integer.TYPE, TYPE_INT);
		dbReverseTypeMap.put(TYPE_INT, "INT");
		dbIndexLenMap.put(TYPE_INT, "");

		dbTypeMap.put("bigint", TYPE_BIGINT); // long
		clsTypeMap.put(Long.TYPE, TYPE_BIGINT);
		dbReverseTypeMap.put(TYPE_BIGINT, "BIGINT");
		dbIndexLenMap.put(TYPE_BIGINT, "");

		dbTypeMap.put("float", TYPE_FLOAT); // float
		clsTypeMap.put(Float.TYPE, TYPE_FLOAT);
		dbReverseTypeMap.put(TYPE_FLOAT, "FLOAT");
		dbIndexLenMap.put(TYPE_FLOAT, "");

		dbTypeMap.put("double", TYPE_DOUBLE); // double
		clsTypeMap.put(Double.TYPE, TYPE_DOUBLE);
		dbReverseTypeMap.put(TYPE_DOUBLE, "DOUBLE");
		dbIndexLenMap.put(TYPE_DOUBLE, "");

		dbTypeMap.put("text", TYPE_TEXT); // String
		clsTypeMap.put(String.class, TYPE_TEXT);
		dbReverseTypeMap.put(TYPE_TEXT, "TEXT");
		dbIndexLenMap.put(TYPE_TEXT, "(16)");
	}

	/**
	 * Clean a string for use in a query.
	 * @param remove the type of quotes you'll use in the query
	 * @param in the String to quote
	 * @return the String with all instances of remove and \ quoted.
	 */
	public String clean(String remove, String in)
	{
		return in.replaceAll("([\\\\" + remove + "])", "\\\\$1");
	}

	private final String getTableName(ObjectDBObject obj)
	{
		return "_objectdb_" + obj.getClassName().toLowerCase().replaceAll("\\.", "_");
	}

	private final void checkTable(ObjectDBClass cls)
	{
		try
		{
			checkTable(NewObjectWrapper(cls.newInstance()));
		}
		catch (InstantiationException e)
		{
			throw new ObjectDBError("Could not instanciate object of type: " + cls.getName());
		}
		catch (IllegalAccessException e)
		{
			throw new ObjectDBError("Could not instanciate object of type: " + cls.getName());
		}
	}

	private final void checkTable(ObjectDBObject obj)
	{
		if (!USEMANYTABLES)
			return; // XXX Maybe want to do stuff here.
		// XXX possibly MySQL specific.
		Statement stat = null;
		try
		{
			List<String> statements = new ArrayList<String>();

			// Obtain DB types
			Map<String,Integer> dbTypes = new HashMap<String,Integer>();
			stat = dbConn.createStatement();

			ResultSet results = stat.executeQuery("DESCRIBE `" + clean("`", getTableName(obj)) + "`");
			if (results.first())
			{
				do
				{
					// get the type name.
					String info = results.getString(2).toLowerCase();
					int flags = 0;
					String name;
					if (info.indexOf('(') != -1)
					{
						name = info.substring(0, info.indexOf('('));
					}
					else if (info.indexOf(' ') != -1)
					{
						name = info.substring(0, info.indexOf(' '));
						//flags |= FLAG_UNSIGNED; // Etc goes here.
					}
					else
					{
						name = info;
					}

					Integer type = dbTypeMap.get(name);
					if (type == null)
						throw new ObjectDBError("Unknown column type: " + name);

					String field = results.getString(1).toLowerCase();
					if (field.equals("id"))
						continue;

					dbTypes.put(field, type);
				}
				while (results.next());
			}
			else
			{
				throw new ObjectDBError("Zero result set in table query.");
			}
			stat.close();

			// Obtain class types
			Map<String,Integer> clsTypes = new HashMap<String,Integer>();
			String[] fields = obj.getFields();
			for(String field: fields)
			{
				Integer type = null;
				try
				{
					type = clsTypeMap.get(obj.getFieldType(field));
					if (type == null)
						throw new ObjectDBError("Unknown class type: " + obj.getFieldType(field));
				}
				catch (NoSuchFieldException e)
				{
					throw new ObjectDBError("Unknown class type for field " + field);
				}

				field = field.toLowerCase();
				if (field.equals("id"))
					continue;

				clsTypes.put(field, type);
			}

			// Compare them.
			Iterator<String> clsIter = clsTypes.keySet().iterator();
			while(clsIter.hasNext())
			{
				String thisName = clsIter.next();
				Integer dbType = dbTypes.get(thisName);
				Integer clsType = clsTypes.get(thisName);
				if (dbType != null && dbType == clsType)
				{
					dbTypes.remove(thisName);
				}
				else if (dbType != null)
				{
					// Types changed. Fix it here.
					statements.add("DROP INDEX `" + clean("`", thisName + "__index") + "`");
					statements.add("CHANGE `" + clean("`", thisName) + "` `" + clean("`", thisName) + "` " + dbReverseTypeMap.get(clsType) + (clsType == TYPE_TEXT ? "" : " NOT NULL"));
					statements.add("ADD INDEX `" + clean("`", thisName + "__index") + "` (`" + clean("`", thisName) + "`" + dbIndexLenMap.get(clsType) + ")");
					dbTypes.remove(thisName);
				}
				else
				{
					statements.add("ADD `" + clean("`", thisName) + "` " + dbReverseTypeMap.get(clsType) + (clsType == TYPE_TEXT ? "" : " NOT NULL"));
					statements.add("ADD INDEX `" + clean("`", thisName + "__index") + "` (`" + clean("`", thisName) + "`" + dbIndexLenMap.get(clsType) + ")");
				}
			}

			// Anything left in dbTypes needs removing.
			Iterator<String> dbIter = dbTypes.keySet().iterator();
			while(dbIter.hasNext())
			{
				String thisName = dbIter.next();
				// Types changed. Fix it here.
				statements.add("DROP COLUMN `" + clean("`", thisName) + "`");
			}

			// Now execute all that!
			begin();
			stat = dbConn.createStatement();
			String initial = "ALTER TABLE `" + clean("`", getTableName(obj)) + "` ";
			for(String query: statements)
			{
				stat.executeUpdate(initial + query);
			}
			commit();
		}
		catch (SQLException e)
		{
			// XXX MySQL specific code
			if (e.getErrorCode() == 1146)
				generateTable(obj);
			else
				throw sqlErr(e);
		}
		finally
		{
			cleanUp(stat);
		}
	}

	private void generateTable(ObjectDBObject obj)
	{
		Statement stat = null;
		try
		{
			List<String> statements = new ArrayList<String>();

			// Obtain class types
			Map<String,Integer> clsTypes = new HashMap<String,Integer>();
			String[] fields = obj.getFields();
			for(String field: fields)
			{
				Integer type = null;
				try
				{
					type = clsTypeMap.get(obj.getFieldType(field));
					if (type == null)
						throw new ObjectDBError("Unknown class type: " + obj.getFieldType(field));
				}
				catch (NoSuchFieldException e)
				{
					throw new ObjectDBError("Unknown class type for field " + field);
				}

				field = field.toLowerCase();
				if (field.equals("id"))
					continue;

				statements.add("ADD `" + clean("`", field) + "` " + dbReverseTypeMap.get(type) + (type == TYPE_TEXT ? "" : " NOT NULL"));
				statements.add("ADD INDEX `" + clean("`", field + "__index") + "` (`" + clean("`", field) + "`" + dbIndexLenMap.get(type) + ")");
			}

			// Now execute all that!
			stat = dbConn.createStatement();
			begin();
			stat.executeUpdate("CREATE TABLE `" + clean("`", getTableName(obj)) + "` (id INT NOT NULL AUTO_INCREMENT PRIMARY KEY) Type=InnoDB");
			String initial = "ALTER TABLE `" + clean("`", getTableName(obj)) + "` ";
			for(String query: statements)
			{
				stat.executeUpdate(initial + query);
			}
			commit();
		}
		catch (SQLException e)
		{
			// XXX MySQL specific code
			throw sqlErr(e);
		}
		finally
		{
			cleanUp(stat);
		}
	}

	public final List<?> retrieve(Object storedClass, String clause)
	{
		return retrieve(NewClassWrapper(storedClass), clause);
	}
	
	public final List<?> retrieve(final ObjectDBClass storedClass, String clause)
	{
		String sqlQuery;

		if ( clause == null )
		{
			clause = "WHERE 1";
		}

		String[] fields;
		try
		{
			fields = NewObjectWrapper(storedClass.newInstance()).getFields();
		}
		catch (InstantiationException e)
		{
			throw new ObjectDBError("Could not instanciate object of type: " + storedClass.getName());
		}
		catch (IllegalAccessException e)
		{
			throw new ObjectDBError("Could not instanciate object of type: " + storedClass.getName());
		}
		String select;
		if (USEMANYTABLES)
		{
			StringBuilder fieldNames = new StringBuilder();
			for(int i=0; i<fields.length; i++)
			{
				fieldNames.append("`" + clean("`", fields[i]) + "`");
				if (i != fields.length - 1)
					fieldNames.append(", ");
			}
			select = fieldNames.toString();
		}
		else
			select = "id";

		ObjectDBClauseParser parser = new ObjectDBClauseParser("SELECT " + select + " " + clause, storedClass.getName());
		parser.setUseMany(USEMANYTABLES);
		try
		{
			sqlQuery = parser.ODBExpr();
		}
		catch (ParseException e)
		{
			// TODO there's some public properties we can use to make a better error message.
			System.err.println("Parse error in string: " + clause);
			System.err.println("Error was: " + e);
			throw new ObjectDBError("Parse error in clause string: " + clause);
		}

		// Make sure it's the right query type... (XXX Do we need to?)
		if (parser.getType() != ObjectDBClauseParser.TYPE_SELECT)
			throw new ObjectDBError("Clause string " + clause + " was not a SELECT clause.");

		// Make sure we can read these classes...
		List<String> classNames = parser.getUsedClasses();
		for(String cls: classNames)
			checkPermission(cls);

		checkTable(storedClass);

		if (USEMANYTABLES)
		{
			Statement objStat = null;
			Statement retrieveStat = null;
			try
			{
				final List<Object> objects = new ArrayList<Object>();

				objStat = dbConn.createStatement();
				retrieveStat = dbConn.createStatement();

				ResultSet allObjects = objStat.executeQuery( sqlQuery );

				Map<String,Type> fieldTypeCache = new HashMap<String,Type>();

				if( allObjects.first() )
				{
					boolean allObjsNext = false;
					int blockOffset = 0;
					do // Loop over all objects
					{
						Object newObject = storedClass.newInstance(); // This will be set immediately, because 0 is not a valid ID.
						ObjectDBObject tempObject = NewObjectWrapper(newObject);

						for(int i=0; i<fields.length; i++)
						{
							String name = fields[i];

							Type fieldType = fieldTypeCache.get(name);
							if (fieldType == null)
							{
								fieldType = tempObject.getFieldType(name);
								fieldTypeCache.put(name, fieldType);
							}

							if (fieldType == String.class)
							{
								tempObject.setFieldValue(name, allObjects.getString(i + 1));
							}
							else if (fieldType == Integer.TYPE)
							{
								tempObject.setFieldValue(name, (int)allObjects.getLong(i + 1));
							}
							else if (fieldType == Long.TYPE)
							{
								tempObject.setFieldValue(name, allObjects.getLong(i + 1));
							}
							else if (fieldType == Boolean.TYPE)
							{
								tempObject.setFieldValue(name, allObjects.getLong(i + 1) == 1);
							}
							else if (fieldType == Float.TYPE)
							{
								tempObject.setFieldValue(name, (float)allObjects.getDouble(i + 1));
							}
							else if (fieldType == Double.TYPE)
							{
								tempObject.setFieldValue(name, allObjects.getDouble(i + 1));
							}
						}
						objects.add(newObject);
					}
					while ( allObjects.next() ); // Looping over blocks of IDs
				}

				return objects;
			}
			catch (NoSuchFieldException e)
			{
				e.printStackTrace();
				// This should never happen...
				throw new ObjectDBError("Field that did exist now doesn't. Ooops?");
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
		else
		{
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
			ObjectDBClauseParser parser = new ObjectDBClauseParser(clause, storedClass.getName());
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
			if (parser.getType() != ObjectDBClauseParser.TYPE_SELECT)
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

		checkTable(storedClass);

		if ( clause != null )
		{
			try
			{
				sqlQuery = ObjectDBClauseParser.getSQL(clause, storedClass.getName());
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
		checkPermission(strObj.getClassName());
		if (USEMANYTABLES)
		{
			checkTable(strObj);
			PreparedStatement delete = null;
			try
			{
				int id = strObj.getId();

				delete = dbConn.prepareStatement("DELETE FROM `" + clean("`", getTableName(strObj)) + "` WHERE id = ?");

				delete.setInt(1, id);

				if (delete.executeUpdate() == 0)
					throw new ObjectDBError("Object for deletion does not exist.");
			}
			catch (SQLException e)
			{
				throw sqlErr(e);
			}
			finally
			{
				cleanUp(delete);
			}
		}
		else
		{
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
		if (USEMANYTABLES)
		{
			checkTable(strObj);
			PreparedStatement stat = null, field;
			try
			{
				int id = strObj.getId();

				boolean setId = false;

				String idVal = id == 0 ? "DEFAULT" : String.valueOf(id);

				StringBuilder values = new StringBuilder();
				String[] fields = strObj.getFields();
				for(int i=0; i<fields.length; i++)
				{
					if (fields[i].equals("id"))
						values.append("`" + clean("`", fields[i]) + "` = " + idVal);
					else
						values.append("`" + clean("`", fields[i]) + "` = ?");
					if (i != fields.length - 1)
						values.append(", ");
				}

				if (replace)
					stat = dbConn.prepareStatement("REPLACE INTO `" + clean("`", getTableName(strObj)) + "` SET " + values);
				else
					stat = dbConn.prepareStatement("INSERT INTO `" + clean("`", getTableName(strObj)) + "` SET " + values);

				int offset = 1; // 0 after id set
				for( int c = 0 ; c < fields.length ; c++ )
				{
					String fieldName = fields[c];

					if( fieldName.equals("id") )
					{
						// Skip...
						offset = 0;
					}
					else
					{
						boolean foundType = true;

						try
						{
							Type theType = strObj.getFieldType(fieldName);

							if( theType == java.lang.Integer.TYPE )
							{
								int theVal = ((Integer)strObj.getFieldValue(fieldName)).intValue();
								stat.setInt(c + offset, theVal);
							}
							else if( theType == java.lang.Long.TYPE )
							{
								long theVal = ((Long)strObj.getFieldValue(fieldName)).longValue();
								stat.setLong(c + offset, theVal);
							}
							else if( theType == java.lang.Boolean.TYPE )
							{
								boolean theVal = ((Boolean)strObj.getFieldValue(fieldName)).booleanValue();
								stat.setByte(c + offset, theVal ? (byte)1 : (byte)0);
							}
							else if( theType == java.lang.Float.TYPE )
							{
								float theVal = ((Float)strObj.getFieldValue(fieldName)).floatValue();
								stat.setFloat(c + offset, theVal);
							}
							else if( theType == java.lang.Double.TYPE )
							{
								double theVal = ((Double)strObj.getFieldValue(fieldName)).doubleValue();
								stat.setDouble(c + offset, theVal);
							}
							else if( theType == String.class )
							{
								stat.setString(c + offset, (String)strObj.getFieldValue(fieldName));
							}
							else
							{
								// Urgh.
								throw new ObjectDBError("Don't know type for variable " + fieldName);
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

				stat.executeUpdate();

				// Set the ID only AFTER we store!
				if (id == 0)
				{
					stat = dbConn.prepareStatement("SELECT LAST_INSERT_ID()");
					ResultSet results = stat.executeQuery();
					if (results.first())
						strObj.setId(results.getInt(1));
					else
						throw new ObjectDBError("Couldn't get the ID of the object which was saved...");
				}
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
		else
		{
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
						catch (ClassCastException e)
						{
							System.err.println("ERROR: Cast exception saving object class '" + strObj.getClassName() + "' property '" + fieldName + "', check ObjectDB wrapper implementation.");
							System.err.println(e);
							e.printStackTrace();
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
