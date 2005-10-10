import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
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
	Modules mods;
	IRCInterface irc;

	public Seen(Modules mods, IRCInterface irc)
	{
		this.irc = irc;
		this.mods = mods;
	}

	public void commandSeen( Message mes ) throws ChoobException
	{
		String nick = mods.nick.getBestPrimaryNick(mods.util.getParamString( mes ));

		if (nick.toLowerCase().equals(mes.getNick().toLowerCase()))
		{
			// Seen on themselves.
			irc.sendContextReply( mes, "Ever looked in a mirror?" );
			return;
		}

		SeenObj seen = getSeen( nick, false );
		if (seen == null)
		{
			irc.sendContextReply( mes, "Sorry, no such luck! I've not seen " + nick + "!" );
			return;
		}

		if (seen.primaryTime > 0)
		{
			String primaryTime = DateFormat.getInstance().format(new Date(seen.primaryTime));
			String secondaryTime = DateFormat.getInstance().format(new Date(seen.secondaryTime));
			// Have spoken
			switch(seen.secondaryType)
			{
				case 0:
					// Nothing
					irc.sendContextReply( mes, nick + " said \"" + seen.primaryMessage + "\" in " + seen.primaryChannel + " at " + primaryTime + ".");
					break;
				case 1:
					// Nick Change
					irc.sendContextReply( mes, nick + " said \"" + seen.primaryMessage + "\" in " + seen.primaryChannel + " at " + primaryTime + " before changing nickname to " + seen.secondaryData + " at " + secondaryTime + ".");
					break;
				case 2:
					// Kick
					irc.sendContextReply( mes, nick + " said \"" + seen.primaryMessage + "\" in " + seen.primaryChannel + " at " + primaryTime + " before being kicked from " + seen.secondaryData + " at " + secondaryTime + ".");
					break;
				case 3:
					// Part
					irc.sendContextReply( mes, nick + " said \"" + seen.primaryMessage + "\" in " + seen.primaryChannel + " at " + primaryTime + " before leaving " + seen.secondaryData + " at " + secondaryTime + ".");
					break;
				case 4:
					// Quit
					irc.sendContextReply( mes, nick + " said \"" + seen.primaryMessage + "\" in " + seen.primaryChannel + " at " + primaryTime + " before quitting with message \"" + seen.secondaryData + "\" at " + secondaryTime + ".");
					break;
			}
		}
		else
		{
			// Haven't spoken
		}
	}

	private SeenObj getSeen(String nick, boolean create) throws ChoobException
	{
		nick = mods.nick.getBestPrimaryNick(nick).replaceAll("(\\W)", "\\\\$1");

		List objs = mods.odb.retrieve( SeenObj.class, "WHERE nick = \"" + nick + "\"" );

		if ( objs.size() == 0 )
		{
			if ( create )
			{
				SeenObj seen = new SeenObj();
				seen.nick = nick;
				seen.primaryMessage = "";
				seen.primaryChannel = "";
				seen.secondaryData = "";
				return seen;
			}
			else
				return null;
		}
		else
			return (SeenObj)objs.get(0);
	}

	// Synchronized, since update appears to be non thread safe...
	private synchronized void saveSeen(SeenObj seen) throws ChoobException
	{
		if (seen.id == 0)
			mods.odb.save(seen);
		else
			mods.odb.update(seen);
	}

	// Expire old checks when appropriate...

	public void onMessage( ChannelMessage mes ) throws ChoobException
	{
		SeenObj seen = getSeen( mes.getNick(), true );
		seen.primaryTime = System.currentTimeMillis();
		seen.primaryMessage = mes.getMessage();
		seen.primaryChannel = mes.getChannel();
		seen.secondaryType = 0;
		seen.secondaryData = "";
		saveSeen (seen);
	}

	public void onNickChange( NickChange nc ) throws ChoobException
	{
		SeenObj seen = getSeen( nc.getNick(), true );
		seen.secondaryTime = System.currentTimeMillis();
		seen.secondaryData = nc.getNewNick();
		seen.secondaryType = 1;
		saveSeen (seen);
	}

	public void onKick( ChannelKick ck ) throws ChoobException
	{
		SeenObj seen = getSeen( ck.getTarget(), true );
		seen.secondaryTime = System.currentTimeMillis();
		seen.secondaryData = ck.getChannel() + " with message \"" + ck.getMessage() + "\"";
		seen.secondaryType = 2;
		saveSeen (seen);
	}

	public void onPart( ChannelPart cp ) throws ChoobException
	{
		SeenObj seen = getSeen( cp.getNick(), true );
		seen.secondaryTime = System.currentTimeMillis();
		seen.secondaryData = cp.getChannel();
		seen.secondaryType = 3;
		saveSeen (seen);
	}

	public void onQuit( QuitEvent qe ) throws ChoobException
	{
		SeenObj seen = getSeen( qe.getNick(), true );
		seen.secondaryTime = System.currentTimeMillis();
		seen.secondaryData = qe.getMessage();
		seen.secondaryType = 4;
		saveSeen (seen);
	}
}
