import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;

// Note: This send/watch couple will break if someone changes their primary nick between the send and the receive, assuming they change their base nick.. it could be done otherwise, but Faux can't think of a way that doesn't involve mass database rapeage on every line sent by irc.
// This entire plugin could do with some caching.

public class TellObject
{
	public int id;
	public String type;
	public long date;
	public String from;
	public String message;
	public String target;
	public boolean nickServ;
}

public class Tell
{
	private static int MAXTARGETS = 7;
	private static long CACHEEXPIRE = 60 * 60 * 1000; // 5 mins

	public String[] info()
	{
		return new String[] {
			"A plugin to allow users to leave each other messages.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			mods.util.getVersion()
		};
	}

	private Modules mods;
	private IRCInterface irc;

	private HashMap<String,Long> tellCache;

	public Tell (Modules mods, IRCInterface irc)
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
		  "Whether or not you have a tell is cached for " + (CACHEEXPIRE / 1000)
		+ " seconds. This is to stop tell continually telling you you have"
		+ " tells but aren't identified. If you DO identify with NickServ"
		+ " therefore, you need to use the Tell.Get command to reset this and"
		+ " try again. Note that NickServ status is also cached."
	};

	public String[] helpCommandSend = {
		"Send a tell to the given nickname.",
		"<Nick>[,<Nick>...] <Message>",
		"<Nick> is the target of the tell",
		"<Message> is the content"
	};

	public synchronized void commandSend( Message mes )
	{
		List<String> params = mods.util.getParams(mes, 2);
		if (params.size() <= 2)
		{
			irc.sendContextReply(mes, "Syntax: 'Tell.Send " + helpCommandSend[1] + "'");
			return;
		}

		final TellObject tellObj = new TellObject();

		// Note: This is intentionally not translated to a primary nick.
		tellObj.from = mes.getNick();

		tellObj.message = params.get(2); // 'Message'.

		tellObj.date = mes.getMillis();

		System.out.println("Command: " + params.get(0));
		if (params.get(0).toLowerCase().equals("ask"))
			tellObj.type = "ask";
		else
			tellObj.type = "tell";

		final String[] targets = params.get(1).split(",");

		if (targets.length > MAXTARGETS)
		{
			irc.sendContextReply(mes, "Sorry, you're only allowed " + MAXTARGETS + " targets for a given tell.");
			return;
		}

		final List<String> done = new ArrayList<String>(MAXTARGETS);

		// Yeah, I don't really understand vim's indenting here either.
		mods.odb.runTransaction(
				new ObjectDBTransaction()
				{
					public void run()
		{
			done.clear();
			for(int i=0; i<targets.length; i++)
		{
			tellObj.id = 0;

			String targetNick = mods.nick.getBestPrimaryNick(targets[i]);
			String rootTargetNick = mods.security.getRootUser(targetNick);

			tellObj.target = rootTargetNick != null ? rootTargetNick : targetNick;

			// Make sure we don't dup targets.
			if (done.contains(tellObj.target))
				continue;

			tellObj.nickServ = nsStatus(tellObj.target) > 0;

			clearCache(tellObj.target);
			save(tellObj);
			done.add(tellObj.target);
		}
		}
		});

		irc.sendContextReply(mes, "Okay, will " + tellObj.type + " upon next speaking. (Sent to " + done.size() + " " + (done.size() == 1 ? "person" : "people") + ".)");
	}

	public String[] helpCommandGet = {
		"Get any tells that have been sent to you. See Cache."
	};
	public void commandGet( Message mes )
	{
		clearCache(mes.getNick());
		loudspew( mes.getNick() );
		irc.sendContextReply(mes, "OK, if you had any tells, I just sent 'em. :)");
	}

	private void clearCache( String nick )
	{
		synchronized(tellCache)
		{
			Iterator<String> iter = tellCache.keySet().iterator();
			while(iter.hasNext())
			{
				if (iter.next().equalsIgnoreCase(nick))
					iter.remove();
			}
		}
	}

	public String[] optionsUser = { "Secure", "Insecure" };
	public String[] optionsUserDefaults = { "0", "0" };
	public boolean optionCheckUserSecure( String value, String userName ) { return value.equals("1") || value.equals("0"); }
	public boolean optionCheckUserInsecure( String value, String userName ) { return value.equals("1") || value.equals("0"); }

	private synchronized void spew (String nick)
	{
		try
		{
			loudspew(nick);
		}
		catch (Exception e)
		{
			System.err.println("Tell.spew suppressed error:");
			e.printStackTrace();
		}
	}

	private synchronized void loudspew( String nick )
	{
		// Use the cache
		boolean willSkip = false;
		synchronized(tellCache)
		{
			Long cache = tellCache.get(nick);
			if (cache != null && cache > System.currentTimeMillis())
				willSkip = true;
			tellCache.put(nick, System.currentTimeMillis() + CACHEEXPIRE);
		}
		if (willSkip)
			return;

		// getBestPrimaryNick should be safe from injection
		String testNick = mods.nick.getBestPrimaryNick( nick );
		// rootNick won't, necessarily
		String rootNick = mods.security.getRootUser( testNick );

		List<TellObject> results;
		if (rootNick != null && !rootNick.equals(testNick))
			results = mods.odb.retrieve (TellObject.class, "WHERE target = '" + testNick + "' OR target = '" + rootNick.replaceAll("(\\\\|\\\")","\\\\$1") + "'");
		else
			results = mods.odb.retrieve (TellObject.class, "WHERE target = '" + testNick + "'");

		if (results.size() != 0)
		{
			int nsStatus = -1;
			for (int i=0; i < results.size(); i++ )
			{
				TellObject tellObj = (TellObject)results.get(i);
				if (tellObj.nickServ)
				{
					// This is a secure tell. One of several things can happen.
					// If Secure is not set, we require only NS auth.
					// Otherwise, we check if rootNick was set. If so, we
					// require both NS auth and that testNick is linked to
					// rootNick.
					if (nsStatus == -1)
					{
						try
						{
							if ( mods.plugin.callAPI("Options", "GetUserOption", nick, "Secure", "0" ).equals("1") )
							{
								// If secure tell is set, we require the
								// actual nickname to be explicitly linked
								// to the root.

								// If not, just the primary will do.
								String secureRootNick = mods.security.getRootUser( nick );
								if (rootNick != null)
								{
									// rootNick is set and we're directed at it.
									// Hence must check root of real nick is
									// equal to rootNick.
									if ( !rootNick.equalsIgnoreCase(secureRootNick) )
										nsStatus = -2;
								}
								else
								{
									// rootNick is NOT set. Since Secure
									// operates on bot users, and the user
									// hasn't registered his, we tell him to
									// bugger off.
									nsStatus = -3;
								}
							}
						}
						catch (ChoobNoSuchCallException e) { } // Since default is 0, don't care
						if (nsStatus == -1)
						{
							// Either not secure tell, or properly linked.
							// We still require NS auth.
							nsStatus = nsStatus( nick );
						}
					}
					// If all the above ran and we're allowed to send, nsStatus
					// is 3. Otherwise it's >= -1, <= 2.
					if (nsStatus == -1)
					{
						try
						{
							if ( mods.plugin.callAPI("Options", "GetUserOption", nick, "Insecure", "1" ).equals("1") )
								nsStatus = nsStatus( nick );
						}
						catch (ChoobNoSuchCallException e) {
							// Use default.
							nsStatus = nsStatus( nick );
						}
					}
					// If all the above ran and we're allowed to send, nsStatus
					// is 3. Otherwise it's >= -1, <= 2.
					if (nsStatus != 3)
						continue;
					if (nsStatus != 3)
						continue;
				}
				irc.sendMessage(nick, "At " + new Date(tellObj.date) + ", " + tellObj.from + " told me to " + tellObj.type + " you: " + tellObj.message);
				mods.odb.delete(results.get(i));
			}
			if (nsStatus == -2)
				irc.sendMessage(nick, "Hi! I think you have tells, and you have set Secure, but your nickname isn't linked to " + rootNick + ". See Help.Help Security.UsingLink to do this, then do Tell.Get.");
			else if (nsStatus == -3)
				irc.sendMessage(nick, "Hi! I think you have tells, and you have set Secure, but you haven't actually registered " + testNick + " with the bot. Since this defeats the point of secure tells, I suggest you register it (Security.AddUser), then link this nickname to it (See Help.Help Security.UsingLink).");
			else if (nsStatus == 0)
				irc.sendMessage(nick, "Hi! I think you (" + testNick + ") have tells, but you haven't actually registered your nickname with NickServ. Since " + testNick + " was registered and you haven't set the Unsecure option, you need to register with this nickname or change to " + testNick + " to pick up your tells.");
			else if (nsStatus > 0 && nsStatus < 3)
				irc.sendMessage(nick, "Hi! You have tells, but you're not identified with NickServ! Once you've done so, use the Tell.Get command.");
		}
	}

	public void onAction( ChannelAction ev )
	{
		if (ev.getSynthLevel() > 0)
			return;
		spew(ev.getNick());
	}

	public void onMessage( ChannelMessage ev )
	{
		if (ev.getSynthLevel() > 0)
			return;
		spew(ev.getNick());
	}

	public void onPrivateMessage( PrivateMessage ev )
	{
		if (ev.getSynthLevel() > 0)
			return;
		spew(ev.getNick());
	}

	public void onPrivateAction( PrivateAction ev )
	{
		if (ev.getSynthLevel() > 0)
			return;
		spew(ev.getNick());
	}

	public void onJoin( ChannelJoin ev )
	{
		spew(ev.getNick());
	}

	public void onNickChange( NickChange ev )
	{
		try
		{
			Thread.sleep(1000);
		}
		catch (InterruptedException e)
		{
			// Bah, who cares? :)
		}
		spew(ev.getNewNick());
	}

	private int nsStatus( String nick )
	{
		try
		{
			return (Integer)mods.plugin.callAPI("NickServ", "Status", nick);
		}
		catch (ChoobNoSuchCallException e)
		{
			return 0;
		}
	}
}

