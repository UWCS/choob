import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jibble.pircbot.PircBot;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.ContextEvent;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.ServerResponse;


public class Where
{
	Modules mods;
	IRCInterface irc;

	abstract class Callback
	{
		Callback(ContextEvent con)
		{
			target = con;
		}

		final ContextEvent target;

		abstract void complete(Details d);

	}

	Callback fromPredicate(ContextEvent con, final Predicate p, final String msg)
	{
		return new Callback(con)
		{
			void complete(Details d)
			{
				List<String> hitted = new ArrayList<String>();
				for (Entry<String, Set<InetAddress>> entr : d.users.entrySet())
					for (InetAddress add : entr.getValue())
						if (p.hit(add))
							hitted.add(entr.getKey());

				Collections.sort(hitted);
				irc.sendContextReply(target, hitted.size() + " " + (hitted.size() == 1 ? "person" : "people") + " (" + hrList(hitted) + ") " + (hitted.size() == 1 ? "is" : "are") + " " + msg + ".");
			}
		};
	}

	abstract class Predicate
	{
		abstract boolean hit(InetAddress add);
	}

	class Details
	{
		Details(Callback c)
		{
			users = new HashMap<String, Set<InetAddress>>();
			callback = c;
			localmap = buildLocalMap();
		}

		Callback callback;
		// Username -> addresses
		Map<String, Set<InetAddress>> localmap;

		// Nick -> host.
		Map<String, Set<InetAddress>> users;
	}

	// (Target) channel -> Outstanding queries in it.
	Map<String, Queue<Details>> outst = new HashMap<String, Queue<Details>>();

	public Where(Modules mods, IRCInterface irc)
	{
		this.irc = irc;
		this.mods = mods;
		whatExtract = Pattern.compile("^" + irc.getNickname() + " ([^ ]+) (.*)$");
	}

	final Pattern whatExtract;

	<T>	boolean matches(Pattern p, T o)
	{
		return p.matcher(o.toString()).find();
	}

	// "User", hostname, server, nick, ...
	final Pattern splitUp = Pattern.compile("^([^ ]+) ([^ ]+) ([^ ]+) ([^ ]+) .*");
	public synchronized void onServerResponse(ServerResponse resp)
	{
		// Ensure resp is related to WHO, and remember if we're at the end. I'm sure there's a sane way to write this.
		final boolean atend = resp.getCode() == PircBot.RPL_ENDOFWHO;
		if (!(atend || resp.getCode() == PircBot.RPL_WHOREPLY))
			return;

		Matcher ma;
		if (!(ma = whatExtract.matcher(resp.getResponse())).matches())
			return; // Lol whut.

		final String aboutWhat = ma.group(1);
		final String tail = ma.group(2);

		Queue<Details> detst = outst.get(aboutWhat);
		if (detst == null || detst.isEmpty())
		{
			// The server has sent us a "WHO" line for a channel we don't care about.
			// Ignoring when it hates us, and we're hitting a RAAAAAAACE CONDITIONS..
			// it does this when we asked for a WHO nick. Assume that this is the only one we're going to get.

			if (!(ma = splitUp.matcher(tail)).matches() || (detst = outst.get(ma.group(4))) == null) // Read the nick.
				return; // If we wern't looking for this either, just bail.
		}

		Details d = detst.peek();
		if (d == null)
			return; // Lol whut.

		if (atend)
		{
			d.callback.complete(d);
			detst.remove();
		}
		else
		{
			if (!(ma = splitUp.matcher(tail)).matches())
				return; // Lol whut.

			try
			{
				// D.users is Nick -> Ip.
				// If the user is local, attempt to hax their real ip.
				InetAddress toStore = InetAddress.getByName(ma.group(2));
				final String nick = ma.group(4);
				Set<InetAddress> newones;

				Set<InetAddress> addto;

				if ((addto = d.users.get(nick)) == null)
					d.users.put(nick, addto = new HashSet<InetAddress>());

				if (!isLocal(toStore) || (newones = d.localmap.get(ma.group(1))) == null)
					addto.add(toStore);
				else
					addto.addAll(newones);

			}
			catch (UnknownHostException e)
			{
				// Ignore, abort the put.
			}
		}
	}

	boolean isLocal(InetAddress add)
	{
		return matches(Pattern.compile("^localhost/127"), add) || matches(Pattern.compile("compsoc.sunion.warwick.ac.uk/.*"), add);
	}

	boolean isCampus(InetAddress add)
	{
		return !isLocal(add) && matches(Pattern.compile("/137.205"), add);
	}

	boolean isDCSLab(InetAddress add)
	{
		return matches(Pattern.compile("/137\\.205\\.11[23]"), add);
	}

	// Username -> set of addresses.
	Map<String, Set<InetAddress>> buildLocalMap()
	{
		Map<String, Set<InetAddress>> ret = new HashMap<String, Set<InetAddress>>();
		try
		{
			Process proc = Runtime.getRuntime().exec("/usr/bin/finger");

			// There's a nefarious reason why this is here.
			if (proc.waitFor() != 0)
				return ret;

			BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			br.readLine(); // Discard header.
			String s;
			Matcher ma;
			while ((s = br.readLine()) != null)
			{
				if ((ma = Pattern.compile("^([^ ]+) .* \\((.*?)(?::S\\.[0-9]+)?\\)$").matcher(s)).matches())
				{
					final String un = ma.group(1);
					if (ret.get(un) == null)
						ret.put(un, new HashSet<InetAddress>());
					ret.get(un).add(InetAddress.getByName(ma.group(2)));
				}
			}
		}
		catch (Throwable t)
		{
			// Discard:
		}
		return ret; // Don't care about part results.
	}

	<T> String hrList(List<T> el)
	{
		String ret = "";
		for (int i=0; i<el.size(); ++i)
		{
			ret += el.get(i).toString();
			if (i == el.size() - 2)
				ret += " and ";
			else if (i == el.size() - 1)
				ret += "";
			else
				ret += ", ";
		}
		return ret;
	}

	// mes only for command line.
	void goDo( Message mes, Callback c )
	{
		String what = mods.util.getParamString(mes).trim().intern();
		if (what.length() == 0)
			what = mes.getContext();

		goDo(what, c);
	}

	synchronized void goDo( String what, Callback c )
	{
		Queue<Details> d = outst.get(what);
		if (d == null)
			d = new LinkedList<Details>();

		d.add(new Details(c));

		outst.put(what, d);

		irc.sendRawLine("WHO " + what);
	}

	public void commandDebug(Message mes)
	{
		goDo(mes,
			new Callback(mes)
			{
				public void complete(Details d)
				{
					List<String> unres = new ArrayList<String>(), wcampus = new ArrayList<String>();
					for (Entry<String, Set<InetAddress>> entr : d.users.entrySet())
						for (InetAddress add : entr.getValue())
							if (isCampus(add))
								wcampus.add(entr.getKey());
							else if (isLocal(add))
								unres.add(entr.getKey());
							else
								System.out.println(add.toString());
					irc.sendContextReply(target, "Found " + d.users.size() + " people, " + wcampus.size() + " are on campus (" + hrList(wcampus) + "), " + hrList(unres) + " are unresolvable.");
				}
			}
		);
	}

	public void commandOnCampus(Message mes)
	{
		goDo(mes, fromPredicate(mes, new Predicate() { boolean hit(InetAddress add) { return isCampus(add); } }, "on campus"));
	}

	public void commandDCSLabs(Message mes)
	{
		goDo(mes, fromPredicate(mes, new Predicate() { boolean hit(InetAddress add) { return isDCSLab(add); } }, "in a DCS lab"));
	}

	public void commandRegex(Message mes)
	{
		Matcher ma = Pattern.compile("^(.+?)((?: [^ ]+)?)$").matcher(mods.util.getParamString(mes).trim());
		if (!ma.matches())
		{
			irc.sendContextReply(mes, "Expected: regex [what]");
			return;
		}
		String what = ma.group(2).trim();
		if (what.length() == 0)
			what = mes.getContext();

		final Pattern p = Pattern.compile(ma.group(1));

		goDo(what, fromPredicate(mes, new Predicate() { boolean hit(InetAddress add) { return matches(p, add); } }, "matching"));
	}
}
