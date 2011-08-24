import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.ObjectDBTransaction;
import uk.co.uwcs.choob.support.events.ChannelAction;
import uk.co.uwcs.choob.support.events.ChannelJoin;
import uk.co.uwcs.choob.support.events.ChannelMessage;
import uk.co.uwcs.choob.support.events.IRCEvent;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.NickChange;
import uk.co.uwcs.choob.support.events.PrivateAction;
import uk.co.uwcs.choob.support.events.PrivateMessage;

class TellObject
{
	public int id;
	public String type;
	public long date;
	public String from;
	public String message;
	public String target;
	public boolean requestDSN;
	public boolean nickServ;
}

// Delivery Service Notifications
class TellDSNObject
{
	public int id;
	public String type; // Type of original tell.
	public long date; // Date of original tell.
	public String from; // Who GOT the tell, i.e. who sent this DSN.
	public String target; // Who asked for the DSN, i.e. the target of this DSN.
	public boolean nickServ;
}

class TellData
{
	boolean valid;
	int error;
	long date;
	String type;
	String from;
	String message;
	String[] targets;
	boolean requestDSN;
}

public class Tell
{
	private static final Logger logger = LoggerFactory.getLogger(Tell.class);

	private static int MAXTARGETS = 7;
	private static long CACHEEXPIRE = 60 * 60 * 1000; // 5 mins

	public String[] info()
	{
		return new String[] {
			"A plugin to allow users to leave each other messages.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private final Modules mods;
	private final IRCInterface irc;

	private final HashMap<String,Long> tellCache;

	public Tell (final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
		this.tellCache = new HashMap<String,Long>();
	}

	public String[] helpTopics = { "Using", "Security", "Cache" };

	public String[] helpUsing = {
		  "Tell is a plugin that allows you to send messages to people who"
		+ " aren't around at the moment. When they next speak, the bot will"
		+ " let them know."
	};

	public String[] helpSecurity = {
		  "Tells currently use NickServ in the following way:",
		  "If the nick you send to's base nickname exists in NickServ (eg."
		+ " you said to 'bob|sleep' and 'bob' is registered), the tell is"
		+ " marked secure. This means that bob can only pick up the message if"
		+ " he is identified with NickServ, on a name that the bot both"
		+ " considers equivalent normally to 'bob' (like, say, 'bob|awake')"
		+ " AND considers securely equivalent, too (ie. is linked to bob).",
		  "You should note that this means, in particular, that people who's"
		+ " root username is not equal to their base nickname can't receive"
		+ " tells at all! That is, if 'bob|bot' is bob's root username, he"
		+ " will never receive tells."
	};

	public String[] helpCache = {
		  "Whether or not you have a tell is cached for " + CACHEEXPIRE / 1000
		+ " seconds. This is to stop tell continually telling you you have"
		+ " tells but aren't identified. If you DO identify with NickServ"
		+ " therefore, you need to use the Tell.Get command to reset this and"
		+ " try again. Note that NickServ status is also cached."
	};

	public synchronized int apiInject(final String from, final String[] targets,
			final String message, final String type) {
		final TellData tell = validateTell(from, targets, message,
				new Date().getTime(), type);
		if (!tell.valid)
			return tell.error;
		return 16 + doTell(tell);
	}

	public String[] helpCommandSend = {
		"Send a tell to the given nickname.",
		"<Nick>[,<Nick>...] <Message>",
		"<Nick> is the target of the tell",
		"<Message> is the content"
	};

	synchronized TellData validateTell(final String from,
			final String[] targets, final String message, final long date,
			final String type) {
		final TellData rv = new TellData();
		rv.valid = true;
		rv.error = 0;
		rv.from = from;
		rv.targets = null;
		rv.message = message;
		rv.date = date;
		rv.type = type;
		rv.requestDSN = false;

		try
		{
			final boolean requestDSN = ((String)mods.plugin.callAPI("Options",
					"GetUserOption", from, "RequestDSN", "0")).equals("1");
			rv.requestDSN = requestDSN;
		}
		catch (final ChoobNoSuchCallException e)
		{
			// Oh dear, no Options. :(
		}

		// Only include unique targets.
		final List<String> validTargets = new ArrayList<String>(MAXTARGETS);
		for (final String target2 : targets)
		{
			final String targetNick = mods.nick.getBestPrimaryNick(mods.security.getUserAuthName(target2));
			final String rootTargetNick = mods.security.getRootUser(targetNick);

			final String target = rootTargetNick != null ? rootTargetNick : targetNick;

			// Make sure we don't dup targets.
			if (validTargets.contains(target))
				continue;

			validTargets.add(target);
		}
		rv.targets = validTargets.toArray(new String[0]);

		// Check we're not going to too many people.
		if (rv.targets.length > MAXTARGETS)
			rv.error = 1;

		if (rv.error != 0)
			rv.valid = false;

		return rv;
	}

	synchronized int doTell(final TellData tell)
	{
		if (!tell.valid)
			return 0;

		final TellObject tellObj = new TellObject();

		// Note: This is intentionally not translated to a primary nick.
		tellObj.from = tell.from;
		tellObj.message = tell.message;
		tellObj.date = tell.date;
		tellObj.type = tell.type;
		tellObj.requestDSN = tell.requestDSN;

		final String[] targets = tell.targets;

		mods.odb.runTransaction(
			new ObjectDBTransaction()
			{
				@Override
				public void run()
				{
					for (final String target : targets)
					{
						tellObj.id = 0;
						tellObj.target = target;
						tellObj.nickServ = checkAuthStatus(tellObj.target) > 0;

						clearCache(tellObj.target);
						save(tellObj);
					}
				}
			}
		);

		// Send e-mails to people who've asked for them.
		try
		{
			for (final String target : targets)
			{
				final String email = (String)mods.plugin.callAPI("Options",
						"GetUserOption", target, "Email", "");
				if (email.length() > 0)
				{
					mods.plugin.callAPI("Mail", "SendMail", email,
							"Tell from " + tellObj.from + " via " + irc.getNickname(),
							"At " + new Date(tellObj.date) + ", " + tellObj.from +
							" told me to " + tellObj.type + " you: " + tellObj.message);
				}
			}

		}
		catch (final ChoobNoSuchCallException e)
		{
			// Oh dear, no Options. :(
		}

		return targets.length;

	}

	int checkTargetSecureStatus(final int authStatus, final String nick,
			final String rootNick) {
		// First pick up the setting of Secure.
		int secureOption;
		try {
			final String val = (String)mods.plugin.callAPI("Options", "GetUserOption",
					nick, "Secure", "1" );
			secureOption = Integer.parseInt(val);
		} catch (final Throwable e) {
			// No such call(default) or number format issue(!)
			secureOption = 1;
		}
		if (secureOption > 2 || secureOption < 0)
			secureOption = 1;

		// This is a secure tell. One of several things can happen.
		if ( secureOption == 2 ) {
			// If secure tell is set, we require the
			// actual nickname to be explicitly linked
			// to the root.
			final String secureRootNick = mods.security.getRootUser(
					mods.security.getUserAuthName(nick));
			if (rootNick != null) {
				// rootNick is set and we're directed at it.
				// Hence must check root of real nick is
				// equal to rootNick.
				if ( !rootNick.equalsIgnoreCase(secureRootNick) )
					return -2;
			} else {
				// rootNick is NOT set. Since Secure
				// operates on bot users, and the user
				// hasn't registered his, we tell him to
				// bugger off.
				return -3;
			}
		}
		// If not, just the primary will do.

		if (authStatus == -1) {
			// No errors from the above...
			if (secureOption != 0) {
				// We require auth.
				return checkAuthStatus(nick);
			}
			// We don't require NS auth.
			return 3;
		}

		return authStatus; // No change.
	}

	public synchronized void commandSend( final Message mes )
	{
		final String[] params = mods.util.getParamArray(mes, 2);
		if (params.length <= 2)
		{
			irc.sendContextReply(mes, "Syntax: 'Tell.Send " + helpCommandSend[1] + "'");
			return;
		}

		final Map<String,String> mesFlags = ((IRCEvent)mes).getFlags();

		String typeLocal = "tell";
		// Allow any alias to this command to be used as the verb.
		// (Except for when using tell.* directly.)
		if (mesFlags.containsKey("command"))
		{
			if (!mesFlags.get("command").toLowerCase().startsWith("tell.") &&
			    mesFlags.get("command").matches("\\w+"))
			{
				typeLocal = mesFlags.get("command");
			}
		}

		// It's a question if it has a "?" within 5 characters of it's end. (ie. "? :)").
		if (typeLocal.equals("tell") && params[2].indexOf('?', params[2].length() - 5) != -1)
			typeLocal = "ask";


		final String type = typeLocal;
		final String[] targets = params[1].split(", *");
		final long time = mes.getMillis();

		final TellData tell = validateTell(mes.getNick(), targets, params[2], time, type);

		if (!tell.valid)
		{
			switch (tell.error)
			{
				case 1:
					irc.sendContextReply(mes,
							"Sorry, you're only allowed " + MAXTARGETS +
							" targets for a given tell.");
					return;
				default:
					irc.sendContextReply(mes,
							"Unknown tell error code: " + tell.error);
					return;
			}
		}

		final int count = tell.targets.length;
		if (!mesFlags.containsKey("timedevents.delayed"))
		{
			irc.sendContextReply(mes,
					"Okay, will " + type + " upon next speaking. (Sent to " +
					count + " " + (count == 1 ? "person" : "people") + ".)");
		}

		doTell(tell);
	}

	public String[] helpCommandGet = {
		"Get any tells that have been sent to you. See Cache."
	};
	public void commandGet( final Message mes )
	{
		clearCache(mes.getNick());
		loudspew( mes.getNick() );
		irc.sendContextReply(mes, "OK, if you had any tells, I just sent 'em. :)");
	}

	private void clearCache( final String nick )
	{
		final String pnick = mods.nick.getBestPrimaryNick(nick);
		String rnick = mods.security.getRootUser(pnick);
		if (rnick == null)
			rnick = pnick;

		synchronized(tellCache)
		{
			final Iterator<String> iter = tellCache.keySet().iterator();
			while(iter.hasNext())
			{
				final String item = iter.next();
				final String pitem = mods.nick.getBestPrimaryNick(item);
				String ritem = mods.security.getRootUser(pitem);
				if (ritem == null)
					ritem = pitem;

				if (ritem.equalsIgnoreCase(rnick))
					iter.remove();
			}
		}
	}

	public String[] optionsUser = { "Secure", "NickChange", "Email", "RequestDSN" };
	public String[] optionsUserDefaults = { "1", "1", "", "0" };

	public boolean optionCheckUserSecure( final String value, final String userName ) {
		return value.equals("0") || value.equals("1") || value.equals("2");
	}

	public String[] helpOptionSecure = {
		"Choose the security level of your tells.",
		"Set this to \"0\" to not have secure tells (no NickServ required), " +
		"\"1\" to make them require NickServ, or \"2\" to make them require " +
		"both NickServ and that your nicknames are linked in the bot."
	};

	public boolean optionCheckUserNickChange( final String value, final String userName ) {
		return value.equals("0") || value.equals("1");
	}

	public String[] helpOptionNickChange = {
		"Choose to have tells delivered on nick change.",
		"Set this to \"0\" to not have tells delivered on nick change, " +
		"default, \"1\", will deliver on nick change."
	};

	public boolean optionCheckUserEmail( final String value, final String userName ) { return true; }
	public String[] helpOptionEmail = {
		"Choose to have a copy of all tells e-mailed to you.",
		"Set this to an e-mail address to have copies of all tells sent to " +
		"you mailed. Set to '' to stop."
	};

	private synchronized void spew (final String nick)
	{
		try
		{
			loudspew(nick);
		}
		catch (final Exception e)
		{
			logger.error("Tell.spew suppressed error:");
			e.printStackTrace();
		}
	}

	private synchronized void loudspew( final String nick )
	{
		// Use the cache
		boolean willSkip = false;
		synchronized(tellCache)
		{
			final Long cache = tellCache.get(nick);
			if (cache != null && cache > System.currentTimeMillis())
				willSkip = true;
			tellCache.put(nick, System.currentTimeMillis() + CACHEEXPIRE);
		}
		if (willSkip)
			return;

		// getBestPrimaryNick should be safe from injection
		final String testNick = mods.nick.getBestPrimaryNick(
				mods.security.getUserAuthName(nick));
		// rootNick won't, necessarily
		final String rootNick = mods.security.getRootUser( testNick );

		List<TellObject> tellResults;
		List<TellDSNObject> dsnResults;

		if (rootNick != null && !rootNick.equals(testNick))
		{
			tellResults = mods.odb.retrieve(TellObject.class,
					"WHERE target = '" + mods.odb.escapeString(testNick) +
					"' OR target = '" + mods.odb.escapeString(rootNick) + "'");
			dsnResults  = mods.odb.retrieve(TellDSNObject.class,
					"WHERE target = '" + mods.odb.escapeString(testNick) +
					"' OR target = '" + mods.odb.escapeString(rootNick) + "'");
		}
		else
		{
			tellResults = mods.odb.retrieve(TellObject.class,
					"WHERE target = '" + mods.odb.escapeString(testNick) + "'");
			dsnResults  = mods.odb.retrieve(TellDSNObject.class,
					"WHERE target = '" + mods.odb.escapeString(testNick) + "'");
		}

		if (tellResults.size() == 0 && dsnResults.size() == 0)
			return;

		final Comparator<TellObject> tellSorter = new Comparator<TellObject>()
		{
			@Override
			public int compare(final TellObject l, final TellObject r)
			{
				return new Date(l.date).compareTo(new Date(r.date));
			}
		};

		// Grr, Java you suck, wtb real templates.
		final Comparator<TellDSNObject> tellDSNSorter = new Comparator<TellDSNObject>()
		{
			@Override
			public int compare(final TellDSNObject l, final TellDSNObject r)
			{
				return new Date(l.date).compareTo(new Date(r.date));
			}
		};

		Collections.sort(tellResults, tellSorter);
		Collections.sort(dsnResults, tellDSNSorter);

		// We do DSNs first because they are less important. Or something.
		int authStatus = -1;
		for (final TellDSNObject dsnObj : dsnResults)
		{
			if (dsnObj.nickServ)
			{
				if (authStatus == -1)
					// NickServ not yet checked...
					authStatus = checkTargetSecureStatus(authStatus, nick, rootNick);

				// If all the above ran and we're allowed to send, nsStatus
				// is 3. Otherwise it's >= -1, <= 2.
				if (authStatus != 3)
					continue;
			}

			// Only send the notification if the person who requested the tell is not the recipient
			if (!dsnObj.target.equals(dsnObj.from)) {
				irc.sendMessage(nick,
				                "Your " + dsnObj.type + " sent to " + dsnObj.from + " at " +
				                new Date(dsnObj.date) + " has been delivered.");
			}
			mods.odb.delete(dsnObj);
		}

		for (final TellObject tellObj : tellResults)
		{
			if (tellObj.nickServ)
			{
				if (authStatus == -1)
					// NickServ not yet checked...
					authStatus = checkTargetSecureStatus(authStatus, nick, rootNick);

				// If all the above ran and we're allowed to send, nsStatus
				// is 3. Otherwise it's >= -1, <= 2.
				if (authStatus != 3)
					continue;
			}
			irc.sendMessage(nick,
					"At " + new Date(tellObj.date) + ", " + tellObj.from +
					" told me to " + tellObj.type + " you: " + tellObj.message);

			// Only send a delivery response if the target isn't the recipient.
			if (tellObj.requestDSN && (!tellObj.target.equals(tellObj.from)))
			{
				final TellDSNObject dsn = new TellDSNObject();
				dsn.type = tellObj.type;
				dsn.date = tellObj.date;
				// These go backwards - we're sending this back.
				dsn.from = tellObj.target;
				dsn.target = tellObj.from;
				dsn.nickServ = checkAuthStatus(dsn.target) > 0;
				clearCache(dsn.target);
				mods.odb.save(dsn);
			}

			mods.odb.delete(tellObj);
		}
		if (authStatus == -2)
			irc.sendMessage(nick,
					"Hi! I think you have tells, and you have set Secure=2, " +
					"but your nickname isn't linked to " + rootNick + ". See " +
					"Help.Help Security.UsingLink to do this, then do Tell.Get.");
		else if (authStatus == -3)
			irc.sendMessage(nick,
					"Hi! I think you have tells, and you have set Secure=2, " +
					"but you haven't actually registered " + testNick +
					" with the bot. Since this defeats the point of secure " +
					"tells, I suggest you register it (Security.AddUser), " +
					"then link this nickname to it (See Help.Help Security.UsingLink).");
		else if (authStatus == 0)
			irc.sendMessage(nick,
					"Hi! I think you (" + testNick + ") have tells, but you " +
					"haven't actually registered your nickname with NickServ. " +
					"Since " + testNick + " was registered and you haven't set " +
					"Secure=0, you need to register with this nickname or " +
					"change to " + testNick + " to pick up your tells.");
		else if (authStatus > 0 && authStatus < 3)
			irc.sendMessage(nick,
					"Hi! You have tells, but you're not identified with " +
					"NickServ! Once you've done so, use the Tell.Get command.");
	}

	public void onAction( final ChannelAction ev )
	{
		if (ev.getSynthLevel() > 0)
			return;
		spew(ev.getNick());
	}

	public void onMessage( final ChannelMessage ev )
	{
		if (ev.getSynthLevel() > 0)
			return;
		spew(ev.getNick());
	}

	public void onPrivateMessage( final PrivateMessage ev )
	{
		if (ev.getSynthLevel() > 0)
			return;
		spew(ev.getNick());
	}

	public void onPrivateAction( final PrivateAction ev )
	{
		if (ev.getSynthLevel() > 0)
			return;
		spew(ev.getNick());
	}

	public void onJoin( final ChannelJoin ev )
	{
		spew(ev.getNick());
	}

	public void onNickChange( final NickChange ev )
	{
		try
		{
			if (((String)mods.plugin.callAPI("Options", "GetUserOption",
					ev.getNewNick(), "NickChange", "1" )).equals("0"))
				return;
		}
		catch (final ChoobNoSuchCallException e)
		{} // Non-issue.

		try
		{
			Thread.sleep(1000);
		}
		catch (final InterruptedException e)
		{
			// Bah, who cares? :)
		}
		spew(ev.getNewNick());
	}

	private int checkAuthStatus( final String nick ) {
		if (mods.security.hasAuth(nick)) {
			return 3;
		}
		return -1;
	}
}

