/** @author rlmw */

import java.security.AllPermission;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.BasicAuthorization;
import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.plugins.RequiresPermission;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

/**
 * Client for Twitter implemented in choob
 *
 * Issue - sends people tells if they're online but not in a common channel with choob.
 *
 * @author rlmw
 * @see http://www.twitter.com
 * @see http://twitter4j.org/en/index.html
 */
// See hilarious end of file
@RequiresPermission(AllPermission.class)
public class Tweet {

	public String[] info()
	{
		return new String[] {
			"Twitter Client implemented in choob",
			"mulletron",
			"ALPHA ALPHA",
			"<3",
		};
	}

	public final String[] helpTopics = { "Using" };

	public final String[] helpUsing = {
		  "1: !Tweet.login <username> <password> to login",
		  "2: !Tweet.update <status> to change status",
		  "3: Sit on irc (preferrably nude), being pm'd tweets",
	};

	private final Modules mods;
	private final IRCInterface irc;
	private final Map<String,Twitter> loginCache;

	// 150 requests per hour MAX
	private final int _5_MINUTES = 5*60*1000;

	public Tweet(final Modules mods, final IRCInterface irc) throws TwitterException {
		super();
		this.mods = mods;
		this.irc = irc;
		this.loginCache = new HashMap<String, Twitter>();

		mods.interval.callBack(null, _5_MINUTES);
	}

	private void tell(final String nick, final String msg) {
		try {
			int ret = (Integer)mods.plugin.callAPI("Tell","Inject",irc.getNickname(), new String[]{nick}, msg,"tell");
			System.out.println(ret);
		} catch (ChoobNoSuchCallException e) {
			e.printStackTrace();
		}
	}

	public void interval(final Object param) {
		// Get Updates
		long id = (param == null) ? 1 : (Long) param;
		Paging p = new Paging(id);

		for(Map.Entry<String, Twitter> entry:loginCache.entrySet()) {
			final Twitter twitter = entry.getValue();
			final String username = entry.getKey();
			final boolean known = irc.isKnownUser(username);
			try {
				final ResponseList<Status> timeline = twitter.getHomeTimeline(p);
				Collections.sort(timeline, new Comparator<Status>() {
					@Override
					public int compare(Status o1, Status o2) {
						return o1.getCreatedAt().compareTo(o2.getCreatedAt());
					}
				});
				for(Status status:timeline) {
					id = Math.max(status.getId(),id);
					final User user = status.getUser();
					final String from = user.getName();
					if(known) {
						irc.sendMessage(username,  from + " ("+ user.getScreenName() + ") : "+status.getText());
					} else {
						tell(username,status.getText());
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		mods.interval.callBack(new Long(id), _5_MINUTES);
	}

	/**
	 * Logs user in
	 */
	public void commandLogout(final Message mes) {

		final String nick = mes.getNick();

		final Twitter remove = loginCache.remove(nick);
		if(remove == null) {
			irc.sendContextReply(mes, "You aren't logged in");
		} else {
			irc.sendContextReply(mes, "Logged out");
		}
	}

	public void commandLogin(final Message mes) {

		final List<String> params = mods.util.getParams(mes);
		final String nick = mes.getNick();

		if(params.size() != 3) {
			irc.sendContextReply(mes, "This requires two arguments - a username and a password, received: " + params);
			return;
		}


		loginCache.put(nick, new TwitterFactory().getInstance(new BasicAuthorization(params.get(1), params.get(2))));
		irc.sendContextReply(mes, "Saved "+nick+"'s login");
	}

	public void commandUpdate(final Message mes) {
		final String nick = mes.getNick();
		final Twitter twitter = loginCache.get(nick);
		if(twitter == null) {
			irc.sendContextReply(mes, "You aren't logged in, see !help TwitterClient.Using");
			return;
		}

		final String status = mods.util.getParamString(mes);
		try {
			twitter.updateStatus(status);
			irc.sendContextReply(mes,"Status Updated");
		} catch (TwitterException e) {
			e.printStackTrace();
			irc.sendContextReply(mes,"Internal Problem: "+e.getMessage());
		}
	}

}

/**
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.dalvik read).
Plugin Tweet lacks permission (java.io.FilePermission ./twitter4j.properties read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.debug read).
Plugin Tweet lacks permission (java.util.PropertyPermission java.specification.version read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.retryCount read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.retryIntervalSecs read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.user read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.password read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.proxyHost read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.proxyPort read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.proxyUser read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.proxyPassword read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.connectionTimeout read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.readTimeout read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.useSSL read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.useSSL read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.useSSL read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.useSSL read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.userAgent read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.oauth.consumerKey read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.oauth.consumerSecret read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.source read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.useSSL read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.clientVersion read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.clientURL read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.user read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.password read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.useSSL read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.useSSL read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.http.useSSL read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.user read).
Plugin Tweet lacks permission (java.util.PropertyPermission twitter4j.password read).

CBA!!!!!!!!!!!

 */