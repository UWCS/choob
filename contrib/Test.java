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

	// Define the regex for the KarmaPlus filter.
	public String filterFauxRegex = "^Faux sucks";

	public void filterFaux( Context con, Modules modules, IRCInterface irc )
	{
		irc.sendContextMessage( con, "I would almost certainly concurr.");
	}

	public String filterBouncyRegex = "^bouncy bouncy";

	public void filterBouncy( Context con, Modules modules, IRCInterface irc )
	{
		irc.sendContextMessage( con, "Ooh, yes please.");
	}
}
