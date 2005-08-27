/*
 * LoggerModule.java
 *
 * Created on June 25, 2005, 10:29 PM
 */

package org.uwcs.choob.modules;

import org.uwcs.choob.*;
import java.sql.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
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
    public void addLog( Message mes ) throws Exception
    {
        try
        {
        Connection dbConnection = dbBroker.getConnection();

        PreparedStatement insertLine = dbConnection.prepareStatement("INSERT INTO History VALUES(NULL,?,?,?,?,?)");

        insertLine.setString(1, mes.getNick());
        String chan = "";
        if (mes instanceof ChannelEvent) {
            chan = ((ChannelEvent)mes).getChannel();
        }
        insertLine.setString(2, chan);
        insertLine.setString(3, mes.getMessage());
        insertLine.setTimestamp(4, new Timestamp(mes.getMillis()));
        insertLine.setInt(5, mes.getRandom());

        insertLine.executeUpdate();

        dbBroker.freeConnection( dbConnection );
        }
        catch( Exception e )
        {
            throw new Exception("Could not write history line to database.", e);
        }
    }

}
