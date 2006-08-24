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
	public long firstPostedTime;
	public long lastPostedTime;
}

public class Link {
	public String[] info() {
		return new String[] {
			"Plugin which matches on links.",
			"Tim Retout /  Chris Hawley",
			"tim@retout.co.uk /  choob@blood-god.co.uk",
			"$Rev$$Date$"
		};
	}

	Modules mods;
	IRCInterface irc;

	//This specifies the minimum time between when the bot last saw the link,
	//it starts complaining about it being ooooold
	private static final long FLOOD_INTERVAL = 15 * 60 * 1000; //15 minute

	public Link(Modules mods, IRCInterface irc) {
		this.irc = irc;
		this.mods = mods;

		//Purge
		/*
		List<OldLink> links = mods.odb.retrieve(OldLink.class, "");
		for (OldLink link : links) {
			mods.odb.delete(link);
		} */
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

	public void filterLink(Message mes, Modules mods, IRCInterface irc) {

		if (!((mes instanceof ChannelMessage) || (mes instanceof ChannelAction))) return;
		if (mes.getMessage().matches(irc.getTriggerRegex() + ".*")) return; //ignore commands
		if (mes.getSynthLevel() > 0) return; //ignore synthetic messages
		String reply = getOldReply(mes,true);
		if (reply == null) return;
		else {
			irc.sendContextReply(mes,reply);
		}
	}

	public void commandIsOld(Message mes)
	{
		if (mes.getContext().matches("^#.*")) return;
		String reply = getOldReply(mes,false);
 		if (reply == null) irc.sendContextReply(mes,"Link is not old.");
		else {
			irc.sendContextReply(mes,reply);
		}
	}

	private String getOldReply(Message mes, boolean channelMessage)
	{
		Matcher linkMatch = linkPattern.matcher(mes.getMessage());
		// Iterate over links in line.
		while (linkMatch.find()) {
			String link = linkMatch.group(0);
			//Ensure that it isn't in our exceptions list
			for (int i=0;i<exceptions.length;i++) {
				if (link.equalsIgnoreCase(exceptions[i])) {
					return null;
				}
			}
			//Check objectDB for an existing link with this URL
			String queryString = "WHERE URL = \"" + mods.odb.escapeString(link) + "\"";
			if (channelMessage) {
				queryString = queryString + " AND channel = \"" + mods.odb.escapeString(mes.getContext()) + "\"";
			}
			List<OldLink> links = mods.odb.retrieve(OldLink.class, queryString);
			if (links.size() > 0) {
				OldLink linkObj = links.get(0);
				if (System.currentTimeMillis() - linkObj.lastPostedTime > FLOOD_INTERVAL) {
					String timeBasedOld = "ld";
					long timeSinceOriginal = System.currentTimeMillis() - linkObj.firstPostedTime;
					//Check how many hours old it is, for each one over 4, add a o.
					int oldHours = (int)timeSinceOriginal/(60*60*1000);
					String output = "O" + Integer.toBinaryString(oldHours).replaceAll("0", "o").replaceAll("1", "O") + "ld! (link originally posted " + mods.date.timeLongStamp(timeSinceOriginal) + " ago by " + linkObj.poster;
					if (!channelMessage) output = output + " in " + linkObj.channel;
					output = output + ")";

					//Update the last posted time.
				 	linkObj.lastPostedTime = mes.getMillis();
					mods.odb.update(linkObj);
					return output;
				}
			} else if (channelMessage) {
				OldLink linkObj = new OldLink();
				linkObj.URL = link;
				linkObj.poster = mods.nick.getBestPrimaryNick(mes.getNick());
				linkObj.channel = mes.getContext();
				linkObj.firstPostedTime = mes.getMillis();
				linkObj.lastPostedTime = mes.getMillis();
				mods.odb.save(linkObj);
			}
		}
		return null;
	}
}
