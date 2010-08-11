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
 */
public class Sentiment {

	private final Pattern wordPattern = Pattern.compile("[a-zA-Z]+");

	private final Modules mods;
	private final IRCInterface irc;
	private final Map<String, Anew> cache;

	public String[] info() {
		return new String[] { "", "mulletron", "ALPHA ALPHA", "<3", };
	}

	public String[] helpTopics = { "Using" };

	public String[] helpUsing = { "!Sentiment.day <nick>|<channel>|nothing prints sentiment stats from your last day",
			"Valence - 10 = pleasant, 0 = unpleasant ", "Arousal - 0 = calm, 10 = excited ", "Dominance - 10 = control, 0 = dominated " };

	public Sentiment(final Modules mods, final IRCInterface irc) {
		this.mods = mods;
		this.irc = irc;
		cache = new HashMap<String, Anew>();
		for (Anew e : mods.odb.retrieve(Anew.class, "")) {
			cache.put(e.word, e);
		}
	}

	/**
	 * @param mes
	 *            Imports a csv file from a url
	 * @throws IOException
	 */
	public void commandImport(final Message mes) throws IOException {
		final URL url = new URL(mods.util.getParamString(mes));
		final BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
		final List<String> errors = new ArrayList<String>();
		try {
			String line;
			int i = 1;
			int count = 0;
			while ((line = reader.readLine()) != null) {
				final String[] split = line.split(",");
				if (split.length != 4) {
					errors.add("warning: ignoring line " + i + " since it contains whitespace");
				} else {
					final String word = split[0].toLowerCase();
					if (word.contains(" ")) {
						errors.add("warning: ignoring line " + i + " since it contains whitespace");
					} else {
						try {
							final Anew anew = new Anew(word, parseFloat(split[1]), parseFloat(split[2]), parseFloat(split[3]));
							mods.odb.save(anew);
							count++;
							if (cache.put(word, anew) != null) {
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

			irc.sendContextReply(mes, "Imported " + count + " words");

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

	/**
	 * 
	 * @param mes
	 * @throws SQLException
	 */
	public void commandDay(final Message mes) throws SQLException {
		final List<String> arg = mods.util.getParams(mes);
		final String nick = (arg.size() > 1) ? arg.get(1) : mes.getNick();
		final Connection conn = mods.odb.getConnection();
		final String cond = (nick.startsWith("#")) ? "Channel like ?" : "Nick like ?";
		System.out
				.println("START SPAMMING NOW -------------------------------------------------------------------------------------------------------");
		try {
			final PreparedStatement s = conn.prepareStatement("select Text from History where " + cond + " and Time > ?");
			try {
				s.setString(1, nick);
				s.setObject(2, makeDay().getTimeInMillis());
				final ResultSet rs = s.executeQuery();
				float valence = 0, arousal = 0, dominance = 0, count = 0;
				while (rs.next()) {
					final String text = rs.getString(1);
					System.out.println(text);
					final Matcher matcher = wordPattern.matcher(text);
					while (matcher.find()) {
						final Anew score = cache.get(matcher.group().toLowerCase());
						if (score != null) {
							valence += score.valence;
							arousal += score.arousal;
							dominance += score.dominance;
							count++;
						}
					}
				}
				irc.sendContextReply(mes, "Valence = " + (valence / count) + " Arousal = " + (arousal / count) + " Dominance = "
						+ (dominance / count));
			} finally {
				s.close();
			}
		} finally {
			mods.odb.freeConnection(conn);
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
	public float valence;
	public float arousal;
	public float dominance;

	protected Anew(final String word, final float f, final float g, final float h) {
		super();
		this.word = word;
		this.valence = f;
		this.arousal = g;
		this.dominance = h;
	}

	public Anew() {
		// Unhide
	}

}