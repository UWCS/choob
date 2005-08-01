import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;

class Faux
{
	public void commandWhoAmI( Context con, Modules mods, IRCInterface irc )
	{
		irc.sendContextMessage(con, mods.nick.getPrimaryNick(con.getNick()));
	}
}
