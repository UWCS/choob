import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

/**
 * Response plugin.
 *
 * @author Tim Retout <tim@retout.co.uk>
 *
 */

public class Response {
	public String[] info() {
		return new String[] {
			"Response plugin.",
			"Tim Retout",
			"tim@retout.co.uk",
			"$Rev$$Date$"
		};
	}

	Modules mods;
	IRCInterface irc;

	public Response(final Modules mods, final IRCInterface irc) {
		this.irc = irc;
		this.mods = mods;
	}

	/*
	 *	Good/Bad bot replies.
	 */

	String[] badBot = {
		"Bastard.",
		"Muppet."
	};

	final public static String filterBotRegex = "\\b(\\w+) bot\\b";
	final private static Pattern botPattern = Pattern.compile(filterBotRegex, Pattern.CASE_INSENSITIVE);
	final private static Pattern badPattern = Pattern.compile("bad|la+me|st(u+|oo+)pid|gooo+d", Pattern.CASE_INSENSITIVE);
	final private static Pattern goodPattern = Pattern.compile("good|nice", Pattern.CASE_INSENSITIVE);

	public void filterBot( final Message mes ) {
		// Ignore synthetic messages
		if (mes.getSynthLevel() > 0)
			return;

		final Matcher botMatch = botPattern.matcher(mes.getMessage());
		int counter = 0;
		boolean triggered = false;

		// Iterate over matches
		while (botMatch.find()) {
			if ( goodPattern.matcher(botMatch.group(1)).matches() ) {
				counter++;
				triggered = true;
			} else if ( badPattern.matcher(botMatch.group(1)).matches() ) {
				counter--;
				triggered = true;
			}
		}

		// Work out the balance of the statements, and reply
		if (counter > 0) {
			irc.sendContextMessage(mes, "Thanks, " + mes.getNick() + " :-)");
		} else if (counter < 0) {
			irc.sendContextReply(mes, chooseRand.from(badBot));
		} else if (counter == 0 && triggered) {
			irc.sendContextMessage(mes, "Make your mind up, "
				+ mes.getNick() + ".");
		}
	}

}

/*
 *	Helper class to choose a random string from an array.
 */
class chooseRand {
	static Random generator = new Random();

    public static String from (final String[] array) {
        final int rand = generator.nextInt(array.length);
        return array[rand];
    }
}
