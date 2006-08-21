import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;

/**
 * Response plugin.
 *
 * @author Tim Retout <tim@retout.co.uk>
 *
 */

public class Response
{
	public String[] info()
	{
		return new String[] {
			"Response plugin.",
			"Tim Retout",
			"tim@retout.co.uk",
			"$Rev$$Date$"
		};
	}

	Modules mods;
	IRCInterface irc;

	public Response(Modules mods, IRCInterface irc)
	{
		this.irc = irc;
		this.mods = mods;
	}

	public static String filterGoodBotRegex = "good bot";

	public void filterGoodBot( Message mes, Modules mods, IRCInterface irc )
	{
		irc.sendContextMessage(mes, "Thanks, " + mes.getNick() + " :-)");
	}

	public static String filterBadBotRegex = "bad bot";

	public void filterBadBot( Message mes, Modules mods, IRCInterface irc )
	{
		irc.sendContextReply(mes, "Bastard.");
	}

}
