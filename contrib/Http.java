import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.net.*;
import java.io.*;
import java.util.regex.*;

public class HashedStringObject
{
	public int id;
	public String hash;
	public String string;
}

public class Http
{
	final int portNumber = 8023;

	// If this is null, the local machine's ip will be used.
	final String externalName =
		null
	;

	public String[] info()
	{
		return new String[] {
			"Plugin that implements a simple HTTP server.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private Modules mods;
	private IRCInterface irc;

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

	public Http (Modules mods, IRCInterface irc) throws ChoobException
	{
		// First, try to get the socket from the old server
		try
		{
			listener = (ServerSocket)mods.plugin.callAPI("Http", "GetSocket");
		}
		catch (ChoobNoSuchPluginException e)
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
			catch (IOException f)
			{
				// OK, now give up!
				throw new ChoobException("Can't open the web socket, nor get the old instance...", f);
			}
		}

		this.mods = mods;
		this.irc = irc;

		mods.interval.callBack(null, 500);
	}

	public void interval(Object param)
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
		catch (SocketTimeoutException e)
		{
			// This is fine.
			return;
		}
		catch (IOException e)
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
			StringBuffer headers = new StringBuffer();
			String inp;
			while ((inp = in.readLine()) != null && !inp.equals(""))
			{
				headers.append(inp);
				headers.append("\n");
			}
			System.out.print(headers);
			Pattern pa = Pattern.compile(".*GET /(.*?) HTTP/1\\..\n.*");
			Matcher ma = pa.matcher(headers);


			boolean b=ma.find();
			System.out.println(b ? "Match" : "Nomatch");

			// http://www.w3.org/TR/html40/appendix/notes.html#non-ascii-chars

			boolean err = false;
			String url = null;
			try
			{
				url = URLDecoder.decode(ma.group(1), "UTF-8").trim();
			}
			catch (UnsupportedEncodingException e)
			{
				err = true;
			}

			if (b)
				System.out.println(url);

			Matcher mo=rpcurl.matcher(url);

			if (err)
			{
				out.println("HTTP/1.0 500 Internal Server Error");
				out.println("Content-Type: text/plain");
				out.println();
				out.println("Badly formatted URL: " + ma.group(1));
			}
			else if (url.length() > 5 && url.substring(0,6).equals("store/"))
			{
				out.println("HTTP/1.0 200 OK");
				out.println("Content-Type: text/plain");
				out.println();

				String hash = url.substring(6).replaceAll("\"", "\\\"");

				System.out.println("\"" + hash + "\"");
				try
				{
					List<HashedStringObject> res = mods.odb.retrieve(HashedStringObject.class, "WHERE hash = \"" + mods.odb.escapeString(hash) + "\"");
					if (res.size() != 0)
						out.println(res.get(0).string);
					else
						out.println("No such object: " + hash);
				}
				catch (Throwable e)
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
				catch (Exception e)
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

			sock.close();
			sock = null;
		}
		catch (IOException e)
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
			catch (Exception e)
			{
				e.printStackTrace();
			}
			try
			{
				if (sock != null)
					sock.close();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	public String apiGetRPCURL() throws ChoobException
	{
		return apiGetRPCURL("");
	}

	public String apiGetRPCURL(String rpcName) throws ChoobException
	{
		String pluginName = ChoobThread.getPluginName(1);
		if (pluginName == null)
			throw new ChoobException("Not called from a plugin, can't get RPC URL");
		
		String address = externalName;
		
		if (address == null) {
			try {
				address = InetAddress.getLocalHost().getHostAddress();
			} catch (UnknownHostException e) {
				throw new ChoobException("Your network appears to be really, really broken.");
			}
		}
		
		return "http://" + address + ":" + portNumber + "/rpc/" + pluginName + "." + rpcName;
	}

	public void webNickServ(PrintWriter out, String args, String[] from)
	{
		try
		{
			out.println(args + " (" + mods.plugin.callAPI("NickServ", "Check", args) + ")");
		}
		catch (Exception e)
		{
			out.println("ERROR!");
			e.printStackTrace();
		}
	}

	public void webPants(PrintWriter out, String extra, String[] info)
	{
		out.println("Badgers!");
	}

	public String[] helpCommandClose = {
		"Close the server socket in case of trouble."
	};
	public void commandClose(Message mes)
	{
		if (!mods.security.hasPerm(new ChoobPermission("plugins.http.close"), mes))
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
		catch (IOException e)
		{
			irc.sendContextReply(mes, "Couldn't close socket: " + e);
			return;
		}
		listener = null;
		irc.sendContextReply(mes, "OK, closed!");
	}

	public ServerSocket apiGetSocket()
	{
		String name = mods.security.getPluginName(1);
		System.out.println("Plugin name is: " + name);
		if ( name != null && name.toLowerCase().equals("http") )
		{
			return listener;
		}
		return null;
	}

	public String apiStoreString(String s) throws ChoobException
	{
		HashedStringObject hso=new HashedStringObject();
		hso.string=s;
		hso.hash=((Integer)((hso.string.hashCode()%128)+256)).toString();

		List<HashedStringObject> res = mods.odb.retrieve(HashedStringObject.class, "WHERE hash = '" + mods.odb.escapeString(hso.hash) + "'");

		for (HashedStringObject li : res)
			mods.odb.delete(li);

		mods.odb.save(hso);

		String address = externalName;

		if (address == null)
			try
			{
				address = InetAddress.getLocalHost().getHostAddress();
			}
			catch (UnknownHostException e)
			{
				throw new ChoobException("Your network appears to be really, really broken.");
			}

		return "http://" + address + ":" + portNumber + "/store/" + hso.hash;

	}
}
