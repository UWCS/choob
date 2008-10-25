import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.ChannelAction;
import uk.co.uwcs.choob.support.events.ChannelKick;
import uk.co.uwcs.choob.support.events.ChannelMessage;
import uk.co.uwcs.choob.support.events.ChannelPart;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.NickChange;
import uk.co.uwcs.choob.support.events.QuitEvent;

/**
 * Choob nickserv checker
 *
 * @author bucko
 *
 * Anyone who needs further docs for this module has some serious Java issues.
 * :)
 */

// Holds the NickServ result
class SeenObj
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

	public Seen(final Modules mods, final IRCInterface irc)
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
	public void commandSeen( final Message mes )
	{
		if (lockedUntil>new GregorianCalendar().getTimeInMillis())
		{
			irc.sendContextReply(mes, "An sql exception occoured sometime, Seen offline for a few minutes.");
			return;
		}
		final String nick = mods.nick.getBestPrimaryNick(mods.util.getParamString( mes ));

		final SeenObj seen = getSeen( nick, false );
		if (seen == null)
		{
			if (nick.toLowerCase().equals(mods.nick.getBestPrimaryNick(mes.getNick()).toLowerCase()))
			{
				// Seen on themselves when they've not been seen yet.
				irc.sendContextReply( mes, "You haven't done anything yet!" );
			} else {
				irc.sendContextReply( mes, "Sorry, no such luck! I don't remember seeing " + nick + "!" );
			}
			return;
		}

		if (seen.primaryTime > 0)
		{
			// Have spoken
			final String primaryTime = new Date(seen.primaryTime).toString();
			final String secondaryTime = new Date(seen.secondaryTime).toString();
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
			final String secondaryTime = new Date(seen.secondaryTime).toString();
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
					irc.sendContextReply( mes, seen.nick + " quit with message \"" + seen.secondaryData + "\" at " + secondaryTime + ".");
					break;
			}
		}
	}

	private SeenObj getSeen(final String nick, final boolean create) // throws ChoobException
	{
		if (lockedUntil>new GregorianCalendar().getTimeInMillis())
			return new SeenObj();

		final String sortNick = mods.nick.getBestPrimaryNick(nick).replaceAll("(\\W)", "\\\\$1");

		List<SeenObj> objs;
		try
		{
			objs=mods.odb.retrieve( SeenObj.class, "WHERE name = \"" + mods.odb.escapeString(sortNick) + "\"" );
		}
		catch (final Exception e)
		{
			lockedUntil=new GregorianCalendar().getTimeInMillis()+2*60*1000;
			System.err.println("Seen suppressed error:");
			e.printStackTrace();
			objs=new ArrayList<SeenObj>();
		}

		if ( objs.size() == 0 )
		{
			if ( create )
			{
				final SeenObj seen = new SeenObj();
				seen.name = sortNick;
				seen.primaryMessage = "";
				seen.primaryChannel = "";
				seen.secondaryData = "";
				return seen;
			}
			return null;
		}
		final SeenObj seen = objs.get(0);
		seen.name = sortNick; // To stop nasty errors...
		return seen;
	}

	private void saveSeen(final SeenObj seen)
	{
		if (lockedUntil>new GregorianCalendar().getTimeInMillis())
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
		catch (final Exception e)
		{
			lockedUntil=new GregorianCalendar().getTimeInMillis()+2*60*1000;
			System.err.println("Seen suppressed error:");
			e.printStackTrace();
		}
	}

	// Expire old checks when appropriate...

	public void onMessage( final ChannelMessage mes )
	{
		// If the event has been faked, don't count it!
		if (mes.getSynthLevel() > 0)
			return;

		// Oh! Oh! Ooooh! Can't you *feel* the hacks?
		try {
			Thread.sleep(1000);
		} catch(final InterruptedException e)
		{
			// It was a hack anyway, who cares?
		}

		final SeenObj seen = getSeen( mes.getNick(), true );
		seen.nick = mes.getNick();
		seen.primaryTime = System.currentTimeMillis();
		seen.primaryMessage = mes.getMessage();
		seen.primaryChannel = mes.getChannel();
		seen.secondaryType = 0;
		seen.secondaryData = "";
		saveSeen (seen);
	}

	public void onAction( final ChannelAction mes )
	{
		// Oh! Oh! Ooooh! Can't you *feel* the hacks?
		try {
			Thread.sleep(1000);
		} catch(final InterruptedException e)
		{
			// It was a hack anyway, who cares?
		}

		final SeenObj seen = getSeen( mes.getNick(), true );
		seen.nick = mes.getNick();
		seen.primaryTime = System.currentTimeMillis();
		seen.primaryMessage = "/me " + mes.getMessage();
		seen.primaryChannel = mes.getChannel();
		seen.secondaryType = 0;
		seen.secondaryData = "";
		saveSeen (seen);
	}

	public void onNickChange( final NickChange nc )
	{
		final SeenObj seen = getSeen( nc.getNick(), true );
		seen.nick = nc.getNick();
		seen.secondaryTime = System.currentTimeMillis();
		seen.secondaryData = nc.getNewNick();
		seen.secondaryType = 1;
		saveSeen (seen);
	}

	public void onKick( final ChannelKick ck )
	{
		final SeenObj seen = getSeen( ck.getTarget(), true );
		seen.nick = ck.getTarget();
		seen.secondaryTime = System.currentTimeMillis();
		seen.secondaryData = ck.getChannel() + " with message \"" + ck.getMessage() + "\"";
		seen.secondaryType = 2;
		saveSeen (seen);
	}

	public void onPart( final ChannelPart cp )
	{
		final SeenObj seen = getSeen( cp.getNick(), true );
		seen.nick = cp.getNick();
		seen.secondaryTime = System.currentTimeMillis();
		seen.secondaryData = cp.getChannel();
		seen.secondaryType = 3;
		saveSeen (seen);
	}

	public void onQuit( final QuitEvent qe )
	{
		final SeenObj seen = getSeen( qe.getNick(), true );
		seen.nick = qe.getNick();
		seen.secondaryTime = System.currentTimeMillis();
		seen.secondaryData = qe.getMessage();
		seen.secondaryType = 4;
		saveSeen (seen);
	}
}
