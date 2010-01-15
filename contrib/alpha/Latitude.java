/** @author rlmw */

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

/**
 * Uses google latitude api to obtain locations for people
 * 
 * Uses the JSON api
 * 
 * Requires permission: "security.grant plugin.Latitude Socket www.google.com connect,resolve"
 * @author rlmw
 * @see http://www.google.com/latitude/apps/badge
 */
public class Latitude {

	public String[] info()
	{
		return new String[] {
			"Location lookup from Google Latitude",
			"mulletron",
			"ALPHA ALPHA",
			"<3",
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
	private Location getLocation(String id) throws IOException, ParseException {
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
		
		String nick = mods.util.getParamString(mes);
		nick = (nick.length() > 0)?nick:mes.getNick();
		final List<Badge> values = mods.odb.retrieve(Badge.class, "where nick = '"+nick+"'");
		if(values.size() == 0) {
			irc.sendContextReply(mes, nick+" has no saved google latitude badge");
		} else {
			irc.sendContextReply(mes, nick+" is believed to be located in "+getLocation(values.get(0).badge_id));
		}
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

	public Location(String geocode, Number x, Number y) {
		super();
		this.geocode = geocode;
		this.x = x;
		this.y = y;
	}

	@Override
	public String toString() {
		return geocode + " at (" + x + "," + y + ")";
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

	public Badge(String nick, String badgeId) {
		super();
		this.nick = nick;
		badge_id = badgeId;
	}
	
}
