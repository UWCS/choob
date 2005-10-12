import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.*;
import java.security.*;
import org.jibble.pircbot.Colors;
import java.util.regex.*;

/**
 * Choob nickserv checker
 *
 * @author bucko
 *
 * Anyone who needs further docs for this module has some serious Java issues.
 * :)
 */

// Holds the NickServ result
public class ResultObj
{
	int result;
	long time;
}

public class NickServ
{
	private static int TIMEOUT = 10000; // Timeout on nick checks.
//	private static int CACHE_TIMEOUT = 3600000; // Timeout on nick check cache (1 hour).
	// ^^ Can only be used once we can verify a user is in a channel and thus trust their online-ness.
	private static int CACHE_TIMEOUT = 300000; // Timeout on nick check cache (5 mins).

	private Map<String,ResultObj> nickChecks;
	Modules mods;
	IRCInterface irc;

	/** Enable horrible hacks. If you know which network you're going to be using the bot on, finalize this, and all of the other code will get removed by the compiler. */
	boolean infooverride=false;

	final Pattern ValidInfoReply=Pattern.compile("^(?:\\s*Nickname: ([^\\s]+) ?(<< ONLINE >>)?)|(?:The nickname \\[([^\\s]+)\\] is not registered)$");

	public NickServ(Modules mods, IRCInterface irc)
	{
		nickChecks = new HashMap<String,ResultObj>();
		this.irc = irc;
		this.mods = mods;
		if (infooverride==false)
			// Check ensure that our NickServ is sane, and, if not, enable workarounds.
			mods.interval.callBack(null, 1);
	}

	public synchronized void interval( Object parameter, Modules mods, IRCInterface irc ) throws ChoobException
	{
		apiCheck("____" + ((new Random()).nextInt() & 1024));
	}

	public void destroy(Modules mods)
	{
		synchronized(nickChecks)
		{
			Iterator<String> nicks = nickChecks.keySet().iterator();
			while(nicks.hasNext()) {
				ResultObj result = getNickCheck(nicks.next());
				synchronized(result)
				{
					result.notifyAll();
				}
			}
		}
	}

	public void commandCheck( Message mes ) throws ChoobException
	{
		String nick = mods.util.getParamString( mes );
		int check1 = (Integer)mods.plugin.callAPI("NickServ", "Status", nick);
		if ( check1 > 1 )
		{
			irc.sendContextReply(mes, nick + " is authed (" + check1 + ")!");
		}
		else
		{
			irc.sendContextReply(mes, nick + " is not authed (" + check1 + ")!");
		}
	}

	public int apiStatus( String nick )
	{
		System.out.println("Called by: " + mods.security.getCallerPluginName());
		List<String> names = mods.security.getPluginNames();
		System.out.println("Stack:");
		for(String name: names)
			System.out.println("\t" + name);
		System.out.println("End of stack.");
		ResultObj result = getCachedNickCheck( nick.toLowerCase() );
		if (result != null)
		{
			return result.result;
		}

		result = getNewNickCheck( nick.toLowerCase() );

		synchronized(result)
		{
			try
			{
				result.wait(30000); // Make sure if NickServ breaks we're not screwed
			}
			catch (InterruptedException e)
			{
				// Ooops, timeout
				return -1;
			}
		}
		int status = result.result;
		return status;
	}

	public boolean apiCheck( String nick )
	{
		return apiStatus( nick ) >= 3; // Ie, authed by password
	}

	private ResultObj getNewNickCheck( final String nick )
	{
		System.out.println("Asked for new nick check for " + nick);
		ResultObj result;
		synchronized(nickChecks)
		{
			result = nickChecks.get( nick );
			if ( result == null )
			{
				// Not already waiting on this one
				result = new ResultObj();
				result.result = -1;
				AccessController.doPrivileged( new PrivilegedAction() {
					public Object run()
					{
						irc.sendMessage("NickServ", (infooverride ? "INFO " : "STATUS ") + nick);
						return null;
					}
				});
				nickChecks.put( nick, result );
			}
		}
		return result;
	}

	private ResultObj getNickCheck( String nick )
	{
		synchronized(nickChecks)
		{
			return (ResultObj)nickChecks.get( nick );
		}
	}

	private ResultObj getCachedNickCheck( String nick )
	{
		ResultObj result;
		synchronized(nickChecks)
		{
			result = (ResultObj)nickChecks.get( nick );
			System.out.println("Cached value for " + nick + " is: " + result);
			if ( result == null )
				// !!! This should never really happen
				return null;

			if (result.result == -1)
				return null;

			if ( result.time + TIMEOUT < System.currentTimeMillis() )
			{
				// expired!
				// TODO - do this in an interval...
				nickChecks.remove( nick );
				System.out.println("Ooops, it expired! It has " + result.time + " vs our " + System.currentTimeMillis() + ".");
				return null;
			}
		}
		return result;
	}

	public void onPrivateNotice( Message mes )
	{
		if ( ! (mes instanceof PrivateNotice) )
			return; // Only interested in private notices

		if ( ! mes.getNick().toLowerCase().equals( "nickserv" ) )
			return; // Not from NickServ --> also don't care

		System.out.println(mes.getMessage());
		if (infooverride == false && mes.getMessage().trim().toLowerCase().equals("unknown command [status]"))
		{
			// Ohes nose, horribly broken network! Let's pretend that it didn't just slap us in the face with a glove.
			System.out.println("Reverting to badly broken NickServ handling.");

			infooverride=true;

			synchronized (nickChecks)
			{
				nickChecks.clear(); // <-- Ooh, lets break things.
			}

			// Any pending nick checks will fail, but.. well, bah.
			return;
		}

		List params = mods.util.getParams( mes );

		if (infooverride)
		{
			if (mes.getMessage().indexOf("Nickname: ") ==-1 && mes.getMessage().indexOf("The nickname [") ==-1)
				return; // Wrong type of message!
		}
		else
		{
			if ( ! ((String)params.get(0)).toLowerCase().equals("status") )
				return; // Wrong type of message!
		}

		String nick;
		int status;


		if (!infooverride /* && statuscommand*/)
		{
			nick = (String)params.get(1);
			status = Integer.valueOf((String)params.get(2));
		}
		else
		{
			Matcher ma=ValidInfoReply.matcher(Colors.removeFormattingAndColors(mes.getMessage()));

			if (!ma.matches())
				return;

			nick = ma.group(1);

			if (nick==null)
				nick=ma.group(3);

			status = (ma.group(2)!=null && !ma.group(2).equals("") ? 3 : 0);
		}

		ResultObj result = getNickCheck( nick.toLowerCase() );
		if ( result == null )
			return; // XXX

		synchronized(result)
		{
			result.result = status;
			result.time = System.currentTimeMillis();

			result.notifyAll();
		}
	}

	// Expire old checks when appropriate...

	public void onNickChange( NickChange nc )
	{
		synchronized(nickChecks)
		{
			nickChecks.remove(nc.getNick());
			nickChecks.remove(nc.getNewNick());
		}
	}

	public void onJoin( ChannelJoin cj )
	{
		synchronized(nickChecks)
		{
			nickChecks.remove(cj.getNick());
		}
	}

	public void onKick( ChannelKick ck )
	{
		synchronized(nickChecks)
		{
			nickChecks.remove(ck.getTarget());
		}
	}

	public void onPart( ChannelPart cp )
	{
		synchronized(nickChecks)
		{
			nickChecks.remove(cp.getNick());
		}
	}

	public void onQuit( QuitEvent qe )
	{
		synchronized(nickChecks)
		{
			nickChecks.remove(qe.getNick());
		}
	}
}
