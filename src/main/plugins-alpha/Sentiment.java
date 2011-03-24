import static java.lang.Float.parseFloat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

/**
 * @author mulletron
 * 
 *         Performs trivial sentiment analysis on choob history
 * 
 *         Calculates average ANEW scores for all words for a person/channel
 *         (suggested by whythehell).
 * 
 *         TODO:
 * 
 *         add gender values to choob'
 * 
 *         add more history/time options to search
 * 
 *         normalise a period over an extended period - eg a day or a year
 * 
 *         nick linking
 */
public class Sentiment {

	private final Pattern wordPattern = Pattern.compile("[a-zA-Z]+");

	private final Modules mods;
	private final IRCInterface irc;
	private final Map<String, Map<String, Anew>> cache;

	public String[] info() {
		return new String[] { "", "mulletron", "ALPHA ALPHA", "<3", };
	}

	public String[] helpTopics = { "Using" };

	public String[] helpUsing = { "!Sentiment.day <stat> <nick>|<channel> prints sentiment stats from your last day",
			"Scores are now between 100 and 0", "!Sentiment.info lists possible sentiment metrics" };

	public Sentiment(final Modules mods, final IRCInterface irc) {
		this.mods = mods;
		this.irc = irc;

		// load up the cache
		cache = new HashMap<String, Map<String, Anew>>();
		for (Anew e : mods.odb.retrieve(Anew.class, "")) {
			Map<String, Anew> map = cache.get(e.stat);
			if (map == null) {
				map = new HashMap<String, Anew>();
				cache.put(e.stat, map);
			}
			map.put(e.word, e);
		}
	}

	/**
	 * @param mes
	 *            Imports a csv file from a url
	 * @throws IOException
	 */
	public void commandImport(final Message mes) throws IOException {
		// This feels ridiculously messy, wtb simultaneous assignment,
		// usingResource etc.
		final List<String> arg = mods.util.getParams(mes);
		final URL url = new URL(arg.get(2));
		final BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
		final List<String> errors = new ArrayList<String>();
		final String statName = mods.odb.escapeString(arg.get(1));
		Map<String, Anew> localCache = cache.get(statName);
		if (localCache == null) {
			localCache = new HashMap<String, Anew>();
			cache.put(statName, localCache);
		}
		try {
			String line;
			int i = 1;
			int count = 0;
			while ((line = reader.readLine()) != null) {
				final String[] split = line.split(",");
				if (split.length != 2) {
					errors.add("warning: ignoring line " + i + " since it contains whitespace");
				} else {
					final String word = split[0].toLowerCase();
					if (word.contains(" ")) {
						errors.add("warning: ignoring line " + i + " since the name contains whitespace");
					} else {
						try {
							final Anew anew = new Anew(word, statName, parseFloat(split[1]));
							mods.odb.save(anew);
							count++;
							if (localCache.put(word, anew) != null) {
								errors.add("warning: line " + i + " has overwritten the value of " + word + " in the file");
								count--;
							}
						} catch (NumberFormatException e) {
							errors.add("warning: ignoring line " + i + " due to a number format problem");
						}
					}
				}
				i++;
			}

			irc.sendContextReply(mes, "Imported " + count + " words to " + statName);

		} finally {
			// TODO: stop flooding
			for (String e : errors) {
				irc.sendContextReply(mes, e);
			}
			reader.close();
		}
	}

	private Calendar makeDay() {
		final Calendar c = new GregorianCalendar();
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.add(Calendar.DAY_OF_YEAR, -1);
		return c;
	}

	public void commandInfo(final Message mes) {
		irc.sendContextReply(mes, "The word corpus currently incorporates the following statistics: " + cache.keySet());
	}

	/**
	 * 
	 * @param mes
	 * @throws SQLException
	 */
	public void commandDay(final Message mes) throws SQLException {
		final List<String> arg = mods.util.getParams(mes);
		final String nick = (arg.size() > 1) ? arg.get(1) : mes.getNick();
		final String statName = arg.get(2);
		final Map<String, Anew> localCache = cache.get(statName);
		if (localCache == null) {
			irc.sendContextReply(mes, "Unknown term, try using the sentiment.info command");
		} else {
			final Connection conn = mods.odb.getConnection();
			final String cond = (nick.startsWith("#")) ? "Channel like ?" : "Nick like ?";
			try {
				final PreparedStatement s = conn.prepareStatement("select Text from History where " + cond + " and Time > ?");
				try {
					s.setString(1, nick);
					s.setObject(2, makeDay().getTimeInMillis());
					final ResultSet rs = s.executeQuery();
					float total = 0, count = 0, reliability = 0;
					while (rs.next()) {
						final String text = rs.getString(1);
						// System.out.println(text);
						final Matcher matcher = wordPattern.matcher(text);
						while (matcher.find()) {
							reliability++;
							final Anew score = localCache.get(matcher.group().toLowerCase());
							if (score != null) {
								total += score.value;
								count++;
							}
						}
					}
					irc.sendContextReply(mes,
							statName + " = " + Math.round(total * 100 / count) + "%, reliability = " + Math.round(count * 100 / reliability) + "%");
				} finally {
					s.close();
				}
			} finally {
				mods.odb.freeConnection(conn);
			}
		}
	}
}

/**
 * 
 * @author rlmw
 * 
 *         Represents a single word to odb, storing its score
 * 
 */
class Anew {
	public int id;

	public String word;
	// this was originally normalised, but choob touched me. No daddy No.
	public String stat;
	public float value;

	protected Anew(String word, String stat, float value) {
		super();
		this.word = word;
		this.stat = stat;
		this.value = value;
	}

	public Anew() {
		// Unhide
	}

}