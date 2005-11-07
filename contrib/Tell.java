import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
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
	private static long CACHEEXPIRE = 5 * 60 * 1000; // 5 mins

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

	public synchronized void commandSend( Message mes ) throws ChoobException
	{
		List<String> params = mods.util.getParams(mes, 2);
		if (params.size() <= 2)
		{
			irc.sendContextReply(mes, "Syntax: Tell.Send <Nick>[,<Nick>...] <Message>");
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
			irc.sendContextReply(mes, "Sorry, you're only allowed " + MAXTARGETS + " targets for a given tell");
			return;
		}

		final List<String> done = new ArrayList<String>(MAXTARGETS);

		// Yeah, I don't really understand vim's indenting here either.
		mods.odb.runTransaction(
				new ObjectDBTransaction()
				{
					public void run() throws ChoobException
		{
			for(int i=0; i<targets.length; i++)
		{
			tellObj.id = 0;
			tellObj.target = mods.nick.getBestPrimaryNick(targets[i]);
			tellObj.nickServ = nsStatus(tellObj.target) > 0;
			if (done.contains(tellObj.target))
				continue;
			clearCache(tellObj.target);
			save(tellObj);
			done.add(tellObj.target);
		}
		}
		});

		irc.sendContextReply(mes, "Okay, will " + tellObj.type + " upon next speaking. (Sent to " + done.size() + " " + (done.size() == 1 ? "person" : "people") + ").");
	}

	public String[] helpCommandGet = {
		"Get any tells that have been sent to you. See Cache."
	};
	public void commandGet( Message mes ) throws ChoobException
	{
		clearCache(mes.getNick());
		spew( mes.getNick() );
		irc.sendContextReply(mes, "OK, if you had any tells, I just sent 'em. :)");
	}

	private void clearCache( String nick )
	{
		synchronized(tellCache)
		{
			Iterator<String> iter = tellCache.keySet().iterator();
			while(iter.hasNext())
			{
				if (mods.nick.getBestPrimaryNick(iter.next()).equalsIgnoreCase(nick))
					iter.remove();
			}
		}
	}

	private synchronized void spew( String nick ) throws ChoobException
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
		String testNick = mods.nick.getBestPrimaryNick(nick);
		List<TellObject> results = mods.odb.retrieve (TellObject.class, "WHERE target = '" + testNick + "'");

		if (results.size() != 0)
		{
			int nsStatus = -2;
			String rootNick = null;
			for (int i=0; i < results.size(); i++ )
			{
				TellObject tellObj = (TellObject)results.get(i);
				if (tellObj.nickServ)
				{
					if (nsStatus == -2)
					{
						rootNick = mods.security.getRootUser( nick );
						if (!rootNick.equalsIgnoreCase(testNick))
							nsStatus = -1;
						else
							nsStatus = nsStatus( testNick );
					}
					if (nsStatus != 3)
						continue;
				}
				irc.sendMessage(nick, "At " + new Date(tellObj.date) + ", " + tellObj.from + " told me to " + tellObj.type + " you: " + tellObj.message);
				mods.odb.delete(results.get(i));
			}
			if (nsStatus == -1)
				irc.sendMessage(nick, "Hi! I think you have tells, but your nickname isn't linked to " + testNick + ". Use Security.Link to do this, then do Tell.Get.");
			else if (nsStatus > 0 && nsStatus < 3)
				irc.sendMessage(nick, "Hi! You have tells, but you're not identified with NickServ! Once you've done so, use the Tell.Get command.");
		}
	}

	public void onMessage( ChannelMessage ev ) throws ChoobException
	{
		spew(ev.getNick());
	}

	public void onPrivateMessage( PrivateMessage ev ) throws ChoobException
	{
		spew(ev.getNick());
	}

	public void onJoin( ChannelJoin ev ) throws ChoobException
	{
		spew(ev.getNick());
	}

	private int nsStatus( String nick ) throws ChoobException
	{
		try
		{
			return (Integer)mods.plugin.callAPI("NickServ", "Status", nick);
		}
		catch (ChoobNoSuchPluginException e)
		{
			return 0;
		}
	}
}

