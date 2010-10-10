import static uk.co.uwcs.choob.modules.UtilModule.hrList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jibble.pircbot.ReplyConstants;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.ChannelMessage;
import uk.co.uwcs.choob.support.events.ContextEvent;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.ServerResponse;

public class Where
{
	public String[] info()
	{
		return new String[] {
			"Location information plugin",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
	Modules mods;
	IRCInterface irc;
	private PrintStream logFile;

	private void minilog(final String s)
	{
		logFile.println(sdf.format(new Date()) + " - " + Integer.toHexString(hashCode()) + ": " + s);
	}

	//ignore hosts people screen from that we can't finger.
	//hardcoding ftw.
	private final static String[] IGNORED_HOSTS =
	{
		//raw
		"137.205.210.18/32",
		"raw.sunion.warwick.ac.uk/.*"
	};
	private final String channels[] = { "#compsoc", "#wuglug", "#bots", "#wug", "#choob" };
	private enum Location { Campus, DCS, Resnet }

	abstract class Callback
	{
		Callback(final ContextEvent con)
		{
			target = con;
		}

		final ContextEvent target;

		abstract void complete(Details d);

		void fail()
		{
			irc.sendContextReply(target, "Timeout expired while trying to service request.");
		}

	}

	Callback fromPredicate(final ContextEvent con, final Predicate p, final String msg)
	{
		return new Callback(con)
		{
			@Override
			void complete(final Details d)
			{
				final List<String> hitted = new ArrayList<String>();
				for (final Entry<String, Set<InetAddress>> entr : d.users.entrySet())
					for (final InetAddress add : entr.getValue())
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
		Details(final Callback c)
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
	Map<String, Queue<Details>> outst = makeMap();

	private static Map<String, Queue<Details>> makeMap()
	{
		return Collections.synchronizedMap(new HashMap<String, Queue<Details>>());
	}

	public synchronized void commandReset(final Message mes)
	{
		int failed = 0;
		for (final Entry<String, Queue<Details>> ent : outst.entrySet())
			for (final Details qu : ent.getValue())
			{
				qu.callback.fail();
				++failed;
			}
		outst = makeMap();
		irc.sendContextReply(mes, "Okay, purged " + failed + " item" + (failed == 1 ? "" : "s") + ".");
	}

	public Where(final Modules mods, final IRCInterface irc) throws IOException
	{
		this.irc = irc;
		this.mods = mods;
		final String whatRegex = "^BadgerBOT ([^ ]+) (.*)$";

		logFile = new PrintStream(new FileOutputStream(new File(System.getProperty("user.home")+"/where.log"), true));
		minilog("whatRegex: " + whatRegex);
		whatExtract = Pattern.compile(whatRegex);
	}

	final Pattern whatExtract;

	<T>	boolean matches(final Pattern p, final T o)
	{
		return p.matcher(o.toString()).find();
	}

	// InetAddress.getByName doesn't resolve textual ips (to addresses) by default, trigger it (and discard the result) such that toString() returns the hostname.
	// Note that the reverse lookups are cached (indefinitely) by Java, so that's not duplicated here.
	private InetAddress getByName(final String name) throws UnknownHostException
	{
		minilog("getByName: enter: " + name);
		final InetAddress temp = InetAddress.getByName(name);
		temp.getHostName();

		// above not part of logging
		minilog("getByName: exit: " + temp.getHostName());
		return temp;
	}

	private static String encode(final String s)
	{
		try
		{
			return URLEncoder.encode(s, "UTF-8");
		}
		catch (final UnsupportedEncodingException e)
		{
			return "FAIL";
		}
	}

	// "User", hostname, server, nick, ...
	final Pattern splitUp = Pattern.compile("^~?([^ ]+) ([^ ]+) ([^ ]+) ([^ ]+) .*");
	public synchronized void onServerResponse(final ServerResponse resp)
	{
		minilog("onServerResponse: enter: " + resp.getCode());
		// Ensure resp is related to WHO, and remember if we're at the end. I'm sure there's a sane way to write this.
		final boolean atend = resp.getCode() == ReplyConstants.RPL_ENDOFWHO;
		if (!(atend || resp.getCode() == ReplyConstants.RPL_WHOREPLY))
			return;

		Matcher ma;
		if (!(ma = whatExtract.matcher(resp.getResponse())).matches())
		{
			minilog("Lol whut: Couldn't parse server's reply: " + encode(resp.getResponse()));
			return; // Lol whut.
		}

		final String aboutWhat = ma.group(1);
		final String tail = ma.group(2);

		Queue<Details> detst = outst.get(aboutWhat);
		minilog(aboutWhat + " -> " + detst);

		if (detst == null || detst.isEmpty())
		{
			// The server has sent us a "WHO" line for a channel we don't care about.
			// Ignoring when it hates us, and we're hitting a RAAAAAAACE CONDITIONS..
			// it does this when we asked for a WHO nick. Assume that this is the only one we're going to get.
			minilog("Looks like a line for an individual: " + resp.getResponse());
			if (!(ma = splitUp.matcher(tail)).matches())
			{
				minilog("splitUp 1 didn't match >>" + encode(tail) + "<<");
				return;
			}

			if ((detst = outst.get(ma.group(4))) == null) // Read the nick.
			{
				minilog("...which we wern't expecting. BAIL.");
				return; // If we wern't looking for this either, just bail.
			}
		}
		else
			minilog("Looks like a line for a channel: " + encode(resp.getResponse()));

		final Details d = detst.peek();
		if (d == null)
		{
			minilog("Wtf? There are no outstanding incomming messages for this item (" + encode(aboutWhat) + ")");
			return; // Lol whut.
		}

		if (atend)
		{
			minilog("..and it was the end, so notify them.");
			d.callback.complete(d);
			detst.remove();
			minilog("done notifying");
		}
		else
		{
			minilog("..which is not at the end, continuing: ");
			if (!(ma = splitUp.matcher(tail)).matches())
			{
				minilog("splitUp 2 didn't match >>" + encode(tail) + "<<");
				return; // Lol whut.
			}

			try
			{
				// D.users is Nick -> Ip.
				// If the user is local, attempt to hax their real ip.
				final InetAddress toStore = getByName(ma.group(2));
				final String nick = ma.group(4);

				// Work out where we're going to add the nick.
				Set<InetAddress> addto;

				if ((addto = d.users.get(nick)) == null)
					d.users.put(nick, addto = new HashSet<InetAddress>());

				//ignore hosts we can't/don't want to check.
				if (shouldIgnore(toStore))
				{
					minilog("Ignore a host and don't store it.");
					return;
				}

				Set<InetAddress> newones;

				if (!isLocal(toStore) || (newones = d.localmap.get(ma.group(1))) == null)
					addto.add(toStore);
				else
					addto.addAll(newones);

			}
			catch (final UnknownHostException e)
			{
				minilog("Warning: Couldn't resolve real ip for " + ma.group(4) + ", not a problem: " + ma.group(2));
				// Ignore, abort the put.
			}
		}
	}

	boolean isLocal(final InetAddress add)
	{
		return matches(Pattern.compile("/127.*"), add) ||
			matches(Pattern.compile("/137.205.210.240"), add);
	}

	boolean shouldIgnore(final InetAddress add)
	{
		for (final String ignoreHost : IGNORED_HOSTS)
		{
			if (matches(Pattern.compile(ignoreHost),add))
			{
				return true;
			}
		}
		return false;
	}

	boolean isCampus(final InetAddress add)
	{
		return !isLocal(add) && matches(Pattern.compile("/137\\.205"), add);
	}

	boolean isResnet(final InetAddress add)
	{
		return !isLocal(add) &&
			(matches(Pattern.compile("\\.res\\.warwick\\.ac\\.uk/"), add)
					|| matches(Pattern.compile("/137\\.205\\.32\\."), add));
	}

	boolean isDCS(final InetAddress add)
	{
		return matches(Pattern.compile("/137\\.205\\.11"), add);
	}

	Map<String, Set<InetAddress>> lastLoginMap(Map<String, Set<InetAddress>> localMap) {
		for (Map.Entry<String, Set<InetAddress>> user : localMap.entrySet()) {
			if(user.getValue().size() > 1) {
				try {
					final Process proc = Runtime.getRuntime().exec("finger "+user.getKey()+"| grep 'Last login' | cut -d ' ' -f 12");
					final BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
					user.setValue(Collections.singleton(InetAddress.getByName(in.readLine())));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return localMap;
	}

	// Username -> set of addresses.
	Map<String, Set<InetAddress>> buildLocalMap()
	{
		minilog("buildLocalMap: enter");
		final Map<String, Set<InetAddress>> ret = new HashMap<String, Set<InetAddress>>();
		try
		{
			final Process proc = Runtime.getRuntime().exec("/usr/bin/finger");

			// There's a nefarious reason why this is here.
			if (proc.waitFor() != 0)
				return ret;

			final BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			br.readLine(); // Discard header.
			String s;
			Matcher ma;
			while ((s = br.readLine()) != null)
			{
				// faux       Christopher West    pts/16         Feb 11 16:25 (82.16.66.10:S.0)
				// account-name SPACE some crap SPACE terminal SPACE date (ip:junk)$
				if ((ma = Pattern.compile("^([^ ]+) .* \\((.*?)(?::S\\.[0-9]+)?\\)$").matcher(s)).matches())
				{
					final String un = ma.group(1);
					if (ret.get(un) == null)
						ret.put(un, new HashSet<InetAddress>());
					ret.get(un).add(getByName(ma.group(2)));
				}
			}
		}
		catch (final Throwable t)
		{
			minilog("buildLocalMap: exception:" + t);
			// Discard:
		}
		minilog("buildLocalMap: exit");
		return lastLoginMap(ret); // Don't care about part results.
	}

	// mes only for command line.
	void goDo( final Message mes, final Callback c )
	{
		String what = mods.util.getParamString(mes).trim().intern();
		if (what.length() == 0)
			what = mes.getContext();

		goDo(what, c);
	}

	class IntervalArgs
	{
		final String what;
		final Details det;

		public IntervalArgs(final String what, final Details det)
		{
			this.what = what;
			this.det = det;
		}

	}

	public synchronized void interval(final Object o)
	{
		final IntervalArgs ia = (IntervalArgs) o;
		final Queue<Details> queue = outst.get(ia.what);
		if (queue == null)
		{
			minilog("Attempting to clean-up a non-event, whut.");
			return;
		}

		// If it's still present, remove and fail it.
		if (queue.remove(ia.det))
		{
			minilog("Interval failing " + ia.det);
			ia.det.callback.fail();
		}
		else
			minilog("Interval found only success.");
	}

	void goDo( final String what, final Callback c )
	{
		Queue<Details> d = outst.get(what);
		if (d == null)
			d = new LinkedList<Details>();

		final Details det = new Details(c);
		d.add(det);

		outst.put(what, d);
		mods.interval.callBack(new IntervalArgs(what, det), 10000);

		irc.sendRawLine("WHO " + what);
	}

	public void commandDebug(final Message mes)
	{
		goDo(mes,
			new Callback(mes)
			{
				@Override
				public void complete(final Details d)
				{
					final List<String> unres = new ArrayList<String>(), wcampus = new ArrayList<String>();
					for (final Entry<String, Set<InetAddress>> entr : d.users.entrySet())
						for (final InetAddress add : entr.getValue())
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

	private void hint(final Message mes)
	{
		if (!(mes instanceof ChannelMessage) && mods.util.getParams(mes,2).size() < 2)
			irc.sendContextReply(mes,"Hint, try " + mods.util.getParams(mes,2).get(0) + " <ChannelName> for relevant results in PM");
	}

	public String[] helpCommandOnCampus = {
		"Displays a list of people connected to IRC from IPs in the university of warwick IP range. Also works properly for those using screens on the server on which the plugin is running.",
		"[<ChannelName>]",
		"<ChannelName> is the name of the channel to return results for."
	};
	public void commandOnCampus(final Message mes)
	{
		hint(mes);
		goDo(mes, fromPredicate(mes, new Predicate() { @Override boolean hit(final InetAddress add) { return isCampus(add); } }, "on campus"));
	}

	public String[] helpCommandOnResnet = {
		"Displays a list of people connected to IRC from IPs in the University of Warwick resnet IP range. Also works properly for those using screens on the server on which the plugin is running.",
		"[<ChannelName>]",
		"<ChannelName> is the name of the channel to return results for."
	};
	public void commandOnResnet(final Message mes)
	{
		hint(mes);
		goDo(mes, fromPredicate(mes, new Predicate() { @Override boolean hit(final InetAddress add) { return isResnet(add); } }, "on resnet"));
	}

	public String[] helpCommandInDCS = {
		"Displays a list of people connected to IRC from IPs in the university of warwick department of computer science IP range. Also works properly for those using screens on the server on which the plugin is running.",
		"[<ChannelName>]",
		"<ChannelName> is the name of the channel to return results for."
	};
	public void commandInDCS(final Message mes)
	{
		hint(mes);
		goDo(mes, fromPredicate(mes, new Predicate() { @Override boolean hit(final InetAddress add) { return isDCS(add); } }, "in DCS"));
	}

	public String[] helpCommandRegex = {
		"Displays a list of people connected to IRC from IPs matching the specified regex",
		"<regex> [<ChannelName>]",
		"<Regex> is the regex to match people's IPs against",
		"<ChannelName> is the name of the channel to return results for."
	};
	public void commandRegex(final Message mes)
	{
		final Matcher ma = Pattern.compile("^(.+?)((?: [^ ]+)?)$").matcher(mods.util.getParamString(mes).trim());
		if (!ma.matches())
		{
			irc.sendContextReply(mes, "Expected: regex [what]");
			return;
		}
		String what = ma.group(2).trim();
		if (what.length() == 0)
			what = mes.getContext();

		final Pattern p = Pattern.compile(ma.group(1));

		goDo(what, fromPredicate(mes, new Predicate() { @Override boolean hit(final InetAddress add) { return matches(p, add); } }, "matching"));
	}

	class MutableInteger
	{
		private int value;

		public MutableInteger(final int value)
		{
			this.value = value;
		}

		public int preDecrement()
		{
			return --value;
		}
	}

	public void commandGlobalDCS(final Message mes)
	{
		global(mes, Location.DCS, "in DCS");
	}

	public void commandGlobalResnet(final Message mes)
	{
		global(mes, Location.Resnet, "on resnet");
	}

	public void commandGlobalCampus(final Message mes)
	{
		global(mes, Location.Campus, "on campus");
	}

	public void commandHostname(final Message mes) throws UnknownHostException
	{
		final List<String> params = new ArrayList<String>(mods.util.getParams(mes));
		String context = mes.getContext();

		if (params.size() < 2)
		{
			irc.sendContextReply(mes, "Usage: " + params.get(0) + " address [[address...] context]");
			return;
		}
		params.remove(0); // Command name.

		if (params.size() >= 2)
			context = params.remove(params.size()-1);

		final Set<InetAddress> ipees = new HashSet<InetAddress>();
		for (final String host : params)
			ipees.add(InetAddress.getByName(host));

		final String finalContext = context;

		goDo(context, fromPredicate(mes, new Predicate()
		{
			@Override
			boolean hit(final InetAddress add)
			{
				// Compares the IPs alone, ignoring the host string.
				for (final InetAddress ip : ipees)
					if (add.equals(ip))
						return true;
				return false;
			}
		}, "at the same place as " + hrList(params, " and/or ") +
			(finalContext.charAt(0) != '#' ? " (Did you really mean to look in " + finalContext + "?)" : "")));
	}

	private void global(final Message mes, final Location loc, final String str)
	{
		final Set<String> users = new HashSet<String>();
		final MutableInteger count = new MutableInteger(channels.length);

		for (final String channel : channels)
		{
			goDo(channel,
				new Callback(mes)
				{
					@Override
					public void complete(final Details d)
					{
						for (final Entry<String, Set<InetAddress>> entr : d.users.entrySet())
						{
							for (final InetAddress add : entr.getValue())
							{
								boolean flag = false;

								switch (loc)
								{
									case DCS:
										flag = isDCS(add);
										break;
									case Resnet:
										flag = isResnet(add);
										break;
									case Campus:
										flag = isCampus(add);
										break;
								}

								if (flag)
								{
									users.add(entr.getKey());
								}
							}
						}

						synchronized (count)
						{
							if (count.preDecrement() == 0)
							{
								irc.sendContextReply(target, "Total users " + str + ": " + users.size());
							}
						}
					}
				}
			);
		}
	}

	private ContextEvent context(final String channel) {
		return new ContextEvent() {
			@Override public String getContext() {
				return "#compsoc";
			}
		};
	}

	/**
	 * TODO: allow user settable hostnames
	 * Assumes 1..5,6..10,11..15 ...
	 * @return
	 */
	private InetAddress[][] buildCache(final String prefix, final PrintWriter err) {
		final InetAddress[][] cache = new InetAddress[5][5];
		for (int row = 0; row < 5; row++) {
			for (int index = 1; index < 6; index++) {
				final int n = row*5 + index;
				try {
					cache[row][index-1] = InetAddress.getByName(prefix+"-"+n+".dcs.warwick.ac.uk");
				} catch (UnknownHostException e) {
					err.write("Couldn't lookup "+prefix+"-"+n);
					cache[row][index-1] = null;
				}
			}
		}
		return cache;
	}

	/**
	 *
	 * @param out
	 * @param params
	 * @param user
	 */
	public void webStalker(final PrintWriter out, final String params, final String[] user) {
		out.println("HTTP/1.0 200 OK");
		out.println("Content-Type: text/html");
		out.println();
		out.println("<html><body><table>");

		final InetAddress[][] cache = buildCache("viglab", out);

		// this internal api really wants closures
		goDo("#compsoc", new Callback(context("#compsoc")) {
			@Override
			void complete(Details d) {
				// Calculate inverse
				final Map<InetAddress,Set<String>> inverse = new HashMap<InetAddress, Set<String>>();
				for (Map.Entry<String, Set<InetAddress>> person : d.localmap.entrySet()) {
					for (InetAddress addr : person.getValue()) {
						Set<String> people = inverse.get(addr);
						if(people == null) {
							people = new HashSet<String>();
							inverse.put(addr, people);
						}
						people.add(person.getKey());
					}
				}

				for (final InetAddress[] row : cache) {
					out.println("<tr>");
					for (final InetAddress pc: row) {
						out.println("<td>");
						final Set<String> people = inverse.get(pc);
						for (String name : people) {
							out.println("<p>"+name+"</p>");
						}
						out.println("</td>");
					}
					out.println("</tr>");
				}

				//d.localmap
				out.println("</table></body></html>");
			}
		});
	}
}
