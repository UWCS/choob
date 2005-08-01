import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;

class Test
{
	public void commandPirate( Context con, Modules mods, IRCInterface irc )
	{
		irc.sendContextMessage(con, "Yarr!");
	}

	public void commandExit( Context con, Modules mods, IRCInterface irc )
	{
		System.exit(1);
	}
}
