/** @author rlmw */

import java.net.SocketPermission;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.plugins.RequiresPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

/**
 * Uses google latitude api to obtain locations for people
 *
 * Uses the JSON api
 *
 * Requires permission:
 * "security.grant plugin.Latitude Socket www.google.com connect,resolve"
 *
 * @author rlmw
 * @see http://www.google.com/latitude/apps/badge
 */
@RequiresPermission(value = SocketPermission.class, permission = "oracleofbacon.org", action = "connect,resolve")
public class SixDegrees {

	final static Pattern p = Pattern.compile("<span class=\"(.*?)\">.*?>(.*?)</");

	public String[] info() {
		return new String[] { "Extracts six degrees information from oracle of bacon", "mulletron", "ALPHA ALPHA", "<3", };
	}

	public String[] helpTopics = { "Using" };

	public String[] helpUsing = { "!SixDegrees.solve Kevin Bacon -> Dennis Hopper" };

	public void commandSolve(final Message mes, final Modules mods, final IRCInterface irc) throws Exception {

		final String[] names = mods.util.getParamString(mes).split("->");

		System.out.println(Arrays.toString(names));
		if (names.length != 2) {
			irc.sendContextReply(mes, "Your argument must be of the form: 'from -> to'");
		} else {
			final Map<String, String> params = new HashMap<String, String>();
			params.put("game", "0");
			params.put("a", names[0]);
			params.put("b", names[1]);
			final String result = mods.http.performPost("http://oracleofbacon.org/cgi-bin/movielinks", params);

			final Matcher m = p.matcher(result);
			final StringBuilder sb = new StringBuilder();
			while (m.find()) {
				if ("film".equals(m.group(1))) {
					sb.append(" via ");
					sb.append(m.group(2));
					sb.append(" to ");
				} else {
					sb.append(m.group(2));
				}
			}

			if (sb.length() == 0) {
				irc.sendContextReply(mes, "Nothing found, maybe your names are incorrect?");
			} else {
				irc.sendContextReply(mes, sb.toString());
			}

		}

	}
}
