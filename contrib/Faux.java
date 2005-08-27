import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;

class Faux
{
	public void commandWhoAmI( Message con, Modules mods, IRCInterface irc )
	{
		irc.sendContextMessage(con, mods.nick.getPrimaryNick(con.getNick()));
	}

	public void commandSay( Message con, Modules mods, IRCInterface irc )
	{
		int a=con.getMessage().indexOf(" ");
		if ( a!=-1 )
			irc.sendContextMessage(con, con.getMessage().substring(a+1));
		else
			irc.sendContextMessage(con, "Dotwhore!");
	}

	public void commandReply( Message con, Modules mods, IRCInterface irc )
		{
			int a=con.getMessage().indexOf(" ");
			if ( a!=-1 )
				irc.sendContextMessage(con, con.getNick() + ": " + con.getMessage().substring(a+1));
			else
				irc.sendContextMessage(con, con.getNick() + "!");
	}

	public void commandGame( Message con, Modules mods, IRCInterface irc )
	{
		irc.sendContextMessage(con, "You just lost the game!");
	}


}
