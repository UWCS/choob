import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;

class Shutup
{
	public void commandPirate( Message con, Modules mods, IRCInterface irc )
	{
		irc.sendContextMessage(con, "Yarr!");
	}

	public void commandAdd( Message con, Modules mods, IRCInterface irc )
	{
		irc.sendContextMessage(con, "Okay, now silent in " + con.getChannel() + ".");
		mods.pc.addProtected(con.getChannel());
	}

	public void commandRemove( Message con, Modules mods, IRCInterface irc )
	{
		mods.pc.removeProtected(con.getChannel());
		irc.sendContextMessage(con, "Yay, I'm free to speak in " + con.getChannel() + " again!");
	}


	public Boolean commandCheck( Message con, Modules mods, IRCInterface irc )
	{
		if (mods.pc.isProtected(con.getChannel()))
			irc.sendContextMessage(con, "Yes, shut-up.");
		else
			irc.sendContextMessage(con, "Nope, not shut-up.");
	}

}
