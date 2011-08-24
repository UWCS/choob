package uk.co.uwcs.choob.support;

import java.lang.reflect.Type;
import java.security.AccessController;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mozilla.javascript.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.modules.ObjectDbModule;

/**
 * Wraps up the database in an ObjectDB-friendly way, which can be used to
 * perform various operations, such as adding, replacing and deleting items
 * in a single transaction (so either all, or none, of the operations occur).
 *
 * Plugins (with the necessary permissions) and core code can use it thus:
 *
 * <pre>
 *   mods.odb.runTransaction(
 *       new ObjectDBTransaction() {
 *           public void run() {
 *               // ObjectDB operations here, e.g.
 *               //   delete(o);
 *               //   save(o);
 *           }
 *       });
 * </pre>
 *
 */
public class ObjectDBTransaction // Needs to be non-final
{
	private static final Logger logger = LoggerFactory.getLogger(ObjectDBTransaction.class);

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

	/**
	 * Starts the transaction, during which <i>all</i> operations will either
	 * succeed or fail.
	 *
	 * The transaction is completed when {@link #commit} is called, or canceled
	 * if {@link #rollback} is called. {@link #finish} should always be called
	 * after {@link #commit} or {@link #rollback} is called, and is the mirror
	 * function to this one.
	 *
	 * @throws ObjectDBDeadlockError If the database has detected a deadlock,
	 *                               this exception is thrown.
	 * @throws ObjectDBError All other SQL-related exceptions are wrapped as
	 *                       ObjectDBError.
	 */
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

	/**
	 * Commits the transaction started by {@link #begin}, attempting to
	 * apply all operations to the database. It is possible that this will
	 * fail if, for example, the data being modified by this transaction has
	 * already been modified by another successful transaction.
	 * {@link #finish} must still be called afterwards.
	 *
	 * @throws ObjectDBDeadlockError If the database has detected a deadlock,
	 *                               this exception is thrown.
	 * @throws ObjectDBError All other SQL-related exceptions are wrapped as
	 *                       ObjectDBError.
	 */
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

	/**
	 * Cancels the transaction started by {@link #begin}, causing all
	 * modifications made since then to be discarded.
	 * {@link #finish} must still be called afterwards.
	 *
	 * @throws ObjectDBDeadlockError If the database has detected a deadlock,
	 *                               this exception is thrown.
	 * @throws ObjectDBError All other SQL-related exceptions are wrapped as
	 *                       ObjectDBError.
	 */
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

	/**
	 * Finishes off the transaction started by {@link #begin}. If
	 * {@link #commit} has not been called, an implicit {@link #rollback}
	 * call is made first.
	 *
	 * @throws ObjectDBDeadlockError If the database has detected a deadlock,
	 *                               this exception is thrown.
	 * @throws ObjectDBError All other SQL-related exceptions are wrapped as
	 *                       ObjectDBError.
	 */
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
		logger.error("Ack! SQL Exception: ", e);
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

		dbTypeMap.put("longtext", TYPE_TEXT); // Allow longtext in database.

		dbTypeMap.put("mediumtext", TYPE_TEXT); // Allow mediumtext in database. Mysql auto-converts to mediumtext sometimes.
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

	/**
	 * Loads any number of stored ObjectDB objects.
	 *
	 * @param storedClass The class object (decendant of {@link Class} for Java,
	 *                    {@link Function} for JavaScript) representing the
	 *                    type of object desired to be retrieved.
	 * @param clause The testricting part of the query, specifying which objects
	 *               are desired. FIXME: link to docs on format.
	 * @return {@link List} of objects, typed according to the caller.
	 */
	@SuppressWarnings("rawtypes")
	public final List retrieve(Object storedClass, String clause)
	{
		return retrieve(NewClassWrapper(storedClass), clause);
	}

	/**
	 * Loads any number of stored ObjectDB objects.
	 *
	 * @param storedClass The {@link ObjectDBClass} indicating the type of
	 *                    object desired to be retrieved.
	 * @param clause The testricting part of the query, specifying which objects
	 *               are desired. FIXME: link to docs on format.
	 * @return {@link List} of objects, typed according to the caller.
	 */
	@SuppressWarnings("rawtypes")
	public final List retrieve(final ObjectDBClass storedClass, String clause)
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
		int idFieldIndex = 0;
		StringBuilder fieldNames = new StringBuilder();
		for(int i=0; i<fields.length; i++)
		{
			fieldNames.append("`" + clean("`", fields[i]) + "`");
			if (i != fields.length - 1)
				fieldNames.append(", ");
			if (fields[i].equals("id"))
				idFieldIndex = i;
		}
		select = fieldNames.toString();

		ObjectDBClauseParser parser = new ObjectDBClauseParser("SELECT " + select + " " + clause, storedClass.getName());
		parser.setUseMany(true);
		try
		{
			sqlQuery = parser.ODBExpr();
		}
		catch (ParseException e)
		{
			// TODO there's some public properties we can use to make a better error message.
			logger.error("Parse error in string: " + clause);
			logger.error("Error was: " + e);
			throw new ObjectDBError("Parse error in clause string: " + clause);
		}

		// Make sure it's the right query type... (XXX Do we need to?)
		if (parser.getType() != ObjectDBClauseParser.TYPE_SELECT)
			throw new ObjectDBError("Clause string " + clause + " was not a SELECT clause.");

		// Make sure we can read these classes...
		@SuppressWarnings("unchecked")
		List<String> classNames = parser.getUsedClasses();
		for(String cls: classNames)
			checkPermission(cls);

		checkTable(storedClass);

		Statement objStat = null;
		Statement retrieveStat = null;
		try
		{
			final List<Object> objects = new ArrayList<Object>();
			final Set<Integer> objectIds = new HashSet<Integer>();

			objStat = dbConn.createStatement();
			retrieveStat = dbConn.createStatement();

			ResultSet allObjects = objStat.executeQuery( sqlQuery );

			Map<String,Type> fieldTypeCache = new HashMap<String,Type>();

			if( allObjects.first() )
			{
				do // Loop over all objects
				{
					// Ensure we never include an object more than once.
					int objectId = (int)allObjects.getLong(idFieldIndex + 1);
					if (objectIds.contains(objectId))
						continue;

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
					objectIds.add(tempObject.getId());
				}
				while ( allObjects.next() ); // Looping over blocks of IDs
			}

			return objects;
		}
		catch (NoSuchFieldException e)
		{
			logger.error("Field that did exist now doesn't. Ooops?", e);
			// This should never happen...
			throw new ObjectDBError("Field that did exist now doesn't. Ooops?", e);
		}
		catch (InstantiationException e)
		{
			logger.error("Error instantiating object of type " + storedClass + ": ", e);
			throw new ObjectDBError("The object could not be instantiated.");
		}
		catch (IllegalAccessException e)
		{
			logger.error("Access error instantiating object of type " + storedClass + ": ", e);
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
				logger.error("Parse error in string: " + clause);
				logger.error("Error was: " + e);
				throw new ObjectDBError("Parse error in clause string: " + clause);
			}

			// Make sure it's the right query type... (XXX Do we need to?)
			if (parser.getType() != ObjectDBClauseParser.TYPE_SELECT)
				throw new ObjectDBError("Clause string " + clause + " was not a SELECT clause.");

			// Make sure we can read these classes...
			@SuppressWarnings("unchecked")
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
				logger.error("Parse error in string: " + clause);
				logger.error("Error was: " + e);
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

	/**
	 * Deletes an object from the ObjectDB.
	 *
	 * @param strObj The object to be deleted.
	 */
	public final void delete(Object strObj)
	{
		delete(NewObjectWrapper(strObj));
	}

	/**
	 * Deletes an object from the ObjectDB.
	 *
	 * @param strObj The {@link ObjectDBObject} wrapping the real object to be
	 *               deleted.
	 */
	public final void delete(ObjectDBObject strObj)
	{
		checkPermission(strObj.getClassName());
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

	/**
	 * Updates the saved data for an ObjectDB object.
	 *
	 * @param strObj The object who's saved data is to be updated.
	 */
	public final void update(Object strObj)
	{
		update(NewObjectWrapper(strObj));
	}

	/**
	 * Updates the saved data for an ObjectDB object.
	 *
	 * @param strObj The {@link ObjectDBObject} wrapping the real object who's
	 *               saved data is to be updated.
	 */
	public final void update(ObjectDBObject strObj)
	{
		_store(strObj, true);
	}

	/**
	 * Method to be override in code that wishes to use the transaction support.
	 *
	 * This method is run when passed to {@link ObjectDbModule#runTransaction},
	 * inside calls to {@link #begin} and {@link #commit}. If this method throws
	 * an exception, {@link #rollback} is called instead.
	 */
	public void run()
	{
		throw new ObjectDBError("This transaction has no run() method...");
	}

	/**
	 * Saves a new ObjectDB object.
	 *
	 * @param strObj The object to be saved.
	 */
	public final void save(Object strObj)
	{
		save(NewObjectWrapper(strObj));
	}

	/**
	 * Saves a new ObjectDB object.
	 *
	 * @param strObj The {@link ObjectDBObject} wrapping the real object
	 *               to be saved.
	 */
	public final void save(ObjectDBObject strObj)
	{
		_store(strObj, false);
	}

	private final void _store(ObjectDBObject strObj, boolean replace)
	{
		checkPermission(strObj.getClassName());
		checkTable(strObj);
		PreparedStatement stat = null;
		try
		{
			int id = strObj.getId();

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
