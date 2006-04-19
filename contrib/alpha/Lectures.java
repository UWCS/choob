/** @author Faux */

import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.sql.*;
import java.text.*;
import java.io.*;
import java.text.DateFormatSymbols;

public class Lectures
{
	public String[] info()
	{
		return new String[] {
			"University of Warwick lectures querying plugin.",
			"Faux",
			"ALPHA ALPHA",
			""
		};
	}

	private Modules mods;
	private IRCInterface irc;
	public Lectures(IRCInterface irc, Modules mods)
	{
		this.mods = mods;
		this.irc = irc;
	}


	public String[] helpTopics = { "Using" };

	public String[] helpUsing = {
		  "You may want to start by using 'Lectures.AddLikeModules' with your course and year, for instance, second year CS students may try 'Lectures.AddLikeModules cs2%'.",
		  "This will give you a starting list of modules. To see it, 'Lectures.ListModules'.",
		  "'Lectures.AddModule' and 'Lectures.RemoveModule' allow you to fine-tune this list.",
		  "'Lectures.NextLecture 3' (for instance) will tell your next three lectures are."
	};


	final SimpleDateFormat df=new SimpleDateFormat("h:mm a 'on' EEEE");

	final String days[] = (new DateFormatSymbols()).getWeekdays();

	private PreparedStatement getStatement(String query) throws SQLException
	{
		Connection con = mods.odb.getConnection();
		return con.prepareStatement(query);
	}

	public String[] helpCommandNextLecture = {
		"Tells you when the next lecture for either you or a specified module is.",
		"<Code or Number to display>",
		"<Code or Number to display> is either a module code or an integer between 1 and 6."
	};

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
				s.close();
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
			int modid=codeSuggestions(param, mes);
			if (modid==-1)
				return;

			s=getStatement("SELECT `modules`.`name` AS modulename, `modules`.`code` AS modulecode, `rooms`.`name` AS roomname,`times`.`start` FROM `times` INNER JOIN `modules` ON (`modules`.`module`=`times`.`module`) INNER JOIN `rooms` ON (`rooms`.`room` = `times`.`room`) WHERE (`start` > NOW()) AND `modules`.`module` = ? ORDER BY `start` LIMIT 1;");
			s.setInt(1, modid);

			ResultSet rs = s.executeQuery();

			rs.first(); // It has to exist.

			irc.sendContextReply(mes, "Thers is a " + rs.getString("modulename") + " (" + rs.getString("modulecode") + ") in " + rs.getString("roomname") + " at " + df.format(rs.getTimestamp("start")) + ".");

			s.close();

		}

	}

	public String[] helpCommandListModules = {
		"Lists the modules you are registered for."
	};
	public void commandListModules(Message mes, Modules mods, IRCInterface irc ) throws SQLException
	{
		PreparedStatement s=getStatement("SELECT `modules`.`code` from `modules` INNER JOIN `usermods` on (`usermods`.`module`=`modules`.`module`) INNER JOIN `users`on (`users`.`userid`=`usermods`.`userid`) WHERE `nick`=?");
		s.setString(1, mes.getNick());
		ResultSet rs = s.executeQuery();
		if (!rs.first())
		{
			irc.sendContextMessage(mes, "You appear not to have any modules listed.");
			s.close();
			return;
		}

		String res="";

		do
		{
			res+=rs.getString("code") + ", ";
		} while (rs.next());

		res=res.substring(0, res.length()-2) + ".";

		irc.sendContextReply(mes, "You are registered for " + res);

		s.close();
	}

	private synchronized int getUserId(String nick) throws SQLException
	{
		PreparedStatement s=getStatement("SELECT `userid` from `users` where `nick` = ? LIMIT 1");
		s.setString(1, nick);

		ResultSet rs = s.executeQuery();

		if (rs.first())
		{
			final int ret=rs.getInt("userid");
			s.close();
			return ret;
		}

		PreparedStatement r=getStatement("INSERT INTO `users` (`nick`) VALUES ( ? )");
		r.setString(1, nick);
		r.executeUpdate();
		r.close();

		rs = s.executeQuery();

		if (rs.first())
		{
			final int ret=rs.getInt("userid");
			s.close ();
			return ret;
		}
		else
			throw new SQLException("Unexpected result from SQL...");
	}

	private synchronized int codeSuggestions (String code, Message mes) throws SQLException
	{
		String sug;
		{
			PreparedStatement s=getStatement("SELECT `module` from `modules` where `code` = ?");
			s.setString(1, code);
			ResultSet rs = s.executeQuery();

			if (rs.first())
			{
				final int ret=rs.getInt("module");
				s.close();
				return ret;
			}

			s.close();
		}

		{
			PreparedStatement s=getStatement("SELECT `code` from `modules` where `code` LIKE ? LIMIT 6;");
			s.setString(1, "%" + code + "%"); // JDBC-- because this really shouldn't work.
			ResultSet rs = s.executeQuery();

			if (!rs.first())
			{
				irc.sendContextReply(mes, "Module code not recognised.");
				s.close();
				return -1;
			}

			sug="";
			do
			{
				sug+=rs.getString("code") + ", ";
			} while (rs.next());

			if (sug.length() != 0)
				sug=sug.substring(0, sug.length()-2);

			s.close();
		}

		if (sug.indexOf(",")==-1)
		{
			PreparedStatement s=getStatement("SELECT `module` from `modules` where `code` = ?");
			s.setString(1, sug);

			ResultSet rs=s.executeQuery();

			rs.first();
			final int ret = rs.getInt("module");
			s.close();
			return ret;
		}
		else
		{
			irc.sendContextReply(mes, "Module code not recognised. Did you mean: " + sug + "..?");

			return -1;
		}
	}

	public String[] helpCommandAddLikeModules = {
		"'Registers' you (rather indescriminantly) for a specified set of modules. % and _ are the wildcards.",
		"<Code String>",
		"<Code String> is the module(s) code to register for."
	};

	public synchronized void commandAddLikeModules(Message mes ) throws SQLException
	{
		mods.security.checkNS(mes);

		int uid=getUserId(mes.getNick());
		final String code=mods.util.getParamString(mes);

		PreparedStatement s=getStatement("INSERT INTO `usermods` (`userid`, `module`) VALUES (?, ?)");
		s.setInt(1, uid);


		PreparedStatement t=getStatement("SELECT `module` from `modules` where `code` LIKE ?;");
		t.setString(1, code);
		ResultSet rt = t.executeQuery();

		if (!rt.first())
		{
			irc.sendContextReply(mes, "No modules matched '" + code + "'.");
			s.close();
			t.close();
			return;
		}

		int i=0;

		do
		{
			s.setInt(2,rt.getInt("module"));
			try
			{
				s.executeUpdate();
				i++;
			}
			catch (SQLException e)
			{}
		} while (rt.next());


		s.close();
		t.close();

		irc.sendContextReply(mes, "Okay, added " + i + " module" + (i!=1 ? "s" : "") + "!");
	}


	public String[] helpCommandAddModule = {
		"'Registers' you for a module.",
		"<Code>",
		"<Code> is the module code to register for."
	};

	public synchronized void commandAddModule(Message mes ) throws SQLException
	{
		mods.security.checkNS(mes);

		int uid=getUserId(mes.getNick());

		int modcode=codeSuggestions(mods.util.getParamString(mes), mes);

		if (modcode==-1)
			return;

		PreparedStatement s=getStatement("INSERT INTO `usermods` (`userid`, `module`) VALUES (?, ?)");
		s.setInt(1, uid);
		s.setInt(2, modcode);
		s.executeUpdate();
		s.close();

		irc.sendContextReply(mes, "Okay, added!");
	}

	public String[] helpCommandRemoveModule = {
		"'Unregisters' you for a module.",
		"<Code>",
		"<Code> is the module code to unregister for."
	};

	public synchronized void commandRemoveModule(Message mes ) throws SQLException
	{
		mods.security.checkNS(mes);

		int uid=getUserId(mes.getNick());

		int modcode=codeSuggestions(mods.util.getParamString(mes),mes);

		if (modcode==-1)
			return;

		PreparedStatement s = getStatement("DELETE FROM `usermods` WHERE `userid` = ? AND `module` = ? LIMIT 1");

		s.setInt(1, uid);
		s.setInt(2, modcode);
		if (s.executeUpdate()!=0)
			irc.sendContextReply(mes, "Gone!");
		else
			irc.sendContextReply(mes, "Um?");

		s.close();
	}

// Not checked or up-to-date:
/*
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
*/
}
