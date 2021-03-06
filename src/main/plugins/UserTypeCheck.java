//import uk.co.uwcs.choob.*;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.ServerResponse;

class UserTypeCheckResult
{
	boolean hasChecked;
	boolean isBot;
	boolean isAway;
	boolean isOperator;
	boolean isRegistered;
	boolean isSecure;
	long timestamp;
}

public class UserTypeCheck
{
	private final Modules mods;
	private final IRCInterface irc;
	private final Map<String,UserTypeCheckResult> userChecks;
	private final int[] statsCalled;
	private final int[] statsWhoisd;
	private final int[] statsFailed;
	private int statsIndex;
	private int statsLastHour;

	private final int STATS_COUNT = 24;

	/* Time that the API will wait for data to arrive */
	private final int USER_DATA_WAIT = 10000; // 10 seconds
	/* If true, cached requests will block until the live request times out */
	private final boolean USER_DATA_CACHE_BLOCK = true;
	/* The time between checks for expired cache items. */
	private final int USER_DATA_INTERVAL = 30000; // 30 seconds
	/* the time for which an entry is cached. */
	private final int USER_DATA_TIMEOUT = USER_DATA_INTERVAL * 10; // 5 minutes

	/* Different flag types... */
	private final int USER_TYPE_FLAG_BOT         = 1;
	private final int USER_TYPE_FLAG_AWAY        = 2;
	private final int USER_TYPE_FLAG_IRCOP       = 3;
	private final int USER_TYPE_FLAG_REGISTERED  = 4;
	private final int USER_TYPE_FLAG_SECURE      = 5;

	/* Return values from the API "check" */
	private final int USER_TYPE_RV_ERROR = -1;
	private final int USER_TYPE_RV_NO    =  0;
	private final int USER_TYPE_RV_YES   =  1;

	private final Pattern SplitWhoisLine = Pattern.compile("^([^ ]+) ([^ ]+) (.*)$");

	public UserTypeCheck(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;

		userChecks = new HashMap<String,UserTypeCheckResult>();
		statsIndex = 0;
		statsLastHour = -1;
		statsCalled = new int[STATS_COUNT];
		statsWhoisd = new int[STATS_COUNT];
		statsFailed = new int[STATS_COUNT];

		mods.interval.callBack(null, 1);
	}

	public String[] info()
	{
		return new String[] {
			"Plugin for getting and caching type info (is bot, is ircop, is registered, etc.), about a user",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	/* This is never called. WTF? */
	public void destroy()
	{
		// Notify all threads blocked on user queries. This allows anything
		// that was asking about a user to continue (and fail) rather than be
		// stuck for all eternity.
		synchronized(userChecks)
		{
			final Iterator<String> user = userChecks.keySet().iterator();
			while(user.hasNext()) {
				final UserTypeCheckResult entry = userChecks.get(user.next());
				//FIXME: synchronized?//
				entry.notifyAll();
			}
		}
	}

	public synchronized void interval(final Object parameter)
	{
		synchronized(userChecks)
		{
			Iterator<String> user = userChecks.keySet().iterator();
			String nick;
			while(user.hasNext()) {
				// We want to remove any items that have expired, but leave
				// those still in progress.
				nick = user.next();
				final UserTypeCheckResult entry = userChecks.get(nick);
				if (System.currentTimeMillis() > entry.timestamp + USER_DATA_TIMEOUT) {
					userChecks.remove(nick);
					// Restart iterator, otherwise it gets all touchy.
					user = userChecks.keySet().iterator();
				}
			}
		}

		// Update stats.
		final int newHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
		if (statsLastHour != newHour) {
			statsLastHour = newHour;

			double totalC = 0;
			double totalW = 0;
			double totalF = 0;
			for (int i = 0; i < STATS_COUNT; i++) {
				totalC += statsCalled[i];
				totalW += statsWhoisd[i];
				totalF += statsFailed[i];
			}
			System.out.println("UTC: Average API usage            : " + totalC / STATS_COUNT + "/hour");
			System.out.println("UTC: Average WHOIS commands issued: " + totalW / STATS_COUNT + "/hour");
			System.out.println("UTC: Average failed requests      : " + totalF / STATS_COUNT + "/hour");

			statsIndex = (statsIndex + 1) % STATS_COUNT;
			statsCalled[statsIndex] = 0;
			statsWhoisd[statsIndex] = 0;
			statsFailed[statsIndex] = 0;
		}

		mods.interval.callBack(null, USER_DATA_INTERVAL);
	}

	public String[] helpCommandCheck = {
		"Displays some cached status information about a user.",
		"[<nickname>]",
		"<nickname> is an optional nick to check"
	};
	public void commandCheck(final Message mes)
	{
		String nick = mods.util.getParamString(mes);
		if (nick.length() == 0)
			nick = mes.getNick();

		final int Bot = apiStatus(nick, "bot");
		final int Away = apiStatus(nick, "away");
		final int IRCop = apiStatus(nick, "ircop");
		final int Reg = apiStatus(nick, "registered");
		final int SSL = apiStatus(nick, "secure");

		irc.sendContextReply(mes, "Status for " + nick +
				": bot = " + mapRVToString(Bot) +
				"; away = " + mapRVToString(Away) +
				"; ircop = " + mapRVToString(IRCop) +
				"; registered = " + mapRVToString(Reg) +
				"; secure = " + mapRVToString(SSL) +
				".");
	}

	/*
	 * Checks the status of the nickname and returns the status of the specified
	 * flag. This bblocks the caller if a client-server check is needed for this
	 * user (which is then cached). The block times out after 10 seconds, at
	 * which point the error value is returned (-1).
	 *
	 * @param nick The nickname to check the status of.
	 * @param flag The flag to check. May be one of: "bot" (user is marked as a bot),
	 *        "away" (marked away), "ircop" (IRC operator/network service),
	 *        "registered" (registered and identified with NickServ) or
	 *        "secure" (using a secure [SSL] connection).
	 * @return 1 meaning the flag is true/set for the user, 0 if it is false/not
	 *         set and -1 if an error occurs.
	 */
	public int apiStatus(final String nick, final String flag)
	{
		statsCalled[statsIndex]++;
		final String nickl = nick.toLowerCase();
		final String flagl = flag.toLowerCase();
		if (flagl.equals("bot"))
			return getStatus(nickl, USER_TYPE_FLAG_BOT);
		if (flagl.equals("away"))
			return getStatus(nickl, USER_TYPE_FLAG_AWAY);
		if (flagl.equals("ircop"))
			return getStatus(nickl, USER_TYPE_FLAG_IRCOP);
		if (flagl.equals("registered"))
			return getStatus(nickl, USER_TYPE_FLAG_REGISTERED);
		if (flagl.equals("secure"))
			return getStatus(nickl, USER_TYPE_FLAG_SECURE);
		return USER_TYPE_RV_ERROR;
	}

	public void onServerResponse(final ServerResponse ev)
	{
		// Check for a code we care about first. If we don't care about the
		// code, we can bail right here. This saves on using the RegExp, and
		// the |synchronized| stuff below.
		final int code = ev.getCode();
		if (code != 301 && code != 307 && code != 313
				&& code != 318 && code != 335 && code != 671) {
			return;
		}

		// ^([^ ]+) ([^ ]+) (.*)$
		final Matcher sp = SplitWhoisLine.matcher(ev.getResponse());
		if (!sp.matches())
			return;

		final String nickl = sp.group(2).toLowerCase();

		UserTypeCheckResult userData;
		synchronized(userChecks) {
			userData = userChecks.get(nickl);
		}
		if (userData == null) {
			return;
		}

		synchronized(userData) {
			if (code == 335) {
				userData.isBot = true;
			}
			if (code == 301) {
				userData.isAway = true;
			}
			if (code == 313) {
				userData.isOperator = true;
			}
			if (code == 307) {
				userData.isRegistered = true;
			}
			if (code == 671) {
				userData.isSecure = true;
			}
			if (code == 318) {
				userData.timestamp = System.currentTimeMillis();
				userData.hasChecked = true;
				userData.notifyAll();
			}
		}
	}

	private int getStatus(final String nick, final int flag)
	{
		final UserTypeCheckResult userData = getUserData(nick);
		if (userData == null) {
			return USER_TYPE_RV_ERROR;
		}

		switch(flag) {
			case USER_TYPE_FLAG_BOT:
				return mapBooleanToCheckRV(userData.isBot);

			case USER_TYPE_FLAG_AWAY:
				return mapBooleanToCheckRV(userData.isAway);

			case USER_TYPE_FLAG_IRCOP:
				return mapBooleanToCheckRV(userData.isOperator);

			case USER_TYPE_FLAG_REGISTERED:
				return mapBooleanToCheckRV(userData.isRegistered);

			case USER_TYPE_FLAG_SECURE:
				return mapBooleanToCheckRV(userData.isSecure);
		}
		return USER_TYPE_RV_ERROR;
	}

	private UserTypeCheckResult getUserData(final String nick)
	{
		UserTypeCheckResult data;
		synchronized(userChecks) {
			data = userChecks.get(nick);

			if (data != null) {
				if (!USER_DATA_CACHE_BLOCK && !data.hasChecked) {
					statsFailed[statsIndex]++;
					return null;
				}
				synchronized(data) {
					if (!data.hasChecked) {
						statsFailed[statsIndex]++;
						return null;
					}
					return data;
				}
			}

			// Create new data thing and send query off.
			data = new UserTypeCheckResult();
			data.hasChecked = false;
			data.timestamp = System.currentTimeMillis();
			userChecks.put(nick, data);
		}
		statsWhoisd[statsIndex]++;
		irc.sendRawLine("WHOIS " + nick);

		synchronized(data) {
			try {
				data.wait(USER_DATA_WAIT);
			} catch (final InterruptedException e) {
				// Do nothing, as data.hasChecked will be false anyway.
			}
			if (!data.hasChecked) {
				statsFailed[statsIndex]++;
				return null;
			}
			return data;
		}
	}

	private int mapBooleanToCheckRV(final boolean in)
	{
		if (in)
			return USER_TYPE_RV_YES;
		return USER_TYPE_RV_NO;
	}

	private String mapRVToString(final int rv)
	{
		switch (rv) {
			case USER_TYPE_RV_YES: return "yes";
			case USER_TYPE_RV_NO: return "no";
			default: return "error";
		}
	}
}
