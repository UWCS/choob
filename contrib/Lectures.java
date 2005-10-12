import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.*;
import java.sql.*;
import java.text.*;


// NB: This will not work unless you've done at least some of the stuff in http://faux.uwcs.co.uk/Lectures.choob.rar.


public class Lectures
{
	SimpleDateFormat df=new SimpleDateFormat("h:mm a 'on' EEEE");

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
		PreparedStatement s=getStatement("SELECT `modules`.`name` AS modulename, `modules`.`code` AS modulecode, `rooms`.`name` AS roomname,`times`.`start` FROM `times` INNER JOIN `modules` ON (`modules`.`module`=`times`.`module`) INNER JOIN `rooms` ON (`rooms`.`room` = `times`.`room`) WHERE `start` > NOW() AND `modules`.`module` IN (SELECT `module` FROM `usermods` INNER JOIN `users` ON ( `users`.`userid` = `usermods`.`userid`) WHERE `users`.`nick` = ?) ORDER BY `start` LIMIT 1;");
		s.setString(1, mes.getNick());
		ResultSet rs = s.executeQuery();
		if (rs.next())
			irc.sendContextReply(mes, "You have " + rs.getString("modulename") + " (" + rs.getString("modulecode") + ") in " + rs.getString("roomname") + " at " + df.format(rs.getTimestamp("start")) + ".");
		else
			irc.sendContextReply(mes, "Durno.");
		s.close();
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

		s.close();
		s=getStatement("INSERT INTO `users` (`nick`) VALUES ( ? )");
		s.setString(1, nick);

		s.executeUpdate();
		return getUserId(nick); // BWBAHAHAHAHAHAHAHAHAHAHAHAHHAWBEWBAHHawha XXX HAX XXX HAX BWAHHA.
	}

	public synchronized void commandAddModule(Message mes, Modules mods, IRCInterface irc ) throws SQLException
	{
		String mo=mods.util.getParamString(mes);
		int uid=getUserId(mes.getNick());

		PreparedStatement s=getStatement("SELECT `module` from `modules` where `code` = ?");
		s.setString(1, mo);
		ResultSet rs = s.executeQuery();
		String sug;
		if (!rs.first()) // Begin copy.
		{
			PreparedStatement t=getStatement("SELECT `code` from `modules` where `code` LIKE ? LIMIT 6;");
			t.setString(1, "%" + mo + "%"); // XXX Wtf, this really shouldn't work.. jdbc, you suck.
			ResultSet rt = t.executeQuery();
			System.out.println(t);
			if (!rt.first())
			{
				irc.sendContextReply(mes, "Module code not recognised.");
				return;
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
			}
			else
			{
				irc.sendContextReply(mes, "Module code not recognised. Did you mean: " + sug + "..?");
				t.close();
				return;
			}
		}
		else
			sug=mo;

		int modcode=rs.getInt("module");

		s.close();

		s=getStatement("INSERT INTO `usermods` (`userid`, `module`) VALUES (?, ?)");
		s.setInt(1, uid);
		s.setInt(2, modcode);
		s.executeUpdate();
		s.close();

		irc.sendContextReply(mes, "Okay, added " + sug + "!");
	}

	public synchronized void commandRemoveModule(Message mes, Modules mods, IRCInterface irc ) throws SQLException
	{
		String mo=mods.util.getParamString(mes);
		int uid=getUserId(mes.getNick());

		PreparedStatement s=getStatement("SELECT `module` from `modules` where `code` = ?");
		s.setString(1, mo);
		ResultSet rs = s.executeQuery();

		if (!rs.first()) // Begin paste.. etc.
		{
			PreparedStatement t=getStatement("SELECT `code` from `modules` where `code` LIKE ? LIMIT 6;");
			t.setString(1, "%" + mo + "%");
			ResultSet rt = t.executeQuery();
			System.out.println(t);
			if (!rt.first())
			{
				irc.sendContextReply(mes, "Module code not recognised.");
			}
			else
			{
				String sug="";
				do
				{
					sug+=rt.getString("code") + ", ";
				} while (rt.next());
				sug=sug.substring(0, sug.length()-2);

				irc.sendContextReply(mes, "Module code not recognised. Did you mean: " + sug + "..?");
			}

			t.close();

			return;
		}

		int modcode=rs.getInt("module");

		s.close();

		s=getStatement("DELETE FROM `usermods` WHERE `userid` = ? AND `module` = ? LIMIT 1");

		s.setInt(1, uid);
		s.setInt(2, modcode);
		if (s.executeUpdate()!=0)
			irc.sendContextReply(mes, "Gone!");
		else
			irc.sendContextReply(mes, "Um?");
		s.close();

	}

}
