/** @author Richard Warburton */

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
	public NickColour(final IRCInterface irc, final Modules mods)
	{
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
	
	public void commandTest(final Message mes, final Modules mods,
			final IRCInterface irc) throws SQLException {
		
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
			final int[][] buffer = new int[d][d];
			
			// find max
			final List<Integer> distances = new ArrayList<Integer>(); 
			for(Map<String,Integer> entry:adjacentLines.values()) {
				distances.addAll(entry.values());
			}
			final int max = Collections.max(distances);
			final List<String> names = new ArrayList<String>(adjacentLines.keySet());
			
			for(Map.Entry<String, Map<String,Integer>> e :adjacentLines.entrySet()) {
				final int[] row = buffer[names.indexOf(e.getKey())];
				for (Map.Entry<String, Integer> inner : e.getValue().entrySet()) {
					row[names.indexOf(inner.getKey())] = max - inner.getValue();
				}
			}
			
			final List<Integer>[] clusters = clusterFuck(IRSSI_COLOR_CODES.length,buffer);
			
			out.println("HTTP/1.0 200 OK");
			out.println("Content-Type: text/html");
			out.println();
			for (int i = 0; i < clusters.length; i++) {
				for (int id : clusters[i]) {
					out.println(names.get(id) + ":" + IRSSI_COLOR_CODES[i]);
				}
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
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
	
	/**
	 * Implements a k means clustering using Lloyd's Algorithm
	 * 
	 * Uses terminology from http://en.wikipedia.org/wiki/K-means_clustering, with d = data.length
	 * 
	 * precondition: k <= data.length
	 * 
	 * @param k - the number of sets
	 * @param data - matrix of data representation, distance vectors from each other, ie index (i,j) is the distance of i from j.
	 * @return a list of sets of data points, each set is a cluster.
	 */
	private List<Integer>[] clusterFuck(final int k, final int[][] data) {
		final int d = data.length;
		if(d < k) {
			throw new RuntimeException("error: d < k");
		}
		for (int i = 0; i < d; i++) {
			if(data[i].length != d) {
				throw new RuntimeException("Invalid Input data set: matrix must be square");
			}
		}
		
		// Step 1: Assign initial 'means' by randomly picking points from input set
		float[][] means = new float[k][d];
		final Random r = new Random();
		// to avoid duplicates
		List<Integer> indices = new ArrayList<Integer>();
		for (int i = 0; i < d; i++) {
			indices.add(i);
		}
		for (int i = 0; i < k; i++) {
			final int[] vals = data[indices.remove(r.nextInt(indices.size()-1))];
			for (int j = 0; j < d; j++) {
				means[i][j] = (float) vals[j];
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
					distances[j] = euclidean(means[j], data[i], d);
				}
				clusters[min(distances)].add(i);
			}
			
			// Step 3: calculate new centoids
			for (int i = 0; i < k; i++) {
				final float[] center = new float[d];
				for (int j : clusters[i]) {
					final int[] element = data[j];
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
	private float euclidean(float[] mean,int[] point, int d) {
		float f = 0f;
		for(int i = 0; i < d; i++) {
			f += Math.pow(mean[i] - point[i], 2);
		}
		return (float) Math.sqrt(f);
	}
}
