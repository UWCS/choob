/** @author Amorya */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.modules.ObjectDbModule;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;


class Fish
{
	public void commandRepeatMe( final Message con, final Modules mods, final IRCInterface irc ) throws SQLException
	{
		final ObjectDbModule broker = mods.odb;
		final Connection dbConnection = broker.getConnection();
		PreparedStatement coreplugSmt = null;
		try
		{
			coreplugSmt = dbConnection.prepareStatement("SELECT * FROM History ORDER BY LineID DESC LIMIT 2;");
			final ResultSet coreplugResults = coreplugSmt.executeQuery();

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

	public void commandWUBRS( final Message con, final Modules mods, final IRCInterface irc ) throws IOException
	{
		final URL yahoo = new URL("http://www.wubrs.org.uk/quote.php");
		final URLConnection yc = yahoo.openConnection();
		final BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream()));
		String inputLine;

		while ((inputLine = in.readLine()) != null)
			irc.sendContextMessage(con, inputLine);
		in.close();
	}
}
