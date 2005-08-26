import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;

class Test
{
	public void commandPirate( Message con, Modules mods, IRCInterface irc )
	{
		irc.sendContextMessage(con, "Yarr!");
	}

	public void commandExit( Message con, Modules mods, IRCInterface irc )
	{
		System.exit(1);
	}

	// Define the regex for the KarmaPlus filter.
	public String filterFauxRegex = "^Faux sucks";

	public void filterFaux( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextMessage( con, "I would almost certainly concurr.");
	}

	public String filterBouncyRegex = "^bouncy bouncy";

	public void filterBouncy( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextMessage( con, "Ooh, yes please.");
	}

	public void onJoin( ChannelEvent ev, Modules mod, IRCInterface irc )
	{
		irc.sendMessage(ev.getChannel(), "Hello, " + ev.getSender() + "!");
	}

	public void onPart( ChannelEvent ev, Modules mod, IRCInterface irc )
	{
		irc.sendMessage(ev.getChannel(), "Bye, " + ev.getSender() + "!");
	}

}
