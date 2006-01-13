//import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
//import java.security.*;
import java.util.regex.*;

public class UserTypeCheckResult
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
	private Modules mods;
	private IRCInterface irc;
	private Map<String,UserTypeCheckResult> userChecks;
	private int[] statsCalled;
	private int[] statsWhoisd;
	private int[] statsFailed;
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
	private final int USER_TYPE_FLAG__MIN = USER_TYPE_FLAG_BOT;
	private final int USER_TYPE_FLAG__MAX = USER_TYPE_FLAG_SECURE;
	
	/* Return values from the API "check" */
	private final int USER_TYPE_RV_ERROR = -1;
	private final int USER_TYPE_RV_NO    =  0;
	private final int USER_TYPE_RV_YES   =  1;
	
	private final Pattern SplitWhoisLine = Pattern.compile("^([^ ]+) ([^ ]+) (.*)$");
	
	public UserTypeCheck(Modules mods, IRCInterface irc)
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
	public void destroy(Modules mods)
	{
		// Notify all threads blocked on user queries. This allows anything
		// that was asking about a user to continue (and fail) rather than be
		// stuck for all eternity.
		synchronized(userChecks)
		{
			Iterator<String> user = userChecks.keySet().iterator();
			while(user.hasNext()) {
				UserTypeCheckResult entry = userChecks.get(user.next());
				//FIXME: synchronized?//
				entry.notifyAll();
			}
		}
	}
	
	public synchronized void interval(Object parameter, Modules mods, IRCInterface irc)
	{
		synchronized(userChecks)
		{
			Iterator<String> user = userChecks.keySet().iterator();
			String nick;
			while(user.hasNext()) {
				// We want to remove any items that have expired, but leave
				// those still in progress.
				nick = user.next();
				UserTypeCheckResult entry = userChecks.get(nick);
				if (entry.hasChecked && (System.currentTimeMillis() > entry.timestamp + USER_DATA_TIMEOUT)) {
					System.out.println("UTC: Data for user <" + nick + "> has expired.");
					userChecks.remove(nick);
					// Restart iterator, otherwise it gets all touchy.
					user = userChecks.keySet().iterator();
				}
			}
		}
		
		// Update stats.
		try {
			int newHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
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
				System.out.println("UTC: Average API usage            : " + (totalC / STATS_COUNT) + "/hour");
				System.out.println("UTC: Average WHOIS commands issued: " + (totalW / STATS_COUNT) + "/hour");
				System.out.println("UTC: Average failed requests      : " + (totalF / STATS_COUNT) + "/hour");
				
				statsIndex = (statsIndex + 1) % STATS_COUNT;
				statsCalled[statsIndex] = 0;
				statsWhoisd[statsIndex] = 0;
				statsFailed[statsIndex] = 0;
			}
		} catch (Exception e) {
			System.err.println("UTC ERROR: " + e);
		}
		
		mods.interval.callBack(null, USER_DATA_INTERVAL);
	}
	
	public String[] helpCommandCheck = {
		"Displays some cached status information about a user.",
		"[<nickname>]",
		"<nickname> is an optional nick to check"
	};
	public void commandCheck(Message mes)
	{
		String nick = mods.util.getParamString(mes);
		if (nick.length() == 0)
			nick = mes.getNick();
		
		int Bot = apiStatus(nick, "bot");
		int Away = apiStatus(nick, "away");
		int IRCop = apiStatus(nick, "ircop");
		int Reg = apiStatus(nick, "registered");
		int SSL = apiStatus(nick, "secure");
		
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
	public int apiStatus(String nick, String flag)
	{
		statsCalled[statsIndex]++;
		String nickl = nick.toLowerCase();
		String flagl = flag.toLowerCase();
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
	
	public void onServerResponse(ServerResponse ev)
	{
		// Check for a code we care about first. If we don't care about the
		// code, we can bail right here. This saves on using the RegExp, and
		// the |synchronized| stuff below.
		int code = ev.getCode();
		if ((code != 301) && (code != 307) && (code != 313)
				&& (code != 318) && (code != 335) && (code != 671)) {
			return;
		}
		
		// ^([^ ]+) ([^ ]+) (.*)$
		Matcher sp = SplitWhoisLine.matcher(ev.getResponse());
		if (!sp.matches())
			return;
		
		String nickl = sp.group(2).toLowerCase();
		
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
				System.out.println("UTC: Data for user <" + nickl +
						 ">: bot("   + (new Boolean(userData.isBot)).toString() + 
						"); away(" + (new Boolean(userData.isAway)).toString() + 
						"); ircop(" + (new Boolean(userData.isOperator)).toString() + 
						"); reg("   + (new Boolean(userData.isRegistered)).toString() + 
						"); ssl("   + (new Boolean(userData.isSecure)).toString() + 
						").");
				userData.notifyAll();
			}
		}
	}
	
	private int getStatus(String nick, int flag)
	{
		UserTypeCheckResult userData = getUserData(nick);
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
	
	private UserTypeCheckResult getUserData(String nick)
	{
		UserTypeCheckResult data;
		synchronized(userChecks) {
			data = userChecks.get(nick);
			
			if (data != null) {
				if (!USER_DATA_CACHE_BLOCK && !data.hasChecked) {
					statsFailed[statsIndex]++;
					System.out.println("UTC: Check (cached) for user <" + nick + "> has no data!");
					return null;
				}
				synchronized(data) {
					if (!data.hasChecked) {
						statsFailed[statsIndex]++;
						System.out.println("UTC: Check (cached) for user <" + nick + "> FAILED!");
						return null;
					}
					return data;
				}
			}
			
			// Create new data thing and send query off.
			data = new UserTypeCheckResult();
			data.hasChecked = false;
			userChecks.put(nick, data);
		}
		statsWhoisd[statsIndex]++;
		irc.sendRawLine("WHOIS " + nick);
		
		synchronized(data) {
			try {
				data.wait(USER_DATA_WAIT);
			} catch (InterruptedException e) {
				// Do nothing, as data.hasChecked will be false anyway.
			}
			if (!data.hasChecked) {
				statsFailed[statsIndex]++;
				System.out.println("UTC: Check (live) for user <" + nick + "> FAILED!");
				return null;
			}
			return data;
		}
	}
	
	private int mapBooleanToCheckRV(boolean in)
	{
		if (in)
			return USER_TYPE_RV_YES;
		return USER_TYPE_RV_NO;
	}
	
	private String mapRVToString(int rv)
	{
		switch (rv) {
			case USER_TYPE_RV_YES: return "yes";
			case USER_TYPE_RV_NO: return "no";
			default: return "error";
		}
	}
}
