import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.*;
import java.sql.*;
import java.text.*;
import java.io.*;
import java.text.DateFormatSymbols;

// NB: This will not work unless you've done at least some of the stuff in http://faux.uwcs.co.uk/Lectures.choob.rar.

public class Lectures
{
	SimpleDateFormat df=new SimpleDateFormat("h:mm a 'on' EEEE");

	final String days[] = (new DateFormatSymbols()).getWeekdays();

	public Lectures() throws ClassNotFoundException
	{
		Class.forName("com.mysql.jdbc.Driver");
	}

	private PreparedStatement getStatement(String query) throws SQLException
	{
		Connection con = DriverManager.getConnection("jdbc:mysql://localhost/timetable", "choob", "choob");
		return con.prepareStatement(query);
	}

	public void commandWhen( Message mes, Modules mods, IRCInterface irc ) throws SQLException
	{
		String s=mods.util.getParamString(mes);
		if (s.toLowerCase().indexOf("next lecture")!=-1)
		{
			commandNextLecture(mes, mods, irc);
			return;
		}
		else if (s.toLowerCase().indexOf("is ")==0)
		{
			if (s.indexOf("?")!=-1)
			{
				s=s.substring(3, s.indexOf("?"));
				irc.sendContextReply(mes, s);
				return;
			}
		}

		irc.sendContextReply(mes, "Durno.");
	}

	public void commandNextLecture( Message mes, Modules mods, IRCInterface irc ) throws SQLException
	{
		String param=mods.util.getParamString(mes).trim();
		boolean modcode=false;
		int c=1;
		try
		{
			if (!param.equals(""))
				c=Integer.parseInt(param);

			if (c<1 || c>6)
			{
				irc.sendContextReply(mes, "Uh, no.");
				return;
			}

		}
		catch (NumberFormatException e)
		{
			modcode=true;
		}

		PreparedStatement s;
		if (!modcode)
		{
			s=getStatement("SELECT `modules`.`name` AS modulename, `modules`.`code` AS modulecode, `rooms`.`name` AS roomname,`times`.`start` FROM `times` INNER JOIN `modules` ON (`modules`.`module`=`times`.`module`) INNER JOIN `rooms` ON (`rooms`.`room` = `times`.`room`) WHERE (`start` > NOW()) AND (`start` < (NOW() + INTERVAL 7 DAY)) AND `modules`.`module` IN (SELECT `module` FROM `usermods` INNER JOIN `users` ON ( `users`.`userid` = `usermods`.`userid`) WHERE `users`.`nick` = ?) ORDER BY `start` LIMIT ?;");

			s.setString(1, mes.getNick());
			s.setInt(2, c);

			ResultSet rs = s.executeQuery();
			String ret="";

			if (!rs.first())
			{
				irc.sendContextReply(mes, "Durno.");
				return;
			}

			rs.beforeFirst();

			while (rs.next())
				ret+=rs.getString("modulename") + " (" + rs.getString("modulecode") + ") in " + rs.getString("roomname") + " at " + df.format(rs.getTimestamp("start")) + ", ";

			ret=ret.substring(0, ret.length()-2);

			irc.sendContextReply(mes, "You have " + ret + ".");

			s.close();
		}
		else
		{
			int modid=codeSuggestions(param, irc, mes);
			if (modid==-1)
				return;

			s=getStatement("SELECT `modules`.`name` AS modulename, `modules`.`code` AS modulecode, `rooms`.`name` AS roomname,`times`.`start` FROM `times` INNER JOIN `modules` ON (`modules`.`module`=`times`.`module`) INNER JOIN `rooms` ON (`rooms`.`room` = `times`.`room`) WHERE (`start` > NOW()) AND `modules`.`module` = ? ORDER BY `start` LIMIT 1;");
			s.setInt(1, modid);

			ResultSet rs = s.executeQuery();

			rs.first(); // It has to exist.

			irc.sendContextReply(mes, "Thers is a " + rs.getString("modulename") + " (" + rs.getString("modulecode") + ") in " + rs.getString("roomname") + " at " + df.format(rs.getTimestamp("start")) + ".");

		}

	}

	public void commandListModules(Message mes, Modules mods, IRCInterface irc ) throws SQLException
	{
		PreparedStatement s=getStatement("SELECT `modules`.`code` from `modules` INNER JOIN `usermods` on (`usermods`.`module`=`modules`.`module`) INNER JOIN `users`on (`users`.`userid`=`usermods`.`userid`) WHERE `nick`=?");
		s.setString(1, mes.getNick());
		ResultSet rs = s.executeQuery();
		if (!rs.first())
		{
			irc.sendContextMessage(mes, "You appear not to have any modules listed.");
			return;
		}

		String res="";

		do
		{
			res+=rs.getString("code") + ", ";
		} while (rs.next());

		res=res.substring(0, res.length()-2) + ".";

		irc.sendContextReply(mes, "You are registered for " + res);
	}

	private synchronized int getUserId(String nick) throws SQLException
	{
		PreparedStatement s=getStatement("SELECT `userid` from `users` where `nick` = ? LIMIT 1");
		s.setString(1, nick);

		ResultSet rs = s.executeQuery();

		if (rs.first())
			return rs.getInt("userid");

		PreparedStatement r=getStatement("INSERT INTO `users` (`nick`) VALUES ( ? )");
		r.setString(1, nick);
		r.executeUpdate();
		r.close();

		rs = s.executeQuery();

		if (rs.first())
			return rs.getInt("userid");
		else
			throw new SQLException("Unexpected result from SQL...");
	}

	private synchronized int codeSuggestions (String code, IRCInterface irc, Message mes) throws SQLException
	{
		PreparedStatement s=getStatement("SELECT `module` from `modules` where `code` = ?");
		s.setString(1, code);
		ResultSet rs = s.executeQuery();
		String sug;
		if (rs.first())
			return rs.getInt("module");

		PreparedStatement t=getStatement("SELECT `code` from `modules` where `code` LIKE ? LIMIT 6;");
		t.setString(1, "%" + code + "%"); // JDBC-- because this really shouldn't work.
		ResultSet rt = t.executeQuery();

		System.out.println(t);

		if (!rt.first())
		{
			irc.sendContextReply(mes, "Module code not recognised.");
			return -1;
		}

		sug="";
		do
		{
			sug+=rt.getString("code") + ", ";
		} while (rt.next());


		sug=sug.substring(0, sug.length()-2);

		if (sug.indexOf(",")==-1)
		{
			s.close();
			s=getStatement("SELECT `module` from `modules` where `code` = ?");
			s.setString(1, sug);
			rs=s.executeQuery();
			rs.first();
			return rs.getInt("module");
		}
		else
		{
			irc.sendContextReply(mes, "Module code not recognised. Did you mean: " + sug + "..?");
			t.close();
			return -1;
		}
	}


	public synchronized void commandAddModule(Message mes, Modules mods, IRCInterface irc ) throws SQLException
	{
		int uid=getUserId(mes.getNick());

		int modcode=codeSuggestions(mods.util.getParamString(mes), irc, mes);

		if (modcode==-1)
			return;

		PreparedStatement s=getStatement("INSERT INTO `usermods` (`userid`, `module`) VALUES (?, ?)");
		s.setInt(1, uid);
		s.setInt(2, modcode);
		s.executeUpdate();
		s.close();

		irc.sendContextReply(mes, "Okay, added!");
	}

	public synchronized void commandRemoveModule(Message mes, Modules mods, IRCInterface irc ) throws SQLException
	{
		int uid=getUserId(mes.getNick());

		int modcode=codeSuggestions(mods.util.getParamString(mes), irc, mes);

		PreparedStatement s=getStatement("DELETE FROM `usermods` WHERE `userid` = ? AND `module` = ? LIMIT 1");

		s.setInt(1, uid);
		s.setInt(2, modcode);
		if (s.executeUpdate()!=0)
			irc.sendContextReply(mes, "Gone!");
		else
			irc.sendContextReply(mes, "Um?");
		s.close();
	}

	public void webWeekly(Modules mods, IRCInterface irc, PrintWriter out, String params, String[] user) throws SQLException
	{
		out.println("HTTP/1.0 200 OK");
		out.println("Content-Type: text/html");
		out.println();

		String ps[]=params.split("&");

		String ss="";

		if (ps.length == 2)
			ss=ps[1];

		params=ps[0];

		out.println("<html><head><link type=\"text/css\" rel=\"stylesheet\" href=\"" + ss + "\" /><title>Timetable for " + params + ".</title></head><body><h1>Timetable for " + params + ":</h1>");

		PreparedStatement s=getStatement("SELECT `modules`.`name` AS modulename, `modules`.`code` AS modulecode, `rooms`.`name` AS roomname,`times`.`start` FROM `times` INNER JOIN `modules` ON (`modules`.`module`=`times`.`module`) INNER JOIN `rooms` ON (`rooms`.`room` = `times`.`room`) WHERE (`start` > NOW() AND `start` < (NOW() + INTERVAL 7 DAY)) AND `modules`.`module` IN (SELECT `module` FROM `usermods` INNER JOIN `users` ON ( `users`.`userid` = `usermods`.`userid`) WHERE `users`.`nick` = ?) ORDER BY `start`;");

		s.setString(1, params);

		ResultSet rs = s.executeQuery();
		String ret="";

		if (!rs.first())
		{
			out.println("No data!");
			return;
		}

		rs.beforeFirst();

		out.println("<table>\n<tr class=\"head\"><th /><th>9</th><th>10</th><th>11</th><th>12</th><th>13</th><th>14</th><th>15</th><th>16</th><th>17</th><th>18</th></tr>\n");

		int ch=9;
		int cd=-1;
		while (rs.next())
		{
			GregorianCalendar cal = new GregorianCalendar();
			cal.setTimeInMillis(rs.getTimestamp("start").getTime());
			if (cal.get(cal.DAY_OF_WEEK)!=cd)
			{
				if (ch!=9)
				{
					System.out.println("ch!=9");
					while (ch++<=18)
						out.print("<td>&nbsp;</td>");
					out.println("</tr>");
				}

				out.print("<tr><td class=\"day\">" + days[cal.get(cal.DAY_OF_WEEK)] + "</td>");
				cd=cal.get(cal.DAY_OF_WEEK);
				ch=9;
			}

			while (ch<cal.get(cal.HOUR_OF_DAY))
			{
				System.out.println("Finding hour.");
				ch++;
				out.print("<td>&nbsp;</td>");
			}

			out.print("<td><span class=\"modulename\">" + rs.getString("modulename") + "</span> (<span class=\"modulecode\">" + rs.getString("modulecode") + "</span>)<span class=\"room\"> in " + rs.getString("roomname") + "</span></td>");
			ch++;
		}

		if (ch!=9)
		{
			System.out.println("ch!=9");
			while (ch++<=18)
				out.print("<td>&nbsp;</td>");
			out.println("</tr>");
		}


		s.close();
		out.println("</table></body></html>");

	}

}
