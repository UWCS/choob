/**
 * DbConnectionBroker.
 * @version 1.0.13 3/12/02
 * @author Marc A. Mnich
 */
package org.uwcs.choob.support;
import com.mchange.v2.c3p0.*;
import java.io.*;
import java.sql.*;

public final class DbConnectionBroker
{
	ComboPooledDataSource cpds;

	private final static int DEFAULTMAXCHECKOUTSECONDS=60;
	private final static int DEFAULTDEBUGLEVEL=0;

	/**
	 * Creates a new Connection Broker<br>
	 * dbDriver:			JDBC driver. e.g. 'oracle.jdbc.driver.OracleDriver'<br>
	 * dbServer:			JDBC connect string. e.g. 'jdbc:oracle:thin:@203.92.21.109:1526:orcl'<br>
	 * dbLogin:				Database login name.  e.g. 'Scott'<br>
	 * dbPassword:			Database password.	e.g. 'Tiger'<br>
	 * minConns:			Minimum number of connections to start with.<br>
	 * maxConns:			Maximum number of connections in dynamic pool.<br>
	 * logFileString:		Absolute path name for log file. e.g. 'c:/temp/mylog.log' <br>
	 * maxConnTime:			Time in days between connection resets. (Reset does a basic cleanup)<br>
	 * logAppend:			Append to logfile (optional)<br>
	 * maxCheckoutSeconds:	Max time a connection can be checked out before being recycled. Zero value turns option off, default is 60 seconds.
	 * debugLevel:			Level of debug messages output to the log file.  0 -> no messages, 1 -> Errors, 2 -> Warnings, 3 -> Information
	 * @deprecated
	 */
	public DbConnectionBroker(String dbDriver, String dbServer, String dbLogin, String dbPassword, int minConns, int maxConns, String logFileString, double maxConnTime) throws IOException, ChoobException, SQLException
	{
		setupBroker(dbDriver, dbServer, dbLogin, dbPassword, minConns, maxConns, logFileString, maxConnTime, false, DEFAULTMAXCHECKOUTSECONDS, DEFAULTDEBUGLEVEL);
	}

	/*
	 * @deprecated
	 */
	public DbConnectionBroker(String dbDriver, String dbServer, String dbLogin, String dbPassword, int minConns, int maxConns, String logFileString, double maxConnTime, boolean logAppend) throws SQLException, IOException
	{
		setupBroker(dbDriver, dbServer, dbLogin, dbPassword, minConns, maxConns, logFileString, maxConnTime, logAppend, DEFAULTMAXCHECKOUTSECONDS, DEFAULTDEBUGLEVEL);
	}

	/*
	 * @deprecated
	 */
	public DbConnectionBroker(String dbDriver, String dbServer, String dbLogin, String dbPassword, int minConns, int maxConns, String logFileString, double maxConnTime, boolean logAppend, int maxCheckoutSeconds, int debugLevel) throws SQLException, IOException
	{
		setupBroker(dbDriver, dbServer, dbLogin, dbPassword, minConns, maxConns, logFileString, maxConnTime, logAppend, maxCheckoutSeconds, debugLevel);
	}

	/**
	 * Constructor that doesn't take anything c3p0 doesn't understand, all of the others are appropriately deprecated.
	 * dbDriver:			JDBC driver. e.g. 'oracle.jdbc.driver.OracleDriver'<br />
	 * dbServer:			JDBC connect string. e.g. 'jdbc:oracle:thin:@203.92.21.109:1526:orcl'<br />
	 * dbLogin:				Database login name.  e.g. 'Scott'<br />
	 * dbPassword:			Database password.	e.g. 'Tiger'<br />
	 * minConns:			Minimum number of connections to start with.<br />
	 * maxConns:			Maximum number of connections in dynamic pool.<br />
	 * logFile:				PrintWriter to log to.<br />
	 * maxCheckoutSeconds:	Max time a connection can be checked out before being recycled. Zero value turns option off, default is 60 seconds.
	 */
	public DbConnectionBroker(String dbDriver, String dbServer, String dbLogin, String dbPassword, int minConns, int maxConns, PrintWriter logFile, int maxCheckoutSeconds) throws SQLException
	{
		setupBroker(dbDriver, dbServer, dbLogin, dbPassword, minConns, maxConns, logFile, maxCheckoutSeconds);
	}

	/**
	 * @deprecated
	 */
	private void setupBroker(String dbDriver, String dbServer, String dbLogin, String dbPassword, int minConns, int maxConns, String logFileString, double maxConnTime, boolean logAppend, int maxCheckoutSeconds, int debugLevel) throws SQLException, IOException
	{
		setupBroker(dbDriver, dbServer, dbLogin, dbPassword, minConns, maxConns, new PrintWriter(new FileOutputStream(logFileString)), maxCheckoutSeconds);
	}
//
	private void setupBroker(String dbDriver, String dbServer, String dbLogin, String dbPassword, int minConns, int maxConns, PrintWriter logFile, int maxCheckoutSeconds) throws SQLException	{
		cpds = new ComboPooledDataSource();
		try
		{
			cpds.setDriverClass(dbDriver);
		}
		catch (java.beans.PropertyVetoException e)
		{
			e.printStackTrace();
			throw new SQLException("Error setting driver class.");
		}
		cpds.setJdbcUrl(dbServer);
		cpds.setUser(dbLogin);
		cpds.setPassword(dbPassword);
		cpds.setMinPoolSize(minConns);
		cpds.setMaxPoolSize(maxConns);
		cpds.setLogWriter(logFile);
		cpds.setCheckoutTimeout(maxCheckoutSeconds*1000);

		cpds.setIdleConnectionTestPeriod(300); // Execute a getTables() after idle for 5 mins.
		cpds.setAutomaticTestTable("C3P0TestTable"); // C3P0 will create this.
	}

	public Connection getConnection() throws SQLException
	{
		return cpds.getConnection();
	}


	public void freeConnection(Connection conn)
	{
		if (conn!=null)
			try
			{
				conn.close();
			}
			catch (SQLException e)
			{
				e.printStackTrace();
			}
	}

	public void destroy() throws SQLException
	{
		DataSources.destroy(cpds);
	}

}
