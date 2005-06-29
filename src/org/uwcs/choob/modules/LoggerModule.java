/*
 * LoggerModule.java
 *
 * Created on June 25, 2005, 10:29 PM
 */

package org.uwcs.choob.modules;

import org.uwcs.choob.*;
import java.sql.*;
import org.uwcs.choob.support.*;
/**
 * Logs lines from IRC to the database.
 * @author sadiq
 */
public class LoggerModule
{
    DbConnectionBroker dbBroker;
    
    /** Creates a new instance of LoggerModule */
    public LoggerModule(DbConnectionBroker dbBroker)
    {
        this.dbBroker = dbBroker;
    }
    
    /**
     * Logs a line from IRC to the database.
     * @param con {@link Context} object representing the line from IRC.
     * @throws Exception Thrown from the database access, potential SQL or IO exceptions.
     */    
    public void addLog( Context con ) throws Exception
    {
        try
        {
        Connection dbConnection = dbBroker.getConnection();
        
        PreparedStatement insertLine = dbConnection.prepareStatement("INSERT INTO History VALUES(NULL,?,?,?,?,?)");
        
        insertLine.setString(1, con.getNick());
        insertLine.setString(2, con.getChannel());
        insertLine.setString(3, con.getText());
        insertLine.setTimestamp(4, new Timestamp(con.getMillis()));
        insertLine.setInt(5, con.getRandom());
        
        insertLine.executeUpdate();
        
        dbBroker.freeConnection( dbConnection );
        }
        catch( Exception e )
        {
            throw new Exception("Could not write history line to database.", e);
        }
    }
    
}
