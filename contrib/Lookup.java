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
	private static final Hashtable env = new Hashtable();
	static
	{
		env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
	}

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

	private String apiLookup(String what, String where)
	{
		if (where.equals("REV"))
			return what + " [reverse lookup] --> " + reverseLookup(what) + ".";

		Attribute attr;

		try
		{
			attr = (new InitialDirContext( env )).getAttributes( what, new String[] { where }).get( where );


			if( attr == null )
				return "None found...";
			else
			{
				StringBuilder rep=new StringBuilder();
				for (int i=0; i<attr.size(); i++)
				{
					rep.append(attr.get(i));
					if (i!=attr.size()-1)
						rep.append(", ");
				}

				return what + " ["  +where + "] --> " + rep.toString() + ".";
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

		List<String> params = mods.util.getParams(mes, 2);
		String domain, record;
		if (params.size() <= 1)
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

		irc.sendContextReply(mes, apiLookup(domain, record));
	}
}
