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
			irc.sendRawLine(mods.util.getParamString(mes));
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

