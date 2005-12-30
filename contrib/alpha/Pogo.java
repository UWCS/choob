/** @author Faux */

import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;

import java.util.*;
import java.util.regex.*;

import java.net.*;
import java.io.*;

public class Pogo
{
	Modules mods;
	IRCInterface irc;

	static ServerThread servthread;

	public Map <String, ClientHandler> commands;
	public Map <Message, Stack> pipecommands;

	public Pogo(Modules mods, IRCInterface irc) throws ChoobException
	{
		this.mods=mods; this.irc=irc;

		commands=new HashMap<String, ClientHandler>();
		pipecommands=new HashMap<Message, Stack>();

		try
		{
			servthread=new ServerThread(this);
		}
		catch (IOException e)
		{
			throw new ChoobException("Couldn't set up server.");
		}

		servthread.start();
	}

	public void commandCommands( Message mes )
	{
		String s="";
		Iterator i=commands.keySet().iterator();
		while (i.hasNext())
			s+=i.next()+", ";
		irc.sendContextReply(mes, "Commands: " + s);
	}

	public void commandKill( Message mes )
	{
		mods.security.checkNickPerm(new ChoobPermission("plugin.pogo.kill"), mes);
		die();
		irc.sendContextReply(mes, "Dead.");
	}

	void clientDeath(ClientHandler c)
	{
		Iterator <Map.Entry<String, ClientHandler>> it = commands.entrySet().iterator();

		while (it.hasNext())
		{
			final Map.Entry<String, ClientHandler> entry = it.next();
			final String key = entry.getKey();
			final ClientHandler value = entry.getValue();

			if (value==c)
				it.remove();
		}
	}

	private void die()
	{
		ServerThread.running=false;
		try { ServerThread.serv.close(); } catch (Exception e) {}
		ServerThread.serv=null;
	}

	public String filterTriggerRegex = "";
	public void filterTrigger( Message mes )
	{
		String text = mes.getMessage();

		Matcher matcher = Pattern.compile(irc.getTriggerRegex(), Pattern.CASE_INSENSITIVE).matcher(text);
		if (!matcher.find())
			return;

		String[] items=text.split("\\|");
		if (items.length<=1)
			doCommand(text, mes);
		else
		{

			final TaggedMessage m = new TaggedMessage(mes);
			Stack <String>s = new Stack<String>();
			int i=items.length;
			while (i-->1)
				s.push(items[i]);

			pipecommands.put(m, s);

			doCommand(items[0], m);
		}
	}

	private void doCommand(String text, Message mes)
	{
		Matcher matcher = Pattern.compile(irc.getTriggerRegex(), Pattern.CASE_INSENSITIVE).matcher(text);

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

	void sendContextReply(Message mes, String text)
	{

		if (mes instanceof TaggedMessage)
		{
			final Stack cs=pipecommands.get(mes);
			if (cs!=null && !cs.empty())
			{
				final String t=cs.pop() + " " + text;
				System.out.println("Doing stacked command: " + t);
				doCommand(t, mes);
				return;
			}
			else
				pipecommands.remove(mes);
		}

		irc.sendContextReply(mes, text);
	}

	final class TaggedMessage extends Message
	{
		String mcontext;

		public String getContext()
		{
			return mcontext;
		}

		public TaggedMessage(Message old)
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

	public ServerThread(Pogo p) throws IOException
	{
		serv=new ServerSocket(3090);

		this.p=p;
	}

	public void run()
	{
		while (running)
			try
			{
				(new ClientHandler(serv.accept(), p)).start();
			}
			catch (IOException e)
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

	public ClientHandler(Socket cs, Pogo p) throws IOException
	{
		sock=cs;
		in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
		out = new PrintWriter(sock.getOutputStream(), true);
		this.p=p;
		contexts=new HashMap<String, Message>();
	}

	public void run()
	{
		String inputLine;
		try
		{
			while ((inputLine = in.readLine()) != null)
			{
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
						out.print("ERR in reg" + "\n");

				}
				else
				{
					ma=Pattern.compile("^([0-9]+)\\s+(.*)$").matcher(inputLine);
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
								Iterator<String>i=p.commands.keySet().iterator();
								StringBuilder s=new StringBuilder();
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
		catch (IOException e)
		{
			System.out.println(e.toString());
		}

		p.clientDeath(this);
	}

	public void inform(String command, Message context)
	{
		final String key = ((Integer)(Math.abs((new Random()).nextInt()))).toString();
		contexts.put(key, context);

		out.print(key + "\t" + command.replaceFirst("\\s+","\t") + "\n");
		out.flush();
	}
}