import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;

public class Shutup
{
	// XXX Make this lot nicer!
	public String[] helpCommandAdd = {
		"Make the bot shut up in the current channel.",
	};
	public void commandAdd( Message mes, Modules mods, IRCInterface irc )
	{
		irc.sendContextReply(mes, "Okay, shutting up in " + mes.getContext() + ".");
		mods.pc.addProtected(mes.getContext());
	}

	public String[] helpCommandRemove = {
		"Make the bot wake up in the current channel.",
	};
	public void commandRemove( Message mes, Modules mods, IRCInterface irc )
	{
		mods.pc.removeProtected(mes.getContext());
		irc.sendContextReply(mes, "Yay, I'm free to speak in " + mes.getContext() + " again!");
	}

	public String[] helpCommandCheck = {
		"Check if the bot is shut up in the current channel.",
	};
	public void commandCheck( Message mes, Modules mods, IRCInterface irc )
	{
		if (mods.pc.isProtected(mes.getContext()))
			irc.sendContextReply(mes, "Yes, shut-up.");
		else
			irc.sendContextReply(mes, "Nope, not shut-up.");
	}
}
