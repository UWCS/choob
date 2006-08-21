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

	public static String filterBotRegex = "(good|bad) bot";
	final private static Pattern botPattern = Pattern.compile(filterBotRegex);

	public void filterBot( Message mes, Modules mods, IRCInterface irc )
	{
		// Ignore synthetic messages
		if (mes.getSynthLevel() > 0)
			return;

		Matcher botMatch = botPattern.matcher(mes.getMessage());
		int counter = 0;

		// Iterate over matches
		while (botMatch.find()) {
			if (botMatch.group(1).equals("good")) {
				counter++;
			} else if (botMatch.group(1).equals("bad")) {
				counter--;
			}
		}
		
		// Work out the balance of the statements, and reply
		if (counter > 0) {
			irc.sendContextMessage(mes, "Thanks, " + mes.getNick() + " :-)");
		} else if (counter == 0) {
			irc.sendContextReply(mes, "Make your mind up.");
		} else {
			irc.sendContextReply(mes, "Bastard.");
		}
	}

}
