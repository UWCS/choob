/** @author Faux */

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

public class Mood
{

	final SimpleDateFormat sdfa = new SimpleDateFormat("Kaa ");
	final SimpleDateFormat sdfb = new SimpleDateFormat("EEEE");

	private final Modules mods;
	private final IRCInterface irc;

	public Mood(final IRCInterface irc, final Modules mods)
	{
		this.mods = mods;
		this.irc = irc;
	}

	private int score(Connection conn, String what, long period) throws SQLException
	{
        final long midnight;
		if (0 == period) {
			final Calendar c = new GregorianCalendar();
    	    c.set(Calendar.HOUR_OF_DAY, 0);
	        c.set(Calendar.MINUTE, 0);
	        c.set(Calendar.SECOND, 0);
			midnight = c.getTimeInMillis();
		} else {
			midnight = new java.util.Date().getTime() - period;
		}

		final String cond;
		if (what.startsWith("#"))
			cond = "Channel like ?";
		else
			cond = "Nick like ?";

		final PreparedStatement s = conn
				.prepareStatement("select count(*) from History where " + cond + " and Time > ? and Text like ?");
		try
		{
			s.setString(1, what);
			s.setLong(2, midnight);
			s.setString(3, "%++%");
			final ResultSet u = s.executeQuery();
			int up = 0;
			try
			{
				if (u.next())
					up = u.getInt(1);
			}
			finally
			{
				u.close();
			}

			s.setString(3, "%--%");
			final ResultSet d= s.executeQuery();
			int down = 0;
			try
			{
				if (d.next())
					down = d.getInt(1);
			}
			finally
			{
				d.close();
			}
			return up-down;
		}
		finally
		{
			s.close();
		}
		
	}

	private String format(Date d)
	{
		return java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.FULL,
				java.text.DateFormat.FULL).format(d);
	}

	public String commandDay(final String mes) throws SQLException, NumberFormatException
	{
		java.util.List<String> arg = mods.util.getParams(mes);
		final String nick = arg.get(1);
		final Connection conn = mods.odb.getConnection();
		try
		{
			final int score = score(conn, nick, arg.size() > 2 ? Long.parseLong(arg.get(2)) : 0);
			return nick + " is having a " + (Math.abs(score) > 5 ? "very " : "") + 
				(score == 0 ? "boring" : ( score < 0 ? "bad" : "good")) + " day (" + score + ").";
		}
		finally
		{
			mods.odb.freeConnection(conn);
		}
	}
}
