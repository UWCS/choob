/** @author Faux */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

public class Pogo
{
	public Modules mods;
	IRCInterface irc;

	static ServerThread servthread;

	public Map <String, ClientHandler> commands;
	public HashMap<Message,Stack<String>> pipecommands;

	public Pogo(final Modules mods, final IRCInterface irc) throws ChoobException
	{
		this.mods=mods; this.irc=irc;

		commands=new HashMap<String, ClientHandler>();
		pipecommands=new HashMap<Message, Stack<String>>();

		try
		{
			servthread=new ServerThread(this);
		}
		catch (final IOException e)
		{
			throw new ChoobException("Couldn't set up server.");
		}

		servthread.start();
	}

	public void commandCommands( final Message mes )
	{
		String s="";
		final Iterator<String> i=commands.keySet().iterator();
		while (i.hasNext())
			s+=i.next()+", ";
		irc.sendContextReply(mes, "Commands: " + s);
	}

	public void commandKill( final Message mes )
	{
		mods.security.checkNickPerm(new ChoobPermission("plugin.pogo.kill"), mes);
		die();
		irc.sendContextReply(mes, "Dead.");
	}

	void clientDeath(final ClientHandler c)
	{
		final Iterator <Map.Entry<String, ClientHandler>> it = commands.entrySet().iterator();

		while (it.hasNext())
		{
			final Map.Entry<String, ClientHandler> entry = it.next();
			final ClientHandler value = entry.getValue();

			if (value==c)
				it.remove();
		}
	}

	private void die()
	{
		ServerThread.running=false;
		try { ServerThread.serv.close(); } catch (final Exception e)
		{
			// What're we going to do?
		}
		ServerThread.serv=null;
	}

	public void commandInvoke( final Message mes )
	{
		final String text = mods.util.getParamString(mes);

		final String[] items=text.split("\\|");
		if (items.length<=1)
			doCommand(text, mes);
		else
		{

			final TaggedMessage m = new TaggedMessage(mes);
			final Stack <String>s = new Stack<String>();
			int i=items.length;
			while (i-->1)
				s.push(items[i]);

			pipecommands.put(m, s);

			doCommand(items[0], m);
		}
	}

	private void doCommand(String text, final Message mes)
	{
		System.out.println("New command: " + text);

		final Matcher matcher = Pattern.compile(irc.getTriggerRegex(), Pattern.CASE_INSENSITIVE).matcher(text);

		if (matcher.find())
			text = text.substring(matcher.end());

		text=text.trim();

		ClientHandler h;

		if (text.indexOf(' ')==-1) // No space
			h=commands.get(text);
		else
			h=commands.get(text.substring(0, text.indexOf(' ')));

		if (h!=null)
			h.inform(text, mes);

	}

	void sendContextReply(final Message mes, String text)
	{

		if (mes instanceof TaggedMessage)
		{
			final Stack<String> cs=pipecommands.get(mes);
			if (cs!=null && !cs.empty())
			{
				final String t=cs.pop() + " " + text;
				System.out.println("Doing stacked command: " + t);
				doCommand(t, mes);
				return;
			}
			pipecommands.remove(mes);
		}

		if (text.length() > 320)
			text = text.substring(0,320) + "...";

		irc.sendContextReply(mes, text);
	}

	final class TaggedMessage extends Message
	{
		String mcontext;

		@Override
		public String getContext()
		{
			return mcontext;
		}

		public TaggedMessage(final Message old)
		{
			super(old, "Pogobot sucks!");
			mcontext=old.getContext();
		}
	}
}

final class ServerThread extends Thread
{
	static Pogo p;

	static protected ServerSocket serv;

	static protected boolean running=true;

	public ServerThread(final Pogo p) throws IOException
	{
		System.out.println("New server");
		serv=new ServerSocket(3090);

		ServerThread.p=p;
	}

	@Override
	public void run()
	{
		while (running)
			try
			{
				new ClientHandler(serv.accept(), p).start();
			}
			catch (final IOException e)
			{
				System.out.println(e.toString());
				return;
			}
	}
}

final class ClientHandler extends Thread
{
	Socket sock;
	final PrintWriter out;
	final BufferedReader in;

	Pogo p;

	Map <String, Message> contexts;

	public ClientHandler(final Socket cs, final Pogo p) throws IOException
	{
		System.out.println("New clienthandler");
		sock=cs;
		in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
		out = new PrintWriter(sock.getOutputStream(), true);
		this.p=p;
		contexts=new HashMap<String, Message>();
	}

	@Override
	public void run()
	{
		String inputLine;
		try
		{
			System.out.println("[pogo] Waiting for line.");
			while ((inputLine = in.readLine()) != null)
			{
				System.out.println("line" + inputLine);
				Matcher ma=Pattern.compile("^REG\\s+([a-z]+)$").matcher(inputLine);
				if (ma.matches())
				{
					final String command=ma.group(1);

					if (!p.commands.containsKey(command))
					{
						p.commands.put(command, this);
						out.print("OK REG " + command + "\n");
					}
					else
						out.print("ERR in REG" + "\n");

				}
				else
				{
					ma=Pattern.compile("^([0-9\\.]+)\\s+(.*)$").matcher(inputLine);
					if (ma.matches())
					{
						final Message m=contexts.get(ma.group(1));
						if (m!=null)
						{
							p.sendContextReply(m, ma.group(2));
							contexts.remove(ma.group(1));
						}
						else
							out.print("ERR unrecognised command (invalid context?)" + "\n");
					}
					else
					{
						ma=Pattern.compile("^ISREG\\s+([a-z]+)$").matcher(inputLine);
						if (ma.matches())
							out.print((p.commands.containsKey(ma.group(1)) ? "OK ISREG " : "ERR ISREG ") + ma.group(1) + "\n");
						else
						{
							ma=Pattern.compile("^LSREG$").matcher(inputLine);
							if (ma.matches())
							{
								final Iterator<String>i=p.commands.keySet().iterator();
								final StringBuilder s=new StringBuilder();
								while (i.hasNext())
									s.append(i.next()).append(" ");
								out.print("OK LSREG " + s.toString() + "\n");
							}
							else
								out.print("ERR unrecognised command\n");
						}

					}
				}
				out.flush();

			}
		}
		catch (final IOException e)
		{
			System.out.println(e.toString());
		}

		p.clientDeath(this);
	}

	public void inform(final String command, final Message context)
	{
		final String key = Double.toString(Math.random());
		contexts.put(key, context);

		System.out.println("To plugin: '" + key + "\t" + command.replaceFirst("\\s+","\t") + "\n" + "'");

		out.print(key + "\t" + command.replaceFirst("\\s+","\t") + "\n");
		out.flush();
	}
}
