/** @author Faux */

import java.io.PrintWriter;
import java.sql.*;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.*;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

public class See
{

	final SimpleDateFormat sdfa = new SimpleDateFormat("Kaa ");
	final SimpleDateFormat sdfb = new SimpleDateFormat("EEEE");

	private Modules mods;
	private IRCInterface irc;
	public See(IRCInterface irc, Modules mods)
	{
		this.mods = mods;
		this.irc = irc;
	}

	String timeStamp(Timestamp d)
	{
		return mods.date.timeStamp((new java.util.Date()).getTime()-d.getTime(), false, 3, uk.co.uwcs.choob.modules.DateModule.TimeUnit.MINUTE);
	}

	private final synchronized ResultSet getDataFor(final String nick, final Connection conn) throws SQLException
	{
		return getDataFor(nick, conn, 5);
	}
	private final synchronized ResultSet getDataFor(final String nick, final Connection conn, int days) throws SQLException
	{
		final Statement stat=conn.createStatement();

		stat.execute("DROP TEMPORARY TABLE IF EXISTS `tempt1`, `tempt2`; ");

		{
			final PreparedStatement s=conn.prepareStatement("CREATE TEMPORARY TABLE `tempt1` AS SELECT `Time` FROM `History` WHERE `Time` > " +  (System.currentTimeMillis()-(1000*60*60*24*days)) + " AND (CASE INSTR(`Nick`,'|') WHEN 0 THEN `Nick` ELSE LEFT(`Nick`, INSTR(`Nick`,'|')-1) END)=? AND `Channel`IS NOT NULL ORDER BY `Time`; ");
			s.setString(1, nick);
			s.executeUpdate();
		}

		stat.execute("ALTER TABLE `tempt1` ADD `index` INT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST; ");

		stat.execute("CREATE TEMPORARY TABLE `tempt2` as SELECT * from `tempt1`; " );
		stat.execute("UPDATE `tempt2` SET `index`:=`index`+1; " );
		stat.execute("ALTER TABLE `tempt2` ADD PRIMARY KEY ( `index` ); ");

		return conn.prepareStatement("SELECT DATE_ADD(FROM_UNIXTIME( `tempt1`.`Time` /1000 ), INTERVAL ((`tempt2`.`Time` - `tempt1`.`Time` ) /1000) SECOND) as `start`, FROM_UNIXTIME( `tempt1`.`Time` /1000 ) AS `end`, ((`tempt1`.`Time` - `tempt2`.`Time` ) /1000 /3600) AS `diff` FROM `tempt2` INNER JOIN `tempt1` ON `tempt2`.`index` = `tempt1`.`index` HAVING `diff` > 6;").executeQuery();
	}

	public final synchronized void commandBodyClock( Message mes ) throws SQLException
	{
		String nick=mods.util.getParamString(mes).trim();

		if (nick.equals(""))
			nick=mes.getNick();

		nick=mods.nick.getBestPrimaryNick(nick);

		final Connection conn=mods.odb.getConnection();

		ResultSet rs = getDataFor(nick, conn);

		String ret="";

		if (!rs.last())
			irc.sendContextReply(mes, "I don't have enough information to work out the bodyclock for " + nick + ".");
		else
		{
			final Timestamp gotup=rs.getTimestamp("end");
			final long diff=rs.getTimestamp("end").getTime() - rs.getTimestamp("start").getTime();

			float bodyclock=8.0f+(((float)((new java.util.Date()).getTime()-gotup.getTime()))/(1000.0f*60.0f*60.0f));

			long minutes=(Math.round((bodyclock-Math.floor(bodyclock))*60.0f));

			if (minutes == 60)
			{
				minutes=0;
				bodyclock++;
			}

			ret+=nick + " probably got up " + timeStamp(gotup) + " ago after " +
				mods.date.timeStamp(diff, false, 2, uk.co.uwcs.choob.modules.DateModule.TimeUnit.HOUR) + " of sleep, making their body-clock time about " +
				((int)Math.floor(bodyclock) % 24) + ":" + (minutes < 10 ? "0" : "") + minutes;

			irc.sendContextReply(mes, ret + ".");
		}

		mods.odb.freeConnection(conn);
	}


	private final String datelet(Date d)
	{
		return sdfa.format(d).toLowerCase() + sdfb.format(d);
	}

	public final synchronized void commandPattern( Message mes ) throws SQLException
	{
		String nick=mods.util.getParamString(mes).trim();

		if (nick.equals(""))
			nick=mes.getNick();

		nick=mods.nick.getBestPrimaryNick(nick);

		final Connection conn=mods.odb.getConnection();

		ResultSet rs = getDataFor(nick, conn);

		if (!rs.first())
			irc.sendContextReply(mes, "I don't have enough information about " + nick + ".");
		else
		{
			rs.beforeFirst();


			String ret=nick + " was sleeping: ";
			while (rs.next())
			{
				final Date start = new Date(rs.getTimestamp("start").getTime());
				final Date end = new Date(rs.getTimestamp("end").getTime());
				ret += datelet(start) + " -> " + datelet(end) + ", ";
			}

			if (ret.length()>2)
				ret = ret.substring(0, ret.length() -2);
			irc.sendContextReply(mes, ret + ".");
		}


		mods.odb.freeConnection(conn);
	}

	public synchronized void webDump(PrintWriter out, String args, String[] from)
	{
		try
		{
			out.println("HTTP/1.0 200 OK");
			out.println("Content-Type: text/plain");
			out.println();

			{
				String nick = args;

				final Connection conn=mods.odb.getConnection();

				final ResultSet rs = getDataFor(nick, conn, 21);

				if (!rs.first())
					return;
				else
				{
					rs.beforeFirst();

					while (rs.next())
					{
						final Date start = new Date(rs.getTimestamp("start").getTime());
						final Date end = new Date(rs.getTimestamp("end").getTime());
						out.println(start.getTime() + " " + end.getTime());
					}
				}

				mods.odb.freeConnection(conn);
			}
		}
		catch (Throwable t)
		{
			out.println("ERROR!");
			t.printStackTrace();
		}
	}

//* LA LA LA REMMED OUT AND INVISIBLE
	public void commandMidday( Message mes ) throws SQLException
	{
		final Connection conn=mods.odb.getConnection();
		try
		{
			String nick=mods.util.getParamString(mes).trim();
			String message;

			float t;
			if (nick.equals(""))
			{
				float rt=0;
				final Set<String> nickset = new HashSet<String>(); 
				for (String n : irc.getUsers(mes.getContext()))
					nickset.add(mods.nick.getBestPrimaryNick(n));
				String[] nicks = nickset.toArray(new String[0]);

				int succ = 0, fail=0;
				for (String n : nicks)
				{
					try
					{
						final float md = midday(n, conn);
						System.out.println(n + "\t" + md);
						rt+=md;

						++succ;
					}
					catch (RuntimeException e)
					{
						++fail;
					}
				}

				if (succ < 2)
				{
					irc.sendContextReply(mes, "Not enough users in " + mes.getContext() + ".");
					return;
				}
				message = "From " + succ + " users in " + mes.getContext() + ", the average";

				t = rt/(float)succ;
			}
			else
			{
				t = midday(nick=mods.nick.getBestPrimaryNick(nick), conn);
				message = nick + "'s";
			}
			final int qnr = (int)((t-(int)t)*60);
			irc.sendContextReply(mes, message + " midday is " + (int)t + ":" + (qnr < 10 ? "0" : "") + qnr + ".");
		}
		finally
		{
			mods.odb.freeConnection(conn);
		}

	}

	private float midday( String nick, final Connection conn) throws SQLException
	{
		// if they wern't awake this long, it doesn't count.
		final int minday = 7*60*60*1000;

		nick = mods.nick.getBestPrimaryNick(nick);

		ResultSet rs = getDataFor(nick, conn);

		int c = 0;
		float midday = 0;

		if (!rs.first())
			throw new RuntimeException("No data for " + nick + ". Cannot continue.");
		else
		{
			rs.beforeFirst();
			Date lastEnd = null;

			while (rs.next())
			{
				final Date start = new Date(rs.getTimestamp("start").getTime());
				final Date end = new Date(rs.getTimestamp("end").getTime());

				if (lastEnd != null)
				{
					long foo = -(lastEnd.getTime() - start.getTime());
					if (foo > minday)
					{
						Calendar cal = new GregorianCalendar();
						cal.setTime(new Date(end.getTime() +  foo * (12-8)/(24-8)));
						midday += cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE)/60.0;
						c++;
					}
				}
				lastEnd = start;
			}

		}

		if (c==0)
			throw new RuntimeException(nick + midday);
		return midday/(float)c;
	}
// */
}
