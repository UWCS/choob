/** @author Faux */

import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.sql.*;
import java.text.*;
import java.io.*;
import java.text.DateFormatSymbols;

public class See
{

	private Modules mods;
	private IRCInterface irc;
	public See(IRCInterface irc, Modules mods)
	{
		this.mods = mods;
		this.irc = irc;
	}

	String timeStamp(Timestamp d)
	{
		return mods.util.timeLongStamp((new java.util.Date()).getTime()-d.getTime(), 2);
	}

	public final synchronized void commandBodyClock( Message mes ) throws SQLException
	{
		final String nick=mods.util.getParamString(mes).trim();

		if (nick.length() < 3)
		{
			irc.sendContextReply(mes, "Please specify a real nick.");
			return;
		}

		final Connection conn=mods.odb.getConnection();

		{
			final Statement stat=conn.createStatement();

			stat.execute("DROP TEMPORARY TABLE IF EXISTS `tempt1`, `tempt2`; ");

			{
				final PreparedStatement s=conn.prepareStatement("CREATE TEMPORARY TABLE `tempt1` AS SELECT `Time` FROM `History` WHERE (CASE INSTR(`Nick`,'|') WHEN 0 THEN `Nick` ELSE LEFT(`Nick`, INSTR(`Nick`,'|')-1) END)=? ORDER BY `Time`; ");
				s.setString(1, nick);
				s.executeUpdate();
			}

			stat.execute("ALTER TABLE `tempt1` ADD `index` INT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST; ");

			stat.execute("CREATE TEMPORARY TABLE `tempt2` as SELECT * from `tempt1`; " );
			stat.execute("UPDATE `tempt2` SET `index`:=`index`+1; " );
			stat.execute("ALTER TABLE `tempt2` ADD PRIMARY KEY ( `index` ); ");
		}

		final PreparedStatement sel=conn.prepareStatement("SELECT DATE_ADD(FROM_UNIXTIME( `tempt1`.`Time` /1000 ), INTERVAL ((`tempt2`.`Time` - `tempt1`.`Time` ) /1000) SECOND) as `start`, FROM_UNIXTIME( `tempt1`.`Time` /1000 ) AS `end`, ((`tempt1`.`Time` - `tempt2`.`Time` ) /1000 /3600) AS `diff` FROM `tempt2` INNER JOIN `tempt1` ON `tempt2`.`index` = `tempt1`.`index` HAVING `diff` > 6;");

		ResultSet rs = sel.executeQuery();
		String ret="";

		if (!rs.last())
		{
			irc.sendContextReply(mes, "Haven't seen " + nick + "!");
		}
		else
		{
			final Timestamp gotup=rs.getTimestamp("end");
			final long diff=rs.getTimestamp("end").getTime() - rs.getTimestamp("start").getTime();

			final long bodyclock=8+(((new java.util.Date()).getTime()-gotup.getTime())/1000/60/60); // int division?

			ret+=nick + " probably got up " + timeStamp(gotup) + " ago after " + mods.util.timeLongStamp(diff, 1) + " of sleep, making their body-clock time about " + bodyclock + ":00";

			irc.sendContextReply(mes, ret + ".");
		}

		mods.odb.freeConnection(conn);
	}

}
