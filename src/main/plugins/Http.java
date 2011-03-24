import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.ChoobThread;
import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.ChoobNoSuchPluginException;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

class HashedStringObject
{
	public int id;
	public String hash;
	public String string;
}

public class Http
{
	int portNumber = 8023;

	// If this is null, the local machine's ip will be used.
	String externalName = null;

	public String[] optionsGeneral = { "PortNumber", "ExternalName" };
	public String[] optionsGeneralDefaults = { "8023", "" };

	// Check it's a number
	public boolean optionCheckGeneralJoinQuote( final String optionValue ) {
		try {
			portNumber = Integer.parseInt(optionValue);

			try
			{
				listener.close();
			}
			catch (final IOException e) { }
			listener = null;

			return true;
		} catch (final NumberFormatException e) {
			return false;
		}
	}

	// No real checking required
	public boolean optionCheckGeneralJoinMessage( final String optionValue ) {
		externalName = optionValue;
		return true;
	}

	public String[] helpOptionPortNumber = {
			  "Port number to operate the web server on."
	};
	public String[] helpOptionExternalName = {
			  "External hostname to display to users for this web server."
	};

	public String[] info()
	{
		return new String[] {
			"Plugin that implements a simple HTTP server.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private final Modules mods;
	private final IRCInterface irc;

	private ServerSocket listener;

	private static Pattern rpcurl = Pattern.compile("rpc/([a-zA-Z0-9_-]+?)\\.([a-zA-Z0-9_-]+?)(?:\\?(.*))?");

	public String[] helpApi = {
		  "Http is a neat plugin to allow your plugins to expose content onto a"
		+ " web page. All you need to do is supply generic calls of type 'web',"
		+ " and Http will call them when it's asked to by its web server.",
		  "Your 'web' generic calls should be able to accept as arguments a"
		+ " PrintWriter, a String and a String[]. The first should be used for"
		+ " output (including headers), the second is the parameter string"
		+ " passed after the ? (if any), and the third is a two element array"
		+ " containing the address and hostname of the caller."
	};

	public Http (final Modules mods, final IRCInterface irc) throws ChoobException
	{
		try {
			String portString = (String)mods.plugin.callAPI("Options", "GetGeneralOption", "PortNumber");
			if (portString != null) {
				portNumber = Integer.parseInt(portString);
			} else {
				portString = optionsGeneralDefaults[0];
			}
			externalName = (String)mods.plugin.callAPI("Options", "GetGeneralOption", "ExternalName");
		}
		catch (final ChoobNoSuchPluginException e) {

		}


		// First, try to get the socket from the old server
		try
		{
			listener = (ServerSocket)mods.plugin.callAPI("Http", "GetSocket");
		}
		catch (final ChoobNoSuchPluginException e)
		{
//			System.out.println("No such plugin...");
//			e.printStackTrace();
		}

		if (listener == null)
		{
			// OK, that failed. Try to make one ourselves?
			try
			{
				listener = new ServerSocket(portNumber);
				listener.setSoTimeout(1); // Minimal timeout
			}
			catch (final IOException f)
			{
				// OK, now give up!
				throw new ChoobException("Can't open the web socket, nor get the old instance...", f);
			}
		}

		this.mods = mods;
		this.irc = irc;

		mods.interval.callBack(null, 500);
	}

	public void interval(final Object param)
	{
		// XXX this shouldn't be at the start...
		mods.interval.callBack(null, 500);

		// Listener closed
		if (listener == null)
			return;

		Socket sock = null;
		PrintWriter out = null;
		BufferedReader in = null;
		try
		{
			sock = listener.accept();
		}
		catch (final SocketTimeoutException e)
		{
			// This is fine.
			return;
		}
		catch (final IOException e)
		{
			// Oh dear...
			System.err.println("Ooops, some problem while attempting to accept a socket:");
			e.printStackTrace();
			return;
		}

		// Stuff
		try
		{
			out = new PrintWriter(sock.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
			final StringBuffer headers = new StringBuffer();
			String inp;
			while ((inp = in.readLine()) != null && !inp.equals(""))
			{
				headers.append(inp);
				headers.append("\n");
			}
			System.out.print(headers);
			final Pattern pa = Pattern.compile(".*GET /(.*?) HTTP/1\\..\n.*");
			final Matcher ma = pa.matcher(headers);


			final boolean b=ma.find();
			System.out.println(b ? "Match" : "Nomatch");


			// http://www.w3.org/TR/html40/appendix/notes.html#non-ascii-chars

			String url = null;
			try
			{
				url = URLDecoder.decode(ma.group(1), "UTF-8").trim();

				if (b)
					System.out.println(url);

				final Matcher mo=rpcurl.matcher(url);
				if (url.length() > 5 && url.substring(0,6).equals("store/"))
				{
					out.println("HTTP/1.0 200 OK");
					out.println("Content-Type: text/plain");
					out.println();

					final String hash = url.substring(6).replaceAll("\"", "\\\"");

					System.out.println("\"" + hash + "\"");
					try
					{
						final List<HashedStringObject> res = mods.odb.retrieve(HashedStringObject.class, "WHERE hash = \"" + mods.odb.escapeString(hash) + "\"");
						if (res.size() != 0)
							out.println(res.get(0).string);
						else
							out.println("No such object: " + hash);
					}
					catch (final Throwable e)
					{
						System.err.println("Error retreiving object ID from database:");
						e.printStackTrace();
						out.println("Error retreiving object ID " + hash);
					}
				}
				else if (mo.matches())
				{
					try
					{
						mods.plugin.callGeneric(mo.group(1), "web", mo.group(2) != null ? mo.group(2) : "", out, (mo.group(3) != null ? mo.group(3) : ""), new String[] { sock.getInetAddress().getHostAddress(), sock.getInetAddress().getHostName()});
					}
					catch (final Exception e)
					{
						out.println("Error: " + e);
						e.printStackTrace();
					}
				}
				else
				{
					out.println("HTTP/1.0 404 Not Found");
					out.println("Content-Type: text/plain");
					out.println();

					// Note that this reply is intentionally really short to trigger prettifying in certain browsers.
					out.println("Oop, no pages here.");
				}
			}
			catch (final UnsupportedEncodingException e)
			{
				out.println("HTTP/1.0 500 Internal Server Error");
				out.println("Content-Type: text/plain");
				out.println();
				out.println("Badly formatted URL: " + ma.group(1));
			}

			sock.close();
			sock = null;
		}
		catch (final IOException e)
		{
			System.err.println("IO Exception while processing HTTP request:");
			e.printStackTrace();
		}
		finally
		{
			if (out != null)
				out.close();
			try
			{
				if (in != null)
					in.close();
			}
			catch (final Exception e)
			{
				e.printStackTrace();
			}
			try
			{
				if (sock != null)
					sock.close();
			}
			catch (final Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	public String apiGetRPCURL() throws ChoobException
	{
		return apiGetRPCURL("");
	}

	public String apiGetRPCURL(final String rpcName) throws ChoobException
	{
		final String pluginName = ChoobThread.getPluginName(1);
		if (pluginName == null)
			throw new ChoobException("Not called from a plugin, can't get RPC URL");

		String address = externalName;

		if (address == null || address.isEmpty()) {
			try {
				address = InetAddress.getLocalHost().getHostAddress();
			} catch (final UnknownHostException e) {
				throw new ChoobException("Your network appears to be really, really broken.");
			}
		}

		return "http://" + address + ":" + portNumber + "/rpc/" + pluginName + "." + rpcName;
	}

	public void webNickServ(final PrintWriter out, final String args, final String[] from)
	{
		try
		{
			out.println(args + " (" + mods.plugin.callAPI("NickServ", "Check", args) + ")");
		}
		catch (final Exception e)
		{
			out.println("ERROR!");
			e.printStackTrace();
		}
	}

	public void webPants(final PrintWriter out, final String extra, final String[] info)
	{
		out.println("Badgers!");
	}

	public String[] helpCommandClose = {
		"Close the server socket in case of trouble."
	};
	public void commandClose(final Message mes)
	{
		if (!mods.security.hasNickPerm(new ChoobPermission("plugins.http.close"), mes))
		{
			irc.sendContextReply(mes, "You lack authority!");
			return;
		}

		if (listener==null)
			return;

		try
		{
			listener.close();
		}
		catch (final IOException e)
		{
			irc.sendContextReply(mes, "Couldn't close socket: " + e);
			return;
		}
		listener = null;
		irc.sendContextReply(mes, "OK, closed!");
	}

	public ServerSocket apiGetSocket()
	{
		final String name = mods.security.getPluginName(1);
		System.out.println("Plugin name is: " + name);
		if ( name != null && name.toLowerCase().equals("http") )
		{
			return listener;
		}
		return null;
	}

	public String apiStoreString(final String s) throws ChoobException
	{
		final HashedStringObject hso=new HashedStringObject();
		hso.string=s;
		hso.hash=((Integer)(hso.string.hashCode()%128+256)).toString();

		final List<HashedStringObject> res = mods.odb.retrieve(HashedStringObject.class, "WHERE hash = '" + mods.odb.escapeString(hso.hash) + "'");

		for (final HashedStringObject li : res)
			mods.odb.delete(li);

		mods.odb.save(hso);

		String address = externalName;

		if (address == null || address.isEmpty()) {
			try
			{
				address = InetAddress.getLocalHost().getHostAddress();
			}
			catch (final UnknownHostException e)
			{
				throw new ChoobException("Your network appears to be really, really broken.");
			}
		}

		return "http://" + address + ":" + portNumber + "/store/" + hso.hash;
	}
}
