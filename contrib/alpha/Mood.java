/** @author Faux */

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import uk.co.uwcs.choob.modules.Modules;

public class Mood
{

	final SimpleDateFormat sdfa = new SimpleDateFormat("Kaa ");
	final SimpleDateFormat sdfb = new SimpleDateFormat("EEEE");

	private final Modules mods;

	public Mood(final Modules mods)
	{
		this.mods = mods;
	}

	private String score(Connection conn, String what, long period) throws SQLException
	{
		final long since;
		final long exsince;
		final int mul = 7;

		if (0 == period)
		{
			final Calendar c = new GregorianCalendar();
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			since = c.getTimeInMillis();
			c.add(Calendar.DAY_OF_YEAR, -mul);
			exsince = c.getTimeInMillis();
		}
		else
		{
			since = new Date().getTime() - period;
			exsince = period  * mul;
		}

		final String cond;
		if (what.startsWith("#"))
			cond = "Channel like ?";
		else
			cond = "Nick like ?";

		final PreparedStatement s = conn.prepareStatement("select count(*) from History where " + cond
				+ " and Time > ? and Text like ?");
		try
		{
			s.setString(1, what);
			final int act = run(s, since);
			final int avg = Math.round(run(s, exsince) / (float)mul);
			final int diff = act - avg;

			String ret = very(diff) + (diff == 0 ? "boring" : (diff < 0 ? "bad" : "good")) + " day";
			if (Math.abs(avg) > 5)
				ret += ", and are generally " + very(avg) + (avg < 0 ? "depressed" : "happy");
			ret += ".  (" + diff + ", today=" + act + ", avg=" + avg + ")";
			return ret;
		}
		finally
		{
			s.close();
		}
	}

	private String very(final int diff)
	{
		return (Math.abs(diff) > 5 ? "very " : "");
	}

	private int run(final PreparedStatement s, final long period) throws SQLException
	{
		s.setLong(2, period);
		return exec(s, "%++%") - exec(s, "%--%");
	}

	private int exec(final PreparedStatement s, final String textLike) throws SQLException
	{
		s.setString(3, textLike);
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
		return up;
	}

	public String commandDay(final String mes) throws SQLException, NumberFormatException
	{
		java.util.List<String> arg = mods.util.getParams(mes);
		final String nick = arg.get(1);
		final Connection conn = mods.odb.getConnection();
		try
		{
			return nick + " is having a " + score(conn, nick, arg.size() > 2 ? Long.parseLong(arg.get(2)) : 0);
		}
		finally
		{
			mods.odb.freeConnection(conn);
		}
	}
}
