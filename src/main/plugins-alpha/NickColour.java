/** @author Richard Warburton */

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

/**
 * Requires permissions: "ChoobPermission db.connection.checkout" and "ChoobPermission db.connection.checkin"
 *
 * Generates a list
 */
public class NickColour
{

	private static final String[] IRSSI_COLOR_CODES = {
		"2", "3", "4", "5", "6", "7", "9", "10", "11", "12", "13",
	};

	private final Modules mods;
	private final IRCInterface irc;

	public NickColour(final IRCInterface irc, final Modules mods)
	{
		this.irc = irc;
		this.mods = mods;
	}

	public String[] info()
	{
		return new String[] {
			"Generates List of Nick Colours",
			"mulletron",
			"ALPHA ALPHA",
			"<3",
		};
	}

	public void commandTest(final Message mes) throws SQLException {

		irc.sendContextReply(mes, "Testing Database Connection:");

		final Connection conn = mods.odb.getConnection();
		mods.odb.freeConnection(conn);
		conn.close();

		irc.sendContextReply(mes, "Passed!");
	}

	public String[] helpTopics = { "Using" };

	public String[] helpUsing = {
		  "!. On Codd: curl http://bot.uwcs.co.uk/rpc/nickcolour.list > $HOME/.irssi/saved_colors",
		  "2: In irssi: /color load",
	};

	//-----------------------------------------------

	public void webList(final PrintWriter out, final String params, final String[] user) {

		Connection conn = null;
		try {
			conn = mods.odb.getConnection();
			// I know this looks weird, but its to stop mysql doing the WHERE before it does the LIMIT and taking AAAAAAAAAAGES
			final PreparedStatement stmt = conn.prepareStatement(
				"SELECT Nick,Channel from (SELECT Nick,Channel FROM History ORDER BY Time DESC LIMIT 15000) AS foo WHERE not Channel IS null");
			// AND Nick != 'Faux';");
			//stmt.setString(1, mes.getNick());
			final ResultSet rs = stmt.executeQuery();

			// Extract the adjacent line counts from the database
			final Map<String,Map<String,Integer>> adjacentLines = new HashMap<String, Map<String,Integer>>();
			final Map<String,String> last = new HashMap<String, String>();
			while(rs.next()) {
				final String nick = rs.getString("Nick"), chan = rs.getString("Channel");
				final String lastNick = last.get(chan);
				if(lastNick != null && ! lastNick.equals(nick)) {
					// Sigh @ Java - the sad thing is this isn't easily genericisable without Integer and HashMap being Cloneable or lambdas
					Map<String, Integer> other = adjacentLines.get(nick);
					if(other == null) {
						other = new HashMap<String, Integer>();
						adjacentLines.put(nick, other);
					}
					Integer i = other.get(lastNick);
					other.put(lastNick, (i == null) ? 1 : i + 1);
				}
				last.put(chan, nick);
			}

			final int d = adjacentLines.size();
			final double[][] buffer = new double[d][d];

			// find max
			final List<Integer> distances = new ArrayList<Integer>();
			for(Map<String,Integer> entry:adjacentLines.values()) {
				distances.addAll(entry.values());
			}
			final double max = Collections.max(distances);
			final List<String> names = new ArrayList<String>(adjacentLines.keySet());

			for(Map.Entry<String, Map<String,Integer>> e :adjacentLines.entrySet()) {
				final double[] row = buffer[names.indexOf(e.getKey())];
				for (Map.Entry<String, Integer> inner : e.getValue().entrySet()) {
					// fix
					row[names.indexOf(inner.getKey())] = 1d - ((double)inner.getValue())/max;
				}
			}

			final List<Integer>[] clusters = clusterFuck(IRSSI_COLOR_CODES.length,buffer);

			html(out);
			for (int i = 0; i < clusters.length; i++) {
				for (int id : clusters[i]) {
					out.println(names.get(id) + ":" + IRSSI_COLOR_CODES[i]);
				}
			}

		} catch (SQLException e) {
			e.printStackTrace(out);
		} finally {
			if (conn != null) {
				mods.odb.freeConnection(conn);
				try {
					conn.close();
				} catch (SQLException e1) {
					e1.printStackTrace();
				}
			}
		}
	}

	private void html(final PrintWriter out) {
		out.println("HTTP/1.0 200 OK");
		out.println("Content-Type: text/html");
		out.println();
		out.println();
	}

	/**
	 * Implements a k means clustering using Lloyd's Algorithm
	 *
	 * Uses terminology from http://en.wikipedia.org/wiki/K-means_clustering, with d = data.length
	 *
	 * precondition: k <= data.length
	 *
	 * @param k - the number of sets
	 * @param buffer - matrix of data representation, distance vectors from each other, ie index (i,j) is the distance of i from j.
	 * @return a list of sets of data points, each set is a cluster.
	 */
	private List<Integer>[] clusterFuck(final int k, final double[][] buffer) {
		final int d = buffer.length;
		if(d < k) {
			throw new RuntimeException("error: d < k");
		}
		for (int i = 0; i < d; i++) {
			if(buffer[i].length != d) {
				throw new RuntimeException("Invalid Input data set: matrix must be square");
			}
		}

		// Step 1: Assign initial 'means' by randomly picking points from input set
		double[][] means = new double[k][d];
		final Random r = new Random();
		// to avoid duplicates
		List<Integer> indices = new ArrayList<Integer>();
		for (int i = 0; i < d; i++) {
			indices.add(i);
		}
		for (int i = 0; i < k; i++) {
			final double[] vals = buffer[indices.remove(r.nextInt(indices.size()-1))];
			for (int j = 0; j < d; j++) {
				means[i][j] = vals[j];
			}
		}

		// clusters[i] is the set of elements
		@SuppressWarnings("unchecked") final List<Integer>[] clusters = new List[k];
		for (int i = 0; i < k; i++) {
			clusters[i] = new ArrayList<Integer>();
		}

		List<Integer>[] previous = null;
		do {
			//System.out.println("Means" + Arrays.deepToString(means));
			previous = Arrays.copyOf(clusters, k);

			// Step 2: assign each element to the cluster with the closest mean
			for (int i = 0; i < d; i++) {
				float[] distances = new float[k];
				for (int j = 0; j < k; j++) {
					distances[j] = euclidean(means[j], buffer[i], d);
				}
				clusters[min(distances)].add(i);
			}

			// Step 3: calculate new centoids
			for (int i = 0; i < k; i++) {
				final double[] center = new double[d];
				for (int j : clusters[i]) {
					final double[] element = buffer[j];
					for(int l = 0; l < d; l++) {
						center[l] += element[l];
					}
				}
				for(int l = 0; l < d; l++) {
					center[l] /= clusters[i].size();
				}
				means[i] = center;
			}

		// Step 4: convergence - when assigments no longer change
		} while ( !Arrays.deepEquals(clusters,previous) );

		return clusters;
	}

	/**
	 * @param data input values
	 * @return index of smallest element in data
	 */
	private int min(float[] data) {
		int index = 0;
		float f = Float.MAX_VALUE;
		for(int i = 0; i < data.length; i++) {
			if(data[i] < f) {
				f = data[i];
				index = i;
			}
		}
		return index;
	}

	/**
	 * We use euclidean distance as our distance measure
	 *
	 * @param mean the dimensions of the mean
	 * @param point the point we are measuring the distance from
	 * @param d the dimensionality
	 * @return
	 */
	private float euclidean(double[] mean,double[] point, int d) {
		float f = 0f;
		for(int i = 0; i < d; i++) {
			f += Math.pow(mean[i] - point[i], 2);
		}
		return (float) Math.sqrt(f);
	}

	static class WordShapeImpl {
		private static final float LEN_BIAS = 3;
		private static final int SIZE_BIAS = 5;
		private static final int LEN_OFFSET = 10;
		private static final int SHAPE_MISS_VALUE = 5;

		private final static Set<String> SKIP = Collections.unmodifiableSet(new HashSet<String>(){{
			add("BadgerBOT");
		}});

		static enum Shape {
			ASC, DESC, NEUTRAL, OTHER;
			private final static Map<Character, Shape> SHAPE = Collections.unmodifiableMap(new HashMap<Character, Shape>(){{
				put('a', Shape.NEUTRAL);
				put('b', Shape.ASC);
				put('c', Shape.NEUTRAL);
				put('d', Shape.ASC);
				put('e', Shape.NEUTRAL);
				put('f', Shape.ASC);
				put('g', Shape.DESC);
				put('h', Shape.ASC);
				put('i', Shape.NEUTRAL);
				put('j', Shape.DESC);
				put('k', Shape.ASC);
				put('l', Shape.ASC);
				put('m', Shape.NEUTRAL);
				put('n', Shape.NEUTRAL);
				put('o', Shape.NEUTRAL);
				put('p', Shape.DESC);
				put('q', Shape.DESC);
				put('r', Shape.NEUTRAL);
				put('s', Shape.NEUTRAL);
				put('t', Shape.ASC);
				put('u', Shape.NEUTRAL);
				put('v', Shape.NEUTRAL);
				put('w', Shape.NEUTRAL);
				put('x', Shape.NEUTRAL);
				put('y', Shape.DESC);
				put('z', Shape.NEUTRAL);
			}});

			static Shape get(char c) {
				if (SHAPE.containsKey(c))
					return SHAPE.get(c);

				return OTHER;
			}

			static List<Shape> get(String left) {
				List<Shape> l = new ArrayList<Shape>();
				for (char c : left.toCharArray())
					l.add(Shape.get(c));
				return l;
			}
		}

		private static float shapeScore(String left, String right) {
			float ret = 0;

			final List<Shape> lsh = Shape.get(left),
				rsh = Shape.get(right);
			final Iterator<Shape> it = rsh.iterator();
			for (Shape l : lsh) {
				if (l != it.next())
					ret += SHAPE_MISS_VALUE;
				if (!it.hasNext())
					break;
			}

			return ret;
		}

		public static Map<String, String> main(Iterable<String> NICKS) {
			final List<String> n = new ArrayList<String>();
			for (String clean : NICKS) {
				if (!SKIP.contains(clean) && !n.contains(clean))
					n.add(clean);
			}

			final Map<String, Set<String>> m = map();

			m.get("10").add("BadgerBOT");

			for (String nick : n) {
				float bestScore = -Float.MAX_VALUE;
				String bestCat = null;
				for (Entry<String, Set<String>> a : m.entrySet()) {
					float score = score(nick, a);
					if (score > bestScore) {
						bestScore = score;
						bestCat = a.getKey();
					}
				}

				m.get(bestCat).add(nick);
			}

			Map<String, String> ret = new HashMap<String, String>();
			for (Entry<String, Set<String>> a : m.entrySet())
				for (String b : a.getValue())
					ret.put(b, a.getKey());
			return ret;
		}

		private static float score(String nick, Entry<String, Set<String>> a) {
			return LEN_BIAS * lenScore(nick, a.getValue())
				+ shapeScore(nick, a.getValue())
				- SIZE_BIAS * a.getValue().size();
		}

		private static float shapeScore(String nick, Set<String> value) {
			if (value.isEmpty())
				return 0;

			float ret = 0;
			for (String x : value)
				ret += shapeScore(nick, x);
			return ret / value.size();
		}


		/** Higher is better. */
		private static float lenScore(String nick, Set<String> existing) {
			if (existing.isEmpty())
				return 0;
			float score = 0;
			for (String other : existing)
				score += Math.abs(nick.length() - other.length()) - LEN_OFFSET;
			return score / existing.size();
		}

		private static Map<String, Set<String>> map() {
			final Map<String, Set<String>> m = new HashMap<String, Set<String>>();
			for (String code : IRSSI_COLOR_CODES)
				m.put(code, new HashSet<String>());
			return Collections.unmodifiableMap(m);
		}
	}

	public void webList2(final PrintWriter out, final String params, final String[] user) {

		Connection conn = mods.odb.getConnection();
		try {
			// I know this looks weird, but its to stop mysql doing the WHERE before it does the LIMIT and taking AAAAAAAAAAGES
			final ResultSet rs = conn.prepareStatement("SELECT Nick,count(*) as cnt from " +
					"(SELECT Nick,Channel,Time FROM History ORDER BY Time DESC LIMIT 35000) AS foo " +
					"WHERE not Channel IS null " +
					"and ((UNIX_TIMESTAMP()-(`Time`/1000)) < (60*60*24*14)) " +
					"group by nick " +
					"having cnt > 5 " +
					"order by cnt desc").executeQuery();
			final List<String> nicks = new ArrayList<String>();
			while (rs.next())
				nicks.add(rs.getString("Nick"));

			html(out);
			final Map<String, String> shape = WordShapeImpl.main(nicks);
			if (params.equals("show")) {
				printTable(out, shape);

			} else
				for (Entry<String, String> a : shape.entrySet())
					out.println(a.getKey() + ":" + a.getValue());

		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			mods.odb.freeConnection(conn);
		}
	}

	private void printTable(final PrintWriter out, final Map<String, String> shape) {
		final Map<String, String> colours = new HashMap<String, String>() {{
			put("14", "85,85,85");
			put("13", "255,85,255");
			put("12", "85,85,255");
			put("11", "85,255,255");
			put("10", "0,186,187");
			put("9", "85,255,85");
			put("8", "255,255,85");
			put("7", "187,187,0");
			put("6", "187,0,187");
			put("5", "187,0,0");
			put("4", "255,85,85");
			put("3", "0,187,0");
			put("2", "0,0,187");
		}};

		out.println("<style type='text/css'>\n" +
				".m{color:rgb(187,187,187)}\n" +
				"body{background-color: black;font-family: Consolas,Code2000,\"Lucida Console\",fixed}");
		for (Entry<String, String> a : colours.entrySet())
			out.println(".c" + a.getKey() + "{color:rgb(" + a.getValue() + ")}");
		out.println("</style>");
		Map<String, List<String>> flipped = new HashMap<String, List<String>>();
		for (Entry<String, String> a : shape.entrySet()) {
			final String v = a.getValue();
			List<String> s = flipped.get(v);
			if (null == s)
				flipped.put(v, s = new ArrayList<String>());
			s.add(a.getKey());
		}
		for (Entry<String, List<String>> a : flipped.entrySet()) {
			out.println("<ul>");
			final List<String> peeps = a.getValue();
			Collections.sort(peeps, new Comparator<String>() {
				@Override public int compare(String o1, String o2) {
					return o1.length() - o2.length();
				}
			});

			for (String b : peeps)
				out.println("  <li><span class='c14'>&lt;</span>" +
						"<span class='m'>" + op() + "</span>" +
						"<span class='c" + a.getKey() + "'>" + mods.scrape.readyForHtml(b) + "</span>" +
						"<span class='c14'>&gt;</span></li>");
			out.println("</ul>");
		}
	}


	private static final Random RAND = new Random();
	private static String op() {
		final int r = RAND.nextInt(50);
		if (0 == r)
			return "&";
		if (1 == r)
			return "~";
		if (r < 6)
			return "@";
		if (r < 16)
			return "+";
		return " ";
	}

//	public static void main(String[] args) {
//		final PrintWriter pw = new PrintWriter(System.out);
//		printTable(pw, new HashMap<String, String>() {{
//			put("b", "2");
//			put("c", "3");
//			put("d", "4");
//			put("e", "5");
//			put("f", "6");
//			put("g", "7");
//			put("h", "8");
//			put("i", "9");
//			put("j", "10");
//			put("k", "11");
//			put("l", "12");
//			put("aeohn", "12");
//			put("m", "13");
//		}});
//		pw.flush();
//		pw.close();
//	}
}
