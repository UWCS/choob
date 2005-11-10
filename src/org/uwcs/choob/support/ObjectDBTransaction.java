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

	public final void cleanUp(Statement stat) throws ChoobException
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

	private final ChoobException sqlErr(SQLException e)
	{
		System.err.println("Ack! SQL Exception: " + e);
		e.printStackTrace();
		return new ChoobException("An SQL exception occurred while processing this operation.", e);
	}

	public final List<?> retrieve(final Class storedClass, String clause) throws ChoobException
	{
		checkPermission(storedClass);
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

		Statement objStat = null;
		Statement retrieveStat = null;
		try
		{
			final List<Object> objects = new ArrayList<Object>();

			objStat = dbConn.createStatement();
			retrieveStat = dbConn.createStatement();

			ResultSet allObjects = objStat.executeQuery( sqlQuery );

			String baseQuery = "SELECT ClassID, FieldName, FieldBigInt, FieldDouble, FieldString FROM ObjectStore LEFT JOIN ObjectStoreData ON ObjectStore.ObjectID = ObjectStoreData.ObjectID WHERE ClassName = '" + storedClass.getName() + "' AND (";

			Map<String,Field> fieldCache = new HashMap<String,Field>();

			Field idField;
			try
			{
				idField = storedClass.getField( "id" );
			}
			catch( NoSuchFieldException e )
			{
				throw new ChoobException("Object of type " + storedClass + " has no id field!");
			}

			if( allObjects.first() )
			{
				do // Loop over all objects
				{
					// Eat this and maybe some more elements...
					int[] ids = new int[MAXOR];
					int count = 0;
					do
					{
						ids[count] = allObjects.getInt(1);
						count++;
					} while (allObjects.next() && count < MAXOR);

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
						throw new ChoobException ("Inconsistent database state: One or more objects of type " + storedClass.getName() + " in ObjectStore did not exist in ObjectStoreData.");
					}
					do // Loop over this block's results
					{
						try
						{
							Object tempObject = storedClass.newInstance();

							int id = result.getInt(1);

							idField.setInt( tempObject, id );

							do // Loop over this object's fields
							{
								try
								{
									// Break if we're in the next object
									if (result.getInt(1) != id)
									{
										objects.add(tempObject);
										tempObject = storedClass.newInstance();
										id = result.getInt(1);
										idField.setInt( tempObject, id );
									}

									String name = result.getString(2);
									Field tempField = fieldCache.get(name);
									if (tempField == null)
									{
										tempField = storedClass.getField( name );
										fieldCache.put( name, tempField );
									}

									Type theType = tempField.getType();

									if( theType == String.class )
									{
										tempField.set( tempObject, result.getString(5) );
									}
									else if( theType == Integer.TYPE )
									{
										tempField.setInt( tempObject, (int)result.getLong(3) );
									}
									else if( theType == Long.TYPE )
									{
										tempField.setLong( tempObject, result.getLong(3) );
									}
									else if( theType == Boolean.TYPE )
									{
										tempField.setBoolean( tempObject, result.getLong(3) == 1 );
									}
									else if( theType == Float.TYPE )
									{
										tempField.setFloat( tempObject, (float)result.getDouble(4) );
									}
									else if( theType == Double.TYPE )
									{
										tempField.setDouble( tempObject, result.getDouble(4) );
									}
								}
								catch( NoSuchFieldException e )
								{
									e.printStackTrace();
									// Ignore this, as per spec.
								}
							}
							while( result.next() ); // Looping over fields

							// tempObject has been built.
							objects.add(tempObject);
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
					} while ( result.next() ); // Looping over objects
				} while ( allObjects.next() ); // Looping over blocks of IDs
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

	public final List<Integer> retrieveInt(Class storedClass, String clause) throws ChoobException
	{
		checkPermission(storedClass);
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

	private final void populateObject( Object tempObject, ResultSet result ) throws ChoobException
	{
		try
		{
			setId( tempObject, result.getInt(1) );

			do
			{
				try
				{
					Field tempField = tempObject.getClass().getField( result.getString(2) );

					Type theType = tempField.getType();

					if( theType == String.class )
					{
						tempField.set( tempObject, result.getString(5) );
					}
					else if( theType == Integer.TYPE )
					{
						tempField.setInt( tempObject, (int)result.getLong(3) );
					}
					else if( theType == Long.TYPE )
					{
						tempField.setLong( tempObject, result.getLong(3) );
					}
					else if( theType == Boolean.TYPE )
					{
						tempField.setBoolean( tempObject, result.getLong(3) == 1 );
					}
					else if( theType == Float.TYPE )
					{
						tempField.setFloat( tempObject, (float)result.getDouble(4) );
					}
					else if( theType == Double.TYPE )
					{
						tempField.setDouble( tempObject, result.getDouble(4) );
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
		checkPermission(strObj.getClass());
		PreparedStatement delete = null, deleteData = null;
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
		checkPermission(strObj.getClass());
		PreparedStatement stat = null, field;
		try
		{
			int id = getId( strObj );

			if( id == 0 )
			{
				stat = dbConn.prepareStatement("SELECT MAX(ClassID) FROM ObjectStore WHERE ClassName = ?;");

				stat.setString(1, strObj.getClass().getName());

				ResultSet ids = stat.executeQuery();

				if( ids.first() )
					id = ids.getInt(1)+1;
				else
					id = 1;

				stat.close();

				setId( strObj, id );
			}

			stat = dbConn.prepareStatement("INSERT INTO ObjectStore VALUES(NULL,?,?);");

			stat.setString(1, strObj.getClass().getName());
			stat.setInt(2, id);

			stat.execute();

			ResultSet generatedKeys = stat.getGeneratedKeys();

			generatedKeys.first();

			int generatedID = generatedKeys.getInt(1);

			stat.close();

			stat = dbConn.prepareStatement("INSERT INTO ObjectStoreData VALUES(?,?,?,?,?);");

			Field[] fields = strObj.getClass().getFields();

			for( int c = 0 ; c < fields.length ; c++ )
			{
				Field tempField = fields[c];

				if( !tempField.getName().equals("id") )
				{
					boolean foundType = true;

					stat.setInt(1, generatedID);

					Type theType = tempField.getType();

					try
					{
						if( theType == java.lang.Integer.TYPE )
						{
							int theVal = tempField.getInt(strObj);
							stat.setString(2, tempField.getName());
							stat.setLong(3, theVal);
							stat.setDouble(4, theVal);
							stat.setString(5, Integer.toString(theVal));
						}
						else if( theType == java.lang.Long.TYPE )
						{
							long theVal = tempField.getLong(strObj);
							stat.setString(2, tempField.getName());
							stat.setLong(3, theVal);
							stat.setDouble(4, theVal);
							stat.setString(5, Long.toString(theVal));
						}
						else if( theType == java.lang.Boolean.TYPE )
						{
							boolean theVal = tempField.getBoolean(strObj);
							stat.setString(2, tempField.getName());
							stat.setLong(3, theVal ? 1 : 0);
							stat.setDouble(4, theVal ? 1 : 0);
							stat.setString(5, theVal ? "1" : "0");
						}
						else if( theType == java.lang.Float.TYPE )
						{
							float theVal = tempField.getFloat(strObj);
							stat.setString(2, tempField.getName());
							stat.setLong(3, (long)theVal);
							stat.setDouble(4, theVal);
							stat.setString(5, Float.toString(theVal));
						}
						else if( theType == java.lang.Double.TYPE )
						{
							double theVal = tempField.getDouble(strObj);
							stat.setString(2, tempField.getName());
							stat.setLong(3, (long)theVal);
							stat.setDouble(4, theVal);
							stat.setString(5, Double.toString(theVal));
						}
						else if( theType == String.class )
						{
							stat.setString(2, tempField.getName());
							stat.setLong(3, 0); // XXX - parse these or not parse these?
							stat.setDouble(4, 0);
							stat.setString(5, (String)tempField.get(strObj));
						}
						else
							foundType = false;

						if( foundType )
						{
							stat.executeUpdate();
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
		finally
		{
			cleanUp(stat);
		}
	}

	private final void checkPermission(Class objClass)
	{
		String plugin = mods.security.getPluginName(0);
		if (plugin != null)
		{
			String plugName = "plugins." + plugin.toLowerCase() + ".";
			String objName = objClass.getName().toLowerCase();
			if ( !objName.startsWith(plugName) )
				AccessController.checkPermission(new ChoobPermission("objectdb."+objClass.getName().toLowerCase()));
		}
		else
			AccessController.checkPermission(new ChoobPermission("objectdb."+objClass.getName().toLowerCase()));
	}
}
