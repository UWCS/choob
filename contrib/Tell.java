import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;


// Note: This send/watch couple will break if someone changes their primary nick between the send and the receive, assuming they change their base nick.. it could be done otherwise, but Faux can't think of a way that doesn't involve mass database rapeage on every line sent by irc.
// This entire plugin could do with some caching.


class Tell
{
	public void create()
	{
		Connection dbConnection = modules.dbBroker.getConnection();
		PreparedStatement Smt = dbConnection.prepareStatement("CREATE TABLE IF NOT EXISTS `Tells` ( `Sent` TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL, `From` TINYTEXT NOT NULL, `Target` TINYTEXT NOT NULL, `Message` TINYTEXT NOT NULL );");
		Smt.executeUpdate();
	}

	public void commandSend( Message con, Modules mods, IRCInterface irc )
	{
		int targets = 0;
		Connection dbConnection = modules.dbBroker.getConnection();
		PreparedStatement insertLine = dbConnection.prepareStatement("INSERT INTO `Tells` VALUES(NOW(),?,?,?);");

		// Note: This is intentionally not translated to a primary nick.
		insertLine.setString(1, con.getNick()); // 'From'.

		Pattern pa = Pattern.compile("^[^ ]+? ([a-zA-Z0-9_,|-]+) (.*)$");
		Matcher ma = pa.matcher(con.getText());

		if (!ma.matches())
		{
			irc.sendContextMessage(con, "Syntax error.");
			return;
		}

		insertLine.setString(3, ma.group(2)); // 'Message'.

		StringTokenizer tokens = new StringTokenizer(ma.group(1), ",");
		while( tokens.hasMoreTokens() )
		{
			// Note: This call to getBestPrimaryNick is not optimal, discussed above.
			insertLine.setString(2, modules.nick.getBestPrimaryNick(tokens.nextToken())); // 'Target'.
			insertLine.executeUpdate();
			targets++;
		}

		irc.sendContextMessage(con, "Okay, will tell upon next speaking. (Sent to " + targets + " " + (targets == 1 ? "person" : "people") + ").");
	}

	public String filterWatchRegex = "";

	public void filterWatch( Message con, Modules modules, IRCInterface irc )
	{
		Connection dbConnection = modules.dbBroker.getConnection();
		PreparedStatement Smt = dbConnection.prepareStatement("SELECT * FROM `Tells` WHERE `Target` = ?;");
		Smt.setString(1, modules.nick.getBestPrimaryNick(con.getNick));

		ResultSet Results = Smt.executeQuery();

		if ( Results.first() )
			do
			{
				irc.sendPrivateMessage(con.getNick(), "At " + Results.getString("Date") + ", " + Results.getString("From") + " told me to tell you: " + + Results.getString("Message"));
			}
			while ( Results.next() );

		modules.dbBroker.freeConnection(dbConnection);
	}

	/*
		onJoin()
		{
			Do as filterWatch does();
			Mark the user as having received all their tells?();
		}
	*/
}
