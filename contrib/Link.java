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

public class OldLink {
	public int id;
	public String URL;
	public String poster;
	public String channel;
	public long postedTime;
}

public class Link
{
	public String[] info()
	{
		return new String[] {
			"Plugin which matches on links.",
			"Tim Retout /  Chris Hawley",
			"tim@retout.co.uk /  choob@blood-god.co.uk",
			"Version 0.2"
		};
	}

	Modules mods;
	IRCInterface irc;

	Set links = Collections.synchronizedSet(new HashSet());

	public Link(Modules mods, IRCInterface irc)
	{
		this.irc = irc;
		this.mods = mods;
		
		List<OldLink> links = mods.odb.retrieve(OldLink.class, "");
		//Purge!
		for(OldLink link : links) {
			mods.odb.delete(link);
		}
	}

	public static String filterLinkRegex = "http://\\S*";

	//Some exceptions to what can be "olded" - like google
	private String[] exceptions = {
		"http://www.google.co.uk",
		"http://www.google.com",
		"http://google.com",
		"http://google.co.uk"
	};
	
	final private static Pattern linkPattern = Pattern.compile(filterLinkRegex);

	public synchronized void filterLink( Message mes, Modules mods, IRCInterface irc )
	{
		Matcher linkMatch = linkPattern.matcher(mes.getMessage());
		
		// Iterate over links in line.
		while (linkMatch.find()) {
			String link = linkMatch.group(0);
			for (int i=0;i<exceptions.length;i++) {
				if (link.equalsIgnoreCase(exceptions[i])) {
					return;
				}
			}
			//Check objectDB for an existing link with this URL
			List<OldLink> links = mods.odb.retrieve(OldLink.class, "WHERE URL = \"" + mods.odb.escapeString(link) + "\" AND channel = \"" + mods.odb.escapeString(mes.getContext()) + "\"");
			if (links.size() > 0) {
				StringBuilder output = new StringBuilder();
				output.append("oooolllldddd! (link originally posted in ");
				output.append(links.get(0).channel + " ");
				output.append(mods.date.timeLongStamp(System.currentTimeMillis() - links.get(0).postedTime));
				output.append(" ago by ");
				output.append(links.get(0).poster);
				output.append(")");
				irc.sendContextReply(mes, output.toString());
			} else {
				OldLink linkObj = new OldLink();
				linkObj.URL = link;
				linkObj.poster = mods.nick.getBestPrimaryNick(mes.getNick());
				linkObj.channel = mes.getContext();
				linkObj.postedTime = mes.getMillis();
				mods.odb.save(linkObj);
			}
		}
	}
}
