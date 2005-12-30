/** @author Amorya */

import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.sql.*;
import java.net.*;
import java.io.*;


class Fish
{
	public void commandRepeatMe( Message con, Modules mods, IRCInterface irc )
	{
		DbConnectionBroker broker = mods.dbBroker;
		Connection dbConnection = broker.getConnection();
		PreparedStatement coreplugSmt = dbConnection.prepareStatement("SELECT * FROM History ORDER BY LineID DESC LIMIT 2;");
		ResultSet coreplugResults = coreplugSmt.executeQuery();

		int flag = 0;

		if ( coreplugResults.first() )
			do
			{
				if (flag==0) {
					flag = 1;
				} else {
					irc.sendContextMessage(con, coreplugResults.getString("Text"));
				}
			}
			while ( coreplugResults.next() );

		broker.freeConnection(dbConnection);
	}

	public void commandWUBRS( Message con, Modules mods, IRCInterface irc )
	{
		URL yahoo = new URL("http://www.wubrs.org.uk/quote.php");
		URLConnection yc = yahoo.openConnection();
		BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream()));
		String inputLine;

		while ((inputLine = in.readLine()) != null)
			irc.sendContextMessage(con, inputLine);
		in.close();
	}
}
