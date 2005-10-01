import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.*;

public class Test
{
	public void commandSecurity( Message con, Modules mods, IRCInterface irc )
	{
		List<String> params = mods.util.getParams( con );

		String priv = params.get(1);
		if ( mods.security.hasPerm( new ChoobPermission(priv), con.getNick() ) )
			irc.sendContextReply(con, "You do indeed have " + priv + "!" );
		else
			irc.sendContextReply(con, "You don't have " + priv + "!" );
	}

	public void commandPirate( Message con, Modules mods, IRCInterface irc )
	{
		irc.sendContextReply(con, "Yarr!");
	}

	public void commandInMy( Message con, Modules mods, IRCInterface irc )
	{
		irc.sendContextMessage(con, "..Pants!");
	}

	public void commandExit( Message con, Modules mods, IRCInterface irc )
	{
		System.exit(1);
	}

	// Define the regex for the KarmaPlus filter.
	public String filterFauxRegex = "^Faux sucks";

	public void filterFaux( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextReply( con, "I would almost certainly concur.");
	}

	public String filterBouncyRegex = "^bouncy bouncy";

	public void filterBouncy( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextReply( con, "Ooh, yes please.");
	}

	public void onJoin( ChannelJoin ev, Modules mod, IRCInterface irc )
	{
		System.out.println(ev.getLogin());
		if (!ev.getLogin().equals("Choob"))
			irc.sendMessage(ev.getChannel(), "Hello, " + ev.getNick() + "!");
	}

	public void onPart( ChannelPart ev, Modules mod, IRCInterface irc )
	{
		irc.sendMessage(ev.getChannel(), "Bye, " + ev.getNick() + "!");
	}
}
