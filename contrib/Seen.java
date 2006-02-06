import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.text.*;

/**
 * Choob nickserv checker
 *
 * @author bucko
 *
 * Anyone who needs further docs for this module has some serious Java issues.
 * :)
 */

// Holds the NickServ result
public class SeenObj
{
	public int id;
	public String name;
	public String nick;
	public String primaryMessage;
	public String primaryChannel;
	public long primaryTime;
	public String secondaryData;
	public int secondaryType;
	public long secondaryTime;
}

public class Seen
{
	public String[] info()
	{
		return new String[] {
			"Plugin which keeps track of when the bot last saw people.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	Modules mods;
	IRCInterface irc;

	long lockedUntil;

	public Seen(Modules mods, IRCInterface irc)
	{
		this.irc = irc;
		this.mods = mods;
		lockedUntil=0;
	}

	public String[] helpTopics = { "Using" };
	public String[] helpUsing = {
		  "Seen is a plugin that watches all channels and observes when it last"
		+ " saw people speak. It monitors messages, nick changes, quits,"
		+ " parts and kicks."
	};

	public String[] helpCommandSeen = {
		"Discover when the bot last saw a user.",
		"<Name>",
		"<Name> is the user to look for."
	};
	public void commandSeen( Message mes ) throws ChoobException
	{
		if (lockedUntil>(new GregorianCalendar()).getTimeInMillis())
		{
			irc.sendContextReply(mes, "An sql exception occoured sometime, Seen offline for a few minutes.");
			return;
		}
		String nick = mods.nick.getBestPrimaryNick(mods.util.getParamString( mes ));

		if (nick.toLowerCase().equals(mods.nick.getBestPrimaryNick(mes.getNick()).toLowerCase()))
		{
			// Seen on themselves.
			irc.sendContextReply( mes, "Ever looked in a mirror?" );
			return;
		}

		SeenObj seen = getSeen( nick, false );
		if (seen == null)
		{
			irc.sendContextReply( mes, "Sorry, no such luck! I don't remember seeing " + nick + "!" );
			return;
		}

		if (seen.primaryTime > 0)
		{
			// Have spoken
			String primaryTime = (new Date(seen.primaryTime)).toString();
			String secondaryTime = (new Date(seen.secondaryTime)).toString();
			switch(seen.secondaryType)
			{
				case 0:
					// Nothing
					irc.sendContextReply( mes, seen.nick + " said \"" + seen.primaryMessage + "\" in " + seen.primaryChannel + " at " + primaryTime + ".");
					break;
				case 1:
					// Nick Change
					irc.sendContextReply( mes, seen.nick + " said \"" + seen.primaryMessage + "\" in " + seen.primaryChannel + " at " + primaryTime + " before changing nickname to " + seen.secondaryData + " at " + secondaryTime + ".");
					break;
				case 2:
					// Kick
					irc.sendContextReply( mes, seen.nick + " said \"" + seen.primaryMessage + "\" in " + seen.primaryChannel + " at " + primaryTime + " before being kicked from " + seen.secondaryData + " at " + secondaryTime + ".");
					break;
				case 3:
					// Part
					irc.sendContextReply( mes, seen.nick + " said \"" + seen.primaryMessage + "\" in " + seen.primaryChannel + " at " + primaryTime + " before leaving " + seen.secondaryData + " at " + secondaryTime + ".");
					break;
				case 4:
					// Quit
					irc.sendContextReply( mes, seen.nick + " said \"" + seen.primaryMessage + "\" in " + seen.primaryChannel + " at " + primaryTime + " before quitting with message \"" + seen.secondaryData + "\" at " + secondaryTime + ".");
					break;
			}
		}
		else
		{
			// Haven't spoken
			String secondaryTime = (new Date(seen.secondaryTime)).toString();
			switch(seen.secondaryType)
			{
				case 0:
					// Nothing
					// Can this even happen?
					irc.sendContextReply( mes, "Sorry, no such luck! I don't remember seeing  " + seen.nick + "!" );
					break;
				case 1:
					// Nick Change
					irc.sendContextReply( mes, seen.nick + " changed nickname to " + seen.secondaryData + " at " + secondaryTime + ".");
					break;
				case 2:
					// Kick
					irc.sendContextReply( mes, seen.nick + " was kicked from " + seen.secondaryData + " at " + secondaryTime + ".");
					break;
				case 3:
					// Part
					irc.sendContextReply( mes, seen.nick + " left " + seen.secondaryData + " at " + secondaryTime + ".");
					break;
				case 4:
					// Quit
					irc.sendContextReply( mes, seen.nick + " before quit with message \"" + seen.secondaryData + "\" at " + secondaryTime + ".");
					break;
			}
		}
	}

	private SeenObj getSeen(String nick, boolean create) // throws ChoobException
	{
		if (lockedUntil>(new GregorianCalendar()).getTimeInMillis())
			return new SeenObj();

		String sortNick = mods.nick.getBestPrimaryNick(nick).replaceAll("(\\W)", "\\\\$1");

		List<SeenObj> objs;
		try
		{
			objs=mods.odb.retrieve( SeenObj.class, "WHERE name = \"" + sortNick + "\"" );
		}
		catch (Exception e)
		{
			lockedUntil=(new GregorianCalendar()).getTimeInMillis()+(2*60*1000);
			System.err.println("Seen suppressed error:");
			e.printStackTrace();
			objs=new ArrayList<SeenObj>();
		}

		if ( objs.size() == 0 )
		{
			if ( create )
			{
				SeenObj seen = new SeenObj();
				seen.name = sortNick;
				seen.primaryMessage = "";
				seen.primaryChannel = "";
				seen.secondaryData = "";
				return seen;
			}
			else
				return null;
		}
		else
		{
			SeenObj seen = objs.get(0);
			seen.name = sortNick; // To stop nasty errors...
			return seen;
		}
	}

	private void saveSeen(SeenObj seen) throws ChoobException
	{
		if (lockedUntil>(new GregorianCalendar()).getTimeInMillis())
			return;

		try
		{
			if (seen.id == 0)
				mods.odb.save(seen);
			else
				synchronized (this)
				{
					mods.odb.update(seen);
				}
		}
		catch (Exception e)
		{
			lockedUntil=(new GregorianCalendar()).getTimeInMillis()+(2*60*1000);
			System.err.println("Seen suppressed error:");
			e.printStackTrace();
		}
	}

	public Boolean apiSeen( String nick , Long period , String channelName )
	{
		if ((nick.matches("internal.*")) || (channelName.matches("internal.*"))) return new Boolean(true);

		boolean inChannelNow = mods.nick.isInChannel(nick,channelName).booleanValue();

		if (inChannelNow == true) return new Boolean(true);

		if (nick.equals("")) return new Boolean(false);
		try {
			SeenObj returnedSeen = getSeen(nick,false);
			if (returnedSeen == null) return new Boolean(false); 
			else {
				if (period.doubleValue() == 0) return new Boolean(true);
				else {  if ( returnedSeen.primaryTime  > (System.currentTimeMillis() - period.doubleValue() )) return new Boolean(true); else return new Boolean(false);}
			}
		} 	catch (Exception e) { return new Boolean(false);}
	}

	// Expire old checks when appropriate...

	public void onMessage( ChannelMessage mes ) throws ChoobException
	{
		SeenObj seen = getSeen( mes.getNick(), true );
		seen.nick = mes.getNick();
		seen.primaryTime = System.currentTimeMillis();
		seen.primaryMessage = mes.getMessage();
		seen.primaryChannel = mes.getChannel();
		seen.secondaryType = 0;
		seen.secondaryData = "";
		saveSeen (seen);
	}

	public void onAction( ChannelAction mes ) throws ChoobException
	{
		SeenObj seen = getSeen( mes.getNick(), true );
		seen.nick = mes.getNick();
		seen.primaryTime = System.currentTimeMillis();
		seen.primaryMessage = "/me " + mes.getMessage();
		seen.primaryChannel = mes.getChannel();
		seen.secondaryType = 0;
		seen.secondaryData = "";
		saveSeen (seen);
	}

	public void onNickChange( NickChange nc ) throws ChoobException
	{
		SeenObj seen = getSeen( nc.getNick(), true );
		seen.nick = nc.getNick();
		seen.secondaryTime = System.currentTimeMillis();
		seen.secondaryData = nc.getNewNick();
		seen.secondaryType = 1;
		saveSeen (seen);
	}

	public void onKick( ChannelKick ck ) throws ChoobException
	{
		SeenObj seen = getSeen( ck.getTarget(), true );
		seen.nick = ck.getTarget();
		seen.secondaryTime = System.currentTimeMillis();
		seen.secondaryData = ck.getChannel() + " with message \"" + ck.getMessage() + "\"";
		seen.secondaryType = 2;
		saveSeen (seen);
	}

	public void onPart( ChannelPart cp ) throws ChoobException
	{
		SeenObj seen = getSeen( cp.getNick(), true );
		seen.nick = cp.getNick();
		seen.secondaryTime = System.currentTimeMillis();
		seen.secondaryData = cp.getChannel();
		seen.secondaryType = 3;
		saveSeen (seen);
	}

	public void onQuit( QuitEvent qe ) throws ChoobException
	{
		SeenObj seen = getSeen( qe.getNick(), true );
		seen.nick = qe.getNick();
		seen.secondaryTime = System.currentTimeMillis();
		seen.secondaryData = qe.getMessage();
		seen.secondaryType = 4;
		saveSeen (seen);
	}
}
