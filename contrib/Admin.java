import java.lang.management.GarbageCollectorMXBean;
import java.util.List;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;


public class Admin
{
	public String[] info()
	{
		return new String[] {
			"Plugin containing various administrator commands",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private final Modules mods;
	private final IRCInterface irc;
	public Admin(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public String[] helpCommandJoin = {
		"Makes the bot join a specific channel.",
		"<Channel>",
		"<Channel> is the channel to join"
	};
	public void commandJoin( final Message mes )
	{
		if ( mods.security.hasNickPerm( new ChoobPermission("state.join"), mes ) )
			try
			{
				irc.join(mods.util.getParamString(mes));
				irc.sendContextReply(mes, "Okay!");
			}
			catch (final ChoobException e)
			{
				irc.sendContextReply(mes, "Couldn't join :/");
			}
	}

	public String[] helpCommandExit = {
		"Makes the bot disconnect and not come back.",
		"[<Message>]",
		"<Message> is message to use when disconnecting"
	};
	public void commandExit(final Message mes)
	{
		if (!mods.security.hasPerm(new ChoobPermission("exit"), mes)) {
			irc.sendContextReply(mes, "You don't have permission to do that!");
			return;
		}
		final List<String> params = mods.util.getParams(mes, 1);
		try
		{
			if (params.size() <= 1) {
				irc.quit("Bye bye!");
			} else {
				irc.quit(params.get(1));
			}
		}
		catch (final ChoobException e)
		{
			irc.sendContextReply(mes, "Unable to quit: " + e);
		}
	}

	public String[] helpCommandRestart = {
		"Makes the bot disconnect and restart.",
		"[<Message>]",
		"<Message> is message to use when disconnecting"
	};
	public void commandRestart(final Message mes)
	{
		if (!mods.security.hasPerm(new ChoobPermission("exit"), mes)) {
			irc.sendContextReply(mes, "You don't have permission to do that!");
			return;
		}
		final List<String> params = mods.util.getParams(mes, 1);
		try
		{
			if (params.size() <= 1) {
				irc.restart("Restarting...");
			} else {
				irc.restart(params.get(1));
			}
		}
		catch (final ChoobException e)
		{
			irc.sendContextReply(mes, "Unable to restart: " + e);
		}
	}

	public void commandGc( final Message mes )
	{
		if ( mods.security.hasNickPerm( new ChoobPermission("system.gc"), mes ) )
		{
			System.gc();
			irc.sendContextReply(mes, "Gcd." );
		}
		else
			irc.sendContextReply(mes, "Denied." );
	}

	public void commandRaw( final Message mes )
	{
		if ( mods.security.hasNickPerm( new ChoobPermission("admin.raw"), mes ) )
		{
			irc.sendRawLine(mods.util.getParamString(mes));
			irc.sendContextReply(mes, "Done.");
		}
		else
			irc.sendContextReply(mes, "Don't do that.");
	}

	private String bytesString(final long l)
	{
		return new Float(l/1024.0/1024.0).toString() + "mb";
	}

	public void commandMemory( final Message mes )
	{
		final long total = Runtime.getRuntime().totalMemory();
		irc.sendContextReply(mes, "I'm using " +
			bytesString(total - Runtime.getRuntime().freeMemory()) +
			" out of " +
			bytesString(total) +
			" of memory (which can scale up to " +
			bytesString(Runtime.getRuntime().maxMemory()) +
		").");
	}

	public void commandGCs( final Message mes )
	{
		final List<GarbageCollectorMXBean> gcbeans = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();
		String ret = "";
		for (final GarbageCollectorMXBean gcbean : gcbeans)
			ret += gcbean.getName() + " has run " + gcbean.getCollectionCount() + " times, taking " + gcbean.getCollectionTime() + "ms total. ";
		irc.sendContextReply(mes, ret);
	}
}

