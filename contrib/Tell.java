import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;


// Note: This send/watch couple will break if someone changes their primary nick between the send and the receive, assuming they change their base nick.. it could be done otherwise, but Faux can't think of a way that doesn't involve mass database rapeage on every line sent by irc.
// This entire plugin could do with some caching.

private class TellObject
{
	public int id;
	public String date;
	public String from;
	public String message;
	public String target;
}

class Tell
{
	public void commandSend( Message con, Modules mods, IRCInterface irc )
	{
		int targets = 0;
		TellObject tO = new TellObject();
		// Note: This is intentionally not translated to a primary nick.
		tO.from=con.getNick();

		Pattern pa = Pattern.compile("^[^ ]+? ([a-zA-Z0-9_,|-]+) (.*)$");
		Matcher ma = pa.matcher(con.getMessage());

		if (!ma.matches())
		{
			irc.sendContextMessage(con, "Syntax error.");
			return;
		}

		tO.message=ma.group(2); // 'Message'.

		StringTokenizer tokens = new StringTokenizer(ma.group(1), ",");

		tO.date=(new Date()).toString();

		while( tokens.hasMoreTokens() )
		{
			// Note: This call to getBestPrimaryNick is not optimal, discussed above.
			tO.target=mods.nick.getBestPrimaryNick(tokens.nextToken());
			System.out.println("Going to save!");
			mods.odb.save(tO);
			targets++;
		}

		irc.sendContextMessage(con, "Okay, will tell upon next speaking. (Sent to " + targets + " " + (targets == 1 ? "person" : "people") + ").");
	}

	private void spew(String nick, Modules mods, IRCInterface irc)
	{
		List results = mods.odb.retrieve (TellObject.class, "target = '" + nick + "'");

		if (results.size()!=0)
			for (int i=0; i < results.size(); i++ )
			{
				irc.sendMessage(nick, "At " + results.get(i).date + ", " + results.get(i).from + " told me to tell you: " + results.get(i).message);
				mods.odb.delete(results.get(i));
			}


	}

	public void onMessage( ChannelMessage ev, Modules mod, IRCInterface irc ) { spew(ev.getNick(), mod, irc); }
	public void onPrivateMessage( PrivateMessage ev, Modules mod, IRCInterface irc ) { spew(ev.getNick(), mod, irc); }
	public void onJoin( ChannelJoin ev, Modules mod, IRCInterface irc ) { spew(ev.getNick(), mod, irc); }


	/*
		onJoin()
		{
			Do as filterWatch does();
			Mark the user as having received all their tells?();
		}
	*/
}

