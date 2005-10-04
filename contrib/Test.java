import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;

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

	public void commandPiratey( Message con, Modules mods, IRCInterface irc )
	{
		//irc.sendContextAction(con, "reaches into her pants, and grabs a Yarr.");
		irc.sendContextReply(con, "(:;test.piratey:)");
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
	public String filterFauxRegex = ".*?([a-zA-Z0-9]{2,}) sucks.*";

	public void filterFaux( Message con, Modules modules, IRCInterface irc )
	{
		Pattern pa;
		Matcher ma;
		pa=Pattern.compile(filterFauxRegex);
		ma=pa.matcher(con.getMessage());
		ma.find();
		if (ma.group(1).equals(con.getNick()))
			irc.sendContextReply( con, "You sure do!");
		else
			irc.sendContextMessage( con, "No, I disagree, " + con.getNick() + " sucks.");
	}

	public String filterBouncyRegex = "^bouncy bouncy";

	public void filterBouncy( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextReply( con, "Ooh, yes please.");
	}

	public void onJoin( ChannelJoin ev, Modules mod, IRCInterface irc )
	{
		if (!ev.getLogin().equals("Choob"))
			irc.sendContextMessage( ev, "Hello, " + ev.getNick() + "!");
	}

	public void onPart( ChannelPart ev, Modules mod, IRCInterface irc )
	{
		irc.sendContextMessage( ev, "Bye, " + ev.getNick() + "!");
	}
}
