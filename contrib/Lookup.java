import org.uwcs.choob.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import org.uwcs.choob.modules.*;
import java.util.*;
import java.net.*;
import java.util.Hashtable;
import javax.naming.*;
import javax.naming.directory.*;

public class Lookup
{
	private String reverseLookup(String s)
	{
		try
		{
			InetAddress ia = InetAddress.getByName(s);
			return ia.getHostName();
		}
		catch(UnknownHostException e)
		{
			return "Failed.";
		}
	}

	private String genericLookup(String what, String where)
	{
		if (where.equals("REV"))
			return what + " [reverse lookup] --> " + reverseLookup(what) + ".";

		// Stolen hax!
		try
		{
			Hashtable env = new Hashtable();
			env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
			DirContext ictx = new InitialDirContext( env );
			Attributes attrs = ictx.getAttributes( what, new String[] { where });
			Attribute attr = attrs.get( where );
			if( attr == null )
				return "None found...";
			else
			{
				String rep="";
				for (int i=0; i<attr.size(); i++)
					rep+=(String)attr.get(i) + ", ";
				rep=rep.substring(0, rep.length()-2);

				return what + " ["  +where + "] --> " + rep + ".";
			}
		}
		catch ( NamingException e )
		{
			return "Unexpected error: " + e;
		}
	}

	public void commandLookupIn( Message mes, Modules mods, IRCInterface irc )
	{

		List<String> p=mods.util.getParams(mes, 2);
		if (p.get(2).trim().length()==0)
		{
			irc.sendContextReply(mes, "Usage: <record> <domain>.");
			return;
		}


		irc.sendContextReply(mes, genericLookup(p.get(2), p.get(1)));
	}
}
