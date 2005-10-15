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
		// XXX clean this up.
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

	public String[] helpCommandLookupIn = {
		"Look up a DNS record in a domain.",
		"<Domain> [<Record>]",
		"<Domain> is the domain name",
		"<Record> is the optional record type"
	};
	public void commandLookupIn( Message mes, Modules mods, IRCInterface irc )
	{

		List<String> params = mods.util.getParams(mes, 3);
		String domain, record;
		if (params.size() < 2 || params.size() > 3)
		{
			irc.sendContextReply(mes, "Usage: <domain> [<record>].");
			return;
		}
		else if (params.size() == 2)
		{
			domain = params.get(1);
			record = "A";
		}
		else
		{
			domain = params.get(1);
			record = params.get(2);
		}

		irc.sendContextReply(mes, genericLookup(domain, record));
	}
}
