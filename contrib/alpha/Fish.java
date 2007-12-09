/** @author Amorya */

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.sql.*;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.modules.ObjectDbModule;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;


class Fish
{
	public void commandRepeatMe( Message con, Modules mods, IRCInterface irc ) throws SQLException
	{
		ObjectDbModule broker = mods.odb;
		Connection dbConnection = broker.getConnection();
		PreparedStatement coreplugSmt = null;
		try
		{
			coreplugSmt = dbConnection.prepareStatement("SELECT * FROM History ORDER BY LineID DESC LIMIT 2;");
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
		}
		finally
		{
			broker.freeConnection(dbConnection);
		}
	}

	public void commandWUBRS( Message con, Modules mods, IRCInterface irc ) throws IOException
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
