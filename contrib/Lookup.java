import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.event.*
import uk.co.uwcs.choob.modules.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;
import java.util.Hashtable;
import javax.naming.*;
import javax.naming.directory.*;
import java.text.*;
import java.io.*;

public class Lookup
{
	public String[] info()
	{
		return new String[] {
			"DNS lookup plugin.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}
											// 0->199
	private final static String token4 = "(?:(?:1?[0-9]?[0-9])|(?:2[0-4][0-9])|(?:25[0-5]))";
	private final static String pattern4 = token4 + "\\." + token4 + "\\." + token4 + "\\." + token4;
	private final static Pattern ipv4=Pattern.compile(pattern4);

	//http://blogs.msdn.com/mpoulson/archive/2005/01/10/350037.aspx
	private final static String token6 = "[0-9a-fA-F]{1,4}";
	private final static String IPv6Pattern =			"(?:" + token6 + ":){7}" + token6;
	private final static String IPv6Pattern_6Hex4Dec =	"(?:" + token6 + ":){6}" + pattern4;

	private final static String compressedStart = 				"((?:" + token6 + "(?::" + token6 + ")*)?)::(?:" + token6;
	private final static String IPv6Pattern_HEXCompressed =		compressedStart + "(?::" + token6 + ")*)?";
	private final static String IPv6Pattern_Hex4DecCompressed =	compressedStart + ":)*" + pattern4;

	private final static String regor = ")|(?:";
	private final static Pattern ipv6 = Pattern.compile("(?:" + IPv6Pattern + regor + IPv6Pattern_6Hex4Dec + regor + IPv6Pattern_HEXCompressed + regor + IPv6Pattern_Hex4DecCompressed + ")");

	private IRCInterface irc;
	private Modules mods;
	public Lookup(Modules mods, IRCInterface irc)
	{
		this.irc = irc;
		this.mods = mods;
	}

	private static final Hashtable<String, String> env = new Hashtable<String, String>();
	static
	{
		env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
	}

	private String reverseLookup(String s)
	{
		try
		{
			InetAddress ia = InetAddress.getByName(s);
			String hostname = ia.getHostName();
			if (!hostname.trim().equalsIgnoreCase(s.trim()))
				return hostname;
			else
			{
				URL url;
				try
				{
					url = new URL("http://www.showmyip.com/xml/?ip=" + URLEncoder.encode(s, "UTF-8") + "&app=Choob");
				}
				catch (UnsupportedEncodingException e)
				{
					return "Unexpected exception generating url.";
				}
				catch (MalformedURLException e)
				{
					return "Error, malformed url generated.";
				}

				String showmyip;
				try
				{
					// Cache for a day.
					showmyip = mods.scrape.getContentsCached(url, 24*60*60*1000);
				}
				catch (IOException e)
				{
					return "No direct lookup avaliable. " + e;
				}

				final String denied = "provided to subscribers only";

				// Not generated at all, no-sir-ee.
				final Pattern pcountry	=Pattern.compile("<lookup_country>(.+)</lookup_country>");
				final Pattern pcountry2	=Pattern.compile("<lookup_country2>(.+)</lookup_country2>");
				final Pattern pisp		=Pattern.compile("<lookup_isp>(.+)</lookup_isp>");
				final Pattern porg		=Pattern.compile("<lookup_org>(.+)</lookup_org>");
				final Pattern porg2		=Pattern.compile("<lookup_org2>(.+)</lookup_org2>");
				final Pattern pcity		=Pattern.compile("<lookup_city>(.+)</lookup_city>");
				final Pattern pcity2	=Pattern.compile("<lookup_city2>(.+)</lookup_city2>");

				final Matcher mcountry	=pcountry		.matcher(showmyip);
				final Matcher mcountry2	=pcountry2		.matcher(showmyip);
				final Matcher misp		=pisp			.matcher(showmyip);
				final Matcher morg		=porg			.matcher(showmyip);
				final Matcher morg2		=porg2			.matcher(showmyip);
				final Matcher mcity		=pcity			.matcher(showmyip);
				final Matcher mcity2	=pcity2			.matcher(showmyip);

				String scountry		=null; if (	mcountry	.find()) scountry	=mcountry	.group(1);
				String scountry2	=null; if (	mcountry2	.find()) scountry2	=mcountry2	.group(1);
				String sisp			=null; if (	misp		.find()) sisp		=misp		.group(1);
				String sorg			=null; if (	morg		.find()) sorg		=morg		.group(1);
				String sorg2		=null; if (	morg2		.find()) sorg2		=morg2		.group(1);
				String scity		=null; if (	mcity		.find()) scity		=mcity		.group(1);
				String scity2		=null; if (	mcity2		.find()) scity2		=mcity2		.group(1);

				if (	scountry	!= null && scountry		.equalsIgnoreCase(denied))	scountry	=null;
				if (	scountry2	!= null && scountry2	.equalsIgnoreCase(denied))	scountry2	=null;
				if (	sisp		!= null && sisp			.equalsIgnoreCase(denied))	sisp		=null;
				if (	sorg		!= null && sorg			.equalsIgnoreCase(denied))	sorg		=null;
				if (	sorg2		!= null && sorg2		.equalsIgnoreCase(denied))	sorg2		=null;
				if (	scity		!= null && scity		.equalsIgnoreCase(denied))	scity		=null;
				if (	scity2		!= null && scity2		.equalsIgnoreCase(denied))	scity2		=null;

				if (sorg		!= null && sorg2		!= null && sorg2		.trim().equalsIgnoreCase(sorg		.trim())) sorg2		=null;
				if (scity		!= null && scity2		!= null && scity2		.trim().equalsIgnoreCase(scity		.trim())) scity2	=null;
				if (scountry 	!= null && scountry2	!= null && scountry2	.trim().equalsIgnoreCase(scountry	.trim())) scountry2	=null;

				if (sorg == null		&& sorg2		!= null) { sorg		= sorg2;		sorg2		=null; }
				if (scity == null		&& scity2		!= null) { scity	= scity2;		scity2		=null; }
				if (scountry == null	&& scountry2	!= null) { scountry	= scountry2;	scountry2	=null; }

				return "No direct reverse lookup avaliable. " +
					(scountry	!= null ? "Country: "		+ scountry	+ (scountry2	!= null ? " (or possibly: " + scountry2	+ ")" : "") + ";" : "") + " " +
					(sorg		!= null ? "Organisation: "	+ sorg		+ (sorg2		!= null ? " (or possibly: " + sorg2		+ ")" : "") + ";" : "") + " " +
					(scity		!= null ? "City: "			+ scity		+ (scity2		!= null ? " (or possibly: " + scity2	+ ")" : "") + ";" : "") + " " +
					(sisp		!= null ? "Isp: "			+ sisp		+ "." : "");


			}
		}
		catch(UnknownHostException e)
		{
			return "Failed.";
		}
	}

	private Attribute getAttributes(String what, String where) throws NameNotFoundException, NamingException
	{
		return (new InitialDirContext( env )).getAttributes( what, new String[] { where }).get( where );
	}

	private String apiLookup(String what, String where)
	{
		if (where.equals("REV"))
			return what + " [reverse lookup] --> " + reverseLookup(what) + ".";

		Attribute attr;

		try
		{
			attr = getAttributes(what, where);

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

				return what + " [" + where + "] --> " + rep.toString() + ".";
			}
		}
		catch ( NameNotFoundException e )
		{
			return "Not found: " + what;
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
	public void commandLookupIn( Message mes )
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
			if (ipv4.matcher(domain).matches() || ipv6.matcher(domain).matches())
				record = "REV";
			else
			{
				URL p;
				try
				{
					String reply;
					p = new URL(domain);
					try
					{
						Attribute attr = getAttributes(p.getHost(), "A");
						if (attr == null)
							reply = "None found...";
						else
						{
							assert attr.size() > 0;
							p = new URL(p.getProtocol(), attr.get(0).toString(), p.getPort(), p.getFile());

							reply = p.toString();
						}
					}
					catch ( NameNotFoundException e )
					{
						reply = "Not found: " + p.getHost();
					}
					catch ( NamingException e )
					{
						reply = "Unexpected error: " + e;
					}

					irc.sendContextReply(mes, reply);
					return;
				}
				catch ( MalformedURLException e )
				{
					// Squish.
				}

				record = "A";
			}
		}
		else
		{
			domain = params.get(1);
			record = params.get(2);
		}

		irc.sendContextReply(mes, apiLookup(domain, record));
	}
}
