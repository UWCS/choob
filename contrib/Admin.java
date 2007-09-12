import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.event.*


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

	private Modules mods;
	private IRCInterface irc;
	public Admin(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public void commandJoin( Message mes )
	{
		if ( mods.security.hasNickPerm( new ChoobPermission("state.join"), mes ) )
			try
			{
				irc.join(mods.util.getParamString(mes));
				irc.sendContextReply(mes, "Okay!");
			}
			catch (ChoobException e)
			{
				irc.sendContextReply(mes, "Couldn't join :/");
			}
	}

	public void commandGc( Message mes )
	{
		if ( mods.security.hasNickPerm( new ChoobPermission("system.gc"), mes ) )
		{
			System.gc();
			irc.sendContextReply(mes, "Gcd." );
		}
		else
			irc.sendContextReply(mes, "Denied." );
	}

	public void commandRaw( Message mes )
	{
		if ( mods.security.hasNickPerm( new ChoobPermission("admin.raw"), mes ) )
			irc.sendRawLine(mods.util.getParamString(mes));
		else
			irc.sendContextReply(mes, "Don't do that.");
	}

	private String bytesString(long l)
	{
		return new Float(((double)l)/1024.0/1024.0).toString() + "mb";
	}

	public void commandMemory( Message mes )
	{
		long total = Runtime.getRuntime().totalMemory();
		irc.sendContextReply(mes, "I'm using " +
			bytesString(total - Runtime.getRuntime().freeMemory()) +
			" out of " +
			bytesString(total) +
			" of memory (which can scale up to " +
			bytesString(Runtime.getRuntime().maxMemory()) +
		").");
	}
}

