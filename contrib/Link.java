import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

/**
 * Choob link plugin
 *
 * @author Tim Retout <tim@retout.co.uk>
 *
 */

public class Link
{
	public String[] info()
	{
		return new String[] {
			"Plugin which matches on links.",
			"Tim Retout",
			"tim@retout.co.uk",
			"Version 0.1"
		};
	}

	Modules mods;
	IRCInterface irc;

	Set links = Collections.synchronizedSet(new HashSet());

	public Link(Modules mods, IRCInterface irc)
	{
		this.irc = irc;
		this.mods = mods;
	}

	public static String filterLinkRegex = "http://\\S*";

	final private static Pattern linkPattern = Pattern.compile(filterLinkRegex);

	public synchronized void filterLink( Message mes, Modules mods, IRCInterface irc )
	{
		Matcher linkMatch = linkPattern.matcher(mes.getMessage());
		
		// Iterate over links in line.
		while (linkMatch.find()) {
			String link = linkMatch.group(0);
			
			if (links.contains(link))
				irc.sendContextReply(mes, "oooolllldddd");
			else
				links.add(link);
		}
	}
}
