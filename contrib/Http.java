import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.*;
import java.net.*;
import java.io.*;
import java.util.regex.*;

// NB: Broken!

public class StupidServ extends Thread
{
	private ServerSocket serv=null;
	private Modules mods;
	private IRCInterface irc;
	public StupidServ(Modules mods, IRCInterface irc)
	{
		this.mods=mods;
		this.irc=irc;
	}

	public void dei()
	{
		try
		{
			serv.close();
			serv=null;
		}
		catch (Exception e)
		{}
	}

    public void run()
    {
		while (serv==null)
		{
			try
			{
				serv=new ServerSocket(12345);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}

			try
			{
				Thread.sleep(5000);
			}
			catch (InterruptedException e) {}
		}

		for (;;)
		{
			PrintWriter out = null;
			BufferedReader in = null;
			String inp;
			Socket sock = null;
			Pattern rpcurl=Pattern.compile("rpc/([a-zA-Z0-9_-]+?)\\.([a-zA-Z0-9_-]+?)(?:\\?(.*))?");
			try
			{
				if (serv==null)
					break;
				sock=serv.accept();
				if (sock==null)
					break;
				out = new PrintWriter(sock.getOutputStream(), true);
				in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
				String headers=new String();
				while ((inp = in.readLine()) != null && !inp.equals(""))
				{
					headers=headers + inp + "\n";
					//System.out.println(inp);
				}
				System.out.println(headers);
				Pattern pa = Pattern.compile(".*GET /(.*?) HTTP/1\\..\n.*");
 				Matcher ma = pa.matcher(headers);


 				boolean b=ma.find();
 				System.out.println(b ? "Match" : "Nomatch");

 				// http://www.w3.org/TR/html40/appendix/notes.html#non-ascii-chars

 				String url=URLDecoder.decode(ma.group(1), "UTF-8").trim();

 				if (b)
					System.out.println(url);

				Matcher mo=rpcurl.matcher(url);

				if (url.length() > 5 && url.substring(0,6).equals("store/"))
				{
					out.println("HTTP/1.0 200 OK");
					out.println("Content-Type: text/plain");
					out.println();

					System.out.println("\"" + url.substring(6) + "\"");
					List<HashedStringObject> res = mods.odb.retrieve(HashedStringObject.class, "WHERE hash = '" + url.substring(6) + "'");
					if (res.size() != 0)
						out.println(res.get(0).string);
				}
				else if (mo.matches())
				{
					/*
					out.println("HTTP/1.0 200 OK");
					out.println("Content-Type: text/plain");
					out.println();
					*/

					try
					{
						mods.plugin.callGeneric(mo.group(1), "web", mo.group(2), mods, irc, out, (mo.groupCount() == 3 ? mo.group(3) : ""), new String[] { sock.getInetAddress().getHostAddress(), sock.getInetAddress().getHostName()});
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
					out.println("Oop, no pages here.");
				}

				sock.close();
				sock = null;

			}
			catch (Exception e)
			{
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
    }
}

public class Http
{
	private StupidServ ss;
	public Http (Modules mods, IRCInterface irc)
	{
		ss=new StupidServ(mods, irc);
		ss.start();
	}

	public void commandShutdown( Message mes, Modules mods, IRCInterface irc )
	{
		ss.dei();
		ss.interrupt();
		irc.sendContextReply(mes, "Poof!");
	}

	public void apiPants(Modules mods, IRCInterface irc, PrintWriter out, String args, String[] from)
	{
		out.println("You asked me " + args + ", but I have no idea how to do your mum.");
	}

	public void apiNickServ(Modules mods, IRCInterface irc, PrintWriter out, String args, String[] from)
	{
		try
		{
			out.println(args + " (" + mods.plugin.callAPI("NickServ", "NickServCheck", irc, args) + ")");
		}
		catch (Exception e)
		{
			out.println("ERROR!");
			e.printStackTrace();
		}
	}

	public void webPants(Object[] a)
	{
		((PrintWriter)a[2]).println("Badgers!");
	}

	protected void finalize() throws Throwable
	{
		ss.dei();
		ss.interrupt();
		super.finalize();
	}


}
