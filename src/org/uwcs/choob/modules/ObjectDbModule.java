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

	/** Creates a new instance of ObjectDbModule */
	public ObjectDbModule(DbConnectionBroker broker)
	{
		this.broker = broker;
	}

	public List retrieve(Class storedClass, String clause) throws ChoobException
	{
		Connection dbConn = broker.getConnection();
		ObjectDBTransaction trans = new ObjectDBTransaction();
		trans.setConn(dbConn);
		try
		{
			return trans.retrieve(storedClass, clause);
		}
		finally
		{
			broker.freeConnection( dbConn );
		}
	}

	public void delete( Object strObject ) throws ChoobException
	{
		Connection dbConn = broker.getConnection();
		ObjectDBTransaction trans = new ObjectDBTransaction();
		trans.setConn(dbConn);
		try
		{
			trans.delete( strObject );
		}
		finally
		{
			broker.freeConnection( dbConn );
		}
	}

	public void update( Object strObject ) throws ChoobException
	{
		Connection dbConn = broker.getConnection();
		ObjectDBTransaction trans = new ObjectDBTransaction();
		trans.setConn(dbConn);
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

	public void save( Object strObject ) throws ChoobException
	{
		Connection dbConn = broker.getConnection();
		ObjectDBTransaction trans = new ObjectDBTransaction();
		trans.setConn(dbConn);
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

	public void runTransaction( ObjectDBTransaction trans ) throws ChoobException
	{
		Connection dbConn = broker.getConnection();
		trans.setConn(dbConn);
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
