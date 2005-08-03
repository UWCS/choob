import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;

class Faux
{
	public void commandWhoAmI( Context con, Modules mods, IRCInterface irc )
	{
		irc.sendContextMessage(con, mods.nick.getPrimaryNick(con.getNick()));
	}

	public void commandSay( Context con, Modules mods, IRCInterface irc )
	{
		int a=con.getText().indexOf(" ");
		if ( a!=-1 )
			irc.sendContextMessage(con, con.getText().substring(a+1));
		else
			irc.sendContextMessage(con, "Dotwhore!");
	}

	public void commandReply( Context con, Modules mods, IRCInterface irc )
		{
			int a=con.getText().indexOf(" ");
			if ( a!=-1 )
				irc.sendContextMessage(con, con.getNick() + ": " + con.getText().substring(a+1));
			else
				irc.sendContextMessage(con, con.getNick() + "!");
	}

	public void commandGame( Context con, Modules mods, IRCInterface irc )
	{
		irc.sendContextMessage(con, "You just lost the game!");
	}


}
