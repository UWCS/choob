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

	private int score(Connection conn, String what, long period) throws SQLException
	{
		final long midnight;
		if (0 == period)
		{
			final Calendar c = new GregorianCalendar();
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			midnight = c.getTimeInMillis();
		}
		else
		{
			midnight = new Date().getTime() - period;
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
			s.setLong(2, midnight);
			return exec(s, "%++%") - exec(s, "%--%");
		}
		finally
		{
			s.close();
		}
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
			final int score = score(conn, nick, arg.size() > 2 ? Long.parseLong(arg.get(2)) : 0);
			return nick + " is having a " + (Math.abs(score) > 5 ? "very " : "")
					+ (score == 0 ? "boring" : (score < 0 ? "bad" : "good")) + " day (" + score + ").";
		}
		finally
		{
			mods.odb.freeConnection(conn);
		}
	}
}
