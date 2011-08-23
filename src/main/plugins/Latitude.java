/** @author rlmw */

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.sort;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketPermission;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.plugins.RequiresPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

import com.google.common.collect.Maps;

/**
 * Uses google latitude api to obtain locations for people
 *
 * Uses the JSON api
 *
 * Requires permission: "security.grant plugin.Latitude Socket www.google.com connect,resolve"
 * @author rlmw
 * @see http://www.google.com/latitude/apps/badge
 */
@RequiresPermission(value=SocketPermission.class,permission="www.google.com",action="connect,resolve")
public class Latitude {

	public String[] info()
	{
		return new String[] {
			"Location lookup from Google Latitude",
			"mulletron",
			"ALPHA ALPHA",
			"<3.2",
		};
	}

	public String[] helpTopics = { "Using" };

	public String[] helpUsing = {
		  "Setup: !Latitude.save <id> where <id> is your badge id",
		  "(see http://www.google.com/latitude/apps/badge)",
		  "Usage: !Latitude.location [<nick>] when you want to see nick's location - defaults to your own",
	};

	private final JSONParser parser = new JSONParser();

	/**
	 * Utility function for looking up people's locations
	 * @param id the User's badge id
	 * @return the location the user is at.
	 */
	private Location getLocation(final String id) throws IOException, ParseException {
		URL url = new URL("http://www.google.com/latitude/apps/badge/api?user="
				+ id + "&type=json");
		// Wtb less casts
		final JSONObject json = (JSONObject) parser
				.parse(new InputStreamReader(url.openStream()));
		final JSONObject features = (JSONObject) ((JSONArray) json
				.get("features")).get(0);
		final String location = (String) ((JSONObject) features
				.get("properties")).get("reverseGeocode");
		final JSONArray coords = (JSONArray) ((JSONObject) features
				.get("geometry")).get("coordinates");
		return new Location(location, (Number) coords.get(0), (Number) coords
				.get(1));
	}

	/**
	 * Saves the badge associated with a nick
	 */
	public void commandSave(final Message mes, final Modules mods,
			final IRCInterface irc) throws Exception {

		final String id = mods.util.getParamString(mes);
		final String nick = mes.getNick();
		final List<Badge> values = mods.odb.retrieve(Badge.class, "where nick = '"+nick+"'");
		if(values.size() == 0) {
			mods.odb.save(new Badge(nick,id));
		} else {
			final Badge badge = values.get(0);
			badge.badge_id = id;
			mods.odb.update(badge);
		}
		irc.sendContextReply(mes, "Saved "+nick+"'s badge as "+id);
	}

	/**
	 * Returns the user's last known location
	 */
	public void commandLocation(final Message mes, final Modules mods,
			final IRCInterface irc) throws Exception {

		String nick = getNick(mes, mods);
		Badge badge = getBadge(mes, mods, irc, nick);
		if(badge != null) {			
			irc.sendContextReply(mes, nick+" is believed to be located in "+getLocation(badge.badge_id));
		}
	}

	public void commandBuddies(final Message mes, final Modules mods,
			final IRCInterface irc) throws Exception {
		String nick = getNick(mes, mods);
		Badge badge = getBadge(mes, mods, irc, nick);
		Location loc = getLocation(badge.badge_id);
		if(badge != null) {
			System.out.println("where nick != '"+mods.odb.escapeString(nick)+"'");
			List<Badge> badges = mods.odb.retrieve(Badge.class,"WHERE ! nick = \""+mods.odb.escapeString(nick)+"\"");
			Map<String,Double> dists = Maps.newHashMap();
			for (Badge other : badges) {
				
				Location otherLoc;
				try {
					otherLoc = getLocation(other.badge_id);
					double dist = Math.sqrt(Math.pow(Math.abs(otherLoc.x.doubleValue() - loc.x.doubleValue()),2) + Math.pow(Math.abs(otherLoc.y.doubleValue() - loc.y.doubleValue()),2));
					dists.put(other.nick, dist);
				} catch (Exception e) {
					//irc.sendContextReply(mes, other.nick+" fails - "+e.getMessage());
					// TODO: figure out what to do
				}
			}
			
			List<Entry<String, Double>> vals = newArrayList(dists.entrySet());
			sort(vals, new Comparator<Entry<String,Double>>() {
				@Override
				public int compare(Entry<String, Double> a,
						Entry<String, Double> b) {
					return a.getValue().compareTo(b.getValue());
				}
			});
			if(vals.size() > 5) {
				vals = vals.subList(0, 4);
			}
			for (Entry<String, Double> entry : vals) {
				// I don't want to be mysterious, but I won't
				// tell you what this magic number is
				// and I won't tell you why I won't tell you what this number is.
				irc.sendContextReply(mes, (83 * entry.getValue())+" from "+entry.getKey());
			}
		}
	}

	private Badge getBadge(final Message mes, final Modules mods,
			final IRCInterface irc, String nick) {
		final List<Badge> values = mods.odb.retrieve(Badge.class, "where nick = '"+nick+"'");
		Badge badge = null;
		if(values.size() == 0) {
			irc.sendContextReply(mes, nick+" has no saved Google Latitude badge");
		} else {
			badge = values.get(0);
		}
		return badge;
	}

	private String getNick(final Message mes, final Modules mods) {
		String nick = mods.util.getParamString(mes);
		nick = (nick.length() > 0)?nick:mes.getNick();
		return nick;
	}
	
	
	
}

/**
 * Maybe Store these in a database in future?
 *
 * @author rlmw
 */
class Location {
	public String geocode;
	public Number x, y;

	public Location(final String geocode, final Number x, final Number y) {
		super();
		this.geocode = geocode;
		this.x = x;
		this.y = y;
	}

	@Override
	public String toString() {
		return geocode + " at (" + y + "," + x + ")";
	}
}

/**
 * Stores the google location badge entries for people
 *
 * @see http://www.google.com/latitude/apps/badge
 * @author rlmw
 */
class Badge {

	public int id;
	public String nick;
	public String badge_id;

	public Badge() {
	}

	public Badge(final String nick, final String badgeId) {
		super();
		this.nick = nick;
		badge_id = badgeId;
	}

}
 