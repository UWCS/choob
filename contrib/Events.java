import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import org.jibble.pircbot.Colors;

public class Events
{

	// As per: http://bermuda.warwickcompsoc.co.uk/UWCSWebsite/Members/silver/document.2005-10-15.5186402265/. Don't fear the slashes, they're cuddly.

	final Pattern events_pattern=Pattern.compile("(\\d+) \"([^\"]*)\" ([^ ]+) ([^ ]+) ([^ ]+) ([^ ]+) ([^ ]+) \"([^\"]*)\" \"((?:\\\\\"|\\\\\\\\|[^\"])*)\" \"((?:\\\\\"|\\\\\\\\|[^\"])*)\"");
	//  Id:                                         1        2          3       4       5       6       7         8                     9                                 10
	//  Name:                                       id       name     start   end <signup>code max   current    names</signup>        desc.                             location

	/** A class for holding information about a single event */
	private class EventItem
	{
		/** Constructor from strings, as will be fed from the events_pattern matches */
		public EventItem(
			String sid,
			String sname,
			String sstart,
			String send,
			String ssignupCode,
			String ssignupMax,
			String ssignupCurrent,
			String ssignupNames,
			String sdesc,
			String slocation
		)
		{
			id            = Integer.parseInt(sid);
			name          = sname;
			start         = convertTimestamp(sstart);
			end           = convertTimestamp(send);
			signupCode    = ssignupCode;
			signupMax     = Integer.parseInt(ssignupMax);
			signupCurrent = Integer.parseInt(ssignupCurrent);

			// The description comes in as one string, pipe-seperated.
			// What happens if it contains strictly> 1 pipe?
			String [] descParts=sdesc.split("\\|");
			if (descParts.length < 2)
				descParts = new String[] {"", ""}; // split acts unexpectedly with just "|", deal with it.

			shortdesc     = descParts[0];
			longdesc      = descParts[1];
			location      = slocation;

			// The names come in in csv, break them up.
			signupNames   = new ArrayList<String>();
			for (String name : ssignupNames.split(","))
				signupNames.add(name);
		}

		public int id;
		public String name;
		public Date start;
		public Date end;
		public String signupCode;
		public int signupMax;
		public int signupCurrent;
		public ArrayList<String> signupNames;
		public String shortdesc;
		public String longdesc;
		public String location;

		private Date convertTimestamp(String timestamp)
		{
			return new Date(Long.parseLong(timestamp)*(long)1000);
		}

		public boolean finished()
		{
			return (new Date()).compareTo(end) > 0;
		}

		public boolean inprogress()
		{
			return !finished() && (new Date()).compareTo(start) > 0;
		}
	}

	private enum Groups
	{
		_WHOLESTRING,
		ID,
		NAME,
		START,
		END,
		SIGNUPCODE,
		SIGNUPMAX,
		SIGNUPCURRENT,
		SIGNUPNAMES,
		DESC,
		LOCATION;

		public String getFromMatcher(Matcher ma)
		{
			return ma.group(ordinal());
		}
	}

	public static final String announceChannel="#bots";
	private static final long checkInterval=60000; // The crond job is run every minute.

	private Modules mods;
	private IRCInterface irc;

	private final URL eventsurl;

	private ArrayList<EventItem> current;

	public String[] info()
	{
		return new String[] {
			"CompSoc events watcher/info plugin.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	public Events(Modules mods, IRCInterface irc) throws ChoobError
	{
		this.mods = mods;
		this.irc = irc;
		try
		{
			eventsurl = new URL("http://faux.uwcs.co.uk/events.data");
		}
		catch (MalformedURLException e)
		{
			throw new ChoobError("Error in constant data.");
		}
		mods.interval.callBack(null, checkInterval);
	}

	private String microStampFromNow(Date d)
	{
		return mods.date.timeMicroStamp(d.getTime() - (new Date()).getTime());
	}

	public void interval(Object param) throws ChoobException
	{
		ArrayList<EventItem> ne = readEventsData();

		if (current!=null && !current.equals(ne))
		{
			ListIterator<EventItem> ni = ne.listIterator();

			// Generate a hashmap of current (ie. before the change) event ids -> eventitems.
			HashMap<Integer, EventItem>curr=new HashMap<Integer, EventItem>();

			for (EventItem c : current)
				curr.put(c.id, c);


			// Now, go through the new items..
			for (EventItem n : ne)
			{
				// Get the corresponding event from the old items
				EventItem corr=curr.get(n.id);

				if (corr==null)
					// It doesn't exist, notify people:
					irc.sendMessage(announceChannel,
						"New event! " + n.name +
						" at " + n.location + " in " +
						mods.date.timeMicroStamp(n.start.getTime() - (new Date()).getTime()) + "."
					);
				else
				{
					// The event existed, do the signups differ?
					if (!corr.signupNames.equals(n.signupNames))
					{
						// Diff the lists.

						HashSet <String> beforeSet = new HashSet<String>();
						HashSet <String> afterSet  = new HashSet<String>();

						// Create a set of names for each list, before (being the current names) and after (being the new ones).
						for (String name : corr.signupNames)
							beforeSet.add(name.trim());

						for (String name : n.signupNames)
							afterSet.add(name.trim());

						Iterator <String>it = beforeSet.iterator();

						// Go through the list of names that's were there "before". If it exists in the "after" list, remove it from both.
						while (it.hasNext())
							if (afterSet.remove(it.next()))
								it.remove();

						StringBuilder sig=new StringBuilder();

						// Now, anything left in "before" is not in "after", so it's been removed:
						for (String name : beforeSet)
							if (name.length()>1)
								sig.append("-").append(name).append(", ");

						// Same applies for "after", anything in here isn't in "before", so it's been added.
						for (String name : afterSet)
							if (name.length()>1)
								sig.append("+").append(name).append(", ");


						// Convert our stringbuilder to a String..
						String sigts = sig.toString();

						// If there's anything in the string.
						if (sigts.length()>2)
						{
							// Remove the trailing comma.
							sigts=sigts.substring(0, sigts.length()-2);

							// Announce the change.
							irc.sendMessage(announceChannel,
								"Signups for " + Colors.BOLD + n.name + Colors.NORMAL +
								" (" + n.id +
								") [" + microStampFromNow(n.start) +
								"] now " + n.signupCurrent + "/" + n.signupMax +
								" (" + sigts + ")."
							);
						}
					}
				}
			}
		}

		current = ne;
		mods.interval.callBack(null, checkInterval);
	}

	private ArrayList<EventItem> readEventsData() throws ChoobException
	{
		ArrayList<EventItem> events=new ArrayList<EventItem>();
		Matcher ma;

		try
		{
			ma=mods.scrape.getMatcher(eventsurl, checkInterval, events_pattern);
		}
		catch (IOException e)
		{
			throw new ChoobException("Error reading events data file.");
		}

		while (ma.find())
			events.add(new EventItem(
				Groups.ID.getFromMatcher(ma),
				Groups.NAME.getFromMatcher(ma),
				Groups.START.getFromMatcher(ma),
				Groups.END.getFromMatcher(ma),
				Groups.SIGNUPCODE.getFromMatcher(ma),
				Groups.SIGNUPMAX.getFromMatcher(ma),
				Groups.SIGNUPCURRENT.getFromMatcher(ma),
				Groups.SIGNUPNAMES.getFromMatcher(ma),
				Groups.DESC.getFromMatcher(ma),
				Groups.LOCATION.getFromMatcher(ma)
			));

		return events;
	}

	public String[] helpCommandInfo = {
		"Get info about a given event",
		"<Key>",
		"<Key> is a key to use for event searching"
	};
	public void commandInfo(Message mes) throws ChoobException
	{
		String comp=mods.util.getParamString(mes).toLowerCase();
		if (comp.equals(""))
		{
			irc.sendContextReply(mes, "Please name the event you want info on.");
			return;
		}

		int eid=0;
		try
		{
			eid=Integer.parseInt(comp);
		}
		catch (NumberFormatException e) {}


		ArrayList<EventItem> events = readEventsData();
		int c = events.size();

		// We can't use foreach here, as we need to go backwards.
		while (c-- > 0)
		{
			EventItem ev = events.get(c);

			if (!ev.finished())
				if (ev.name.toLowerCase().indexOf(comp) != -1 || ev.id == eid)
				{
					final String signup;

					if (ev.signupMax != 0)
					{
						if (ev.signupCurrent != 0)
							signup = " Currently " + ev.signupCurrent + " signup" + (ev.signupCurrent == 1 ? "" : "s") + " out of " + ev.signupMax + ".";
						else
							signup = " Nobody has signed up yet.";
					}
					else
						signup="";

					irc.sendContextReply(mes,
						Colors.BOLD + ev.name + Colors.NORMAL +
						" at " + ev.location +
						( !"".equals(ev.shortdesc) ? " (" + ev.shortdesc + ")" : "") +
						" (" + ev.id +
						") from " + absoluteDateFormat(ev.start) + " to " + absoluteDateFormat(ev.end) + "." +
						signup
					);
					return;
				}
		}
		irc.sendContextReply(mes, "Event not found.");
	}

	public String[] helpCommandSignup = {
		"Get a signup link for a given event.",
		"<Key>",
		"<Key> is a key to use for event searching"
	};
	public void commandSignup(Message mes) throws ChoobException
	{
		String comp=mods.util.getParamString(mes).toLowerCase();
		if (comp.equals(""))
		{
			irc.sendContextReply(mes, "Please name the event you want info on.");
			return;
		}

		int eid=0;
		try
		{
			eid=Integer.parseInt(comp);
		}
		catch (NumberFormatException e) { }

		ArrayList<EventItem> events = readEventsData();
		int c=events.size();

		StringBuilder rep = new StringBuilder();

		while (c-- > 0)
		{
			EventItem ev = events.get(c);

			if (!ev.finished())
				if (ev.name.toLowerCase().indexOf(comp) != -1 || ev.id == eid)
				{
					if (!ev.signupCode.equals("X") && !ev.signupCode.equals("-"))
						irc.sendContextReply(mes,
							"Please use http://www.warwickcompsoc.co.uk/events/details/options?id=" + ev.id + "&action=signup to sign-up for " +
							Colors.BOLD + ev.name + Colors.NORMAL +
							(!ev.finished() ? " [" + microStampFromNow(ev.start) + "]" : "") +
							"."
						);
					else
					{
						rep.append("Event ").append(ev.name).append(" matched, but does not accept sign-ups... ");
						continue;
					}
					return;
				}
		}
		irc.sendContextReply(mes, rep.toString() + "Event not found.");
	}

	public String[] helpCommandLink = {
		"Get an information link for a given event.",
		"<Key>",
		"<Key> is a key to use for event searching"
	};
	public void commandLink(Message mes) throws ChoobException
	{
		String comp=mods.util.getParamString(mes).toLowerCase();
		if (comp.equals(""))
		{
			irc.sendContextReply(mes, "Please name the event you want info on.");
			return;
		}

		ArrayList<EventItem> events=readEventsData();
		int c=events.size();

		int eid=0;
		try
		{
			eid=Integer.parseInt(comp);
		}
		catch (NumberFormatException e) { }

		while (c-- > 0)
		{
			EventItem ev= events.get(c);

			if (!ev.finished())
				if (ev.name.toLowerCase().indexOf(comp) != -1 || ev.id == eid)
				{
					irc.sendContextReply(mes, "http://www.warwickcompsoc.co.uk/events/details/?id=" + ev.id + ".");
					return;
				}
		}
		irc.sendContextReply(mes, "Event not found.");
	}

	public String[] helpCommandSignups = {
		"Get the signups list for a given event.",
		"<Key>",
		"<Key> is a key to use for event searching"
	};
	public void commandSignups(Message mes) throws ChoobException
	{
		String comp=mods.util.getParamString(mes).toLowerCase();
		if (comp.equals(""))
		{
			irc.sendContextReply(mes, "Please name the event you want info on.");
			return;
		}

		ArrayList<EventItem> events = readEventsData();
		int c = events.size();

		int eid=0;
		try
		{
			eid=Integer.parseInt(comp);
		}
		catch (NumberFormatException e) { }

		while (c-- > 0)
		{
			EventItem ev = events.get(c);
			if (!ev.finished())
				if (ev.name.toLowerCase().indexOf(comp) != -1 || ev.id == eid)
				{
					if (ev.signupCurrent == 0)
						irc.sendContextReply(mes,
							"No signups for " + Colors.BOLD + ev.name + Colors.NORMAL +
							" at " + ev.location + " (" + ev.id + ")" +
							" [" + microStampFromNow(ev.start) + "]" +
							"!"
						);
					else
						irc.sendContextReply(mes,
							"Signups for " + Colors.BOLD + ev.name + Colors.NORMAL  +
							" at " + ev.location + " (" + ev.id + ")" +
							" [" + microStampFromNow(ev.start) + "]" +
							(ev.signupMax != 0 ? " [" + ev.signupCurrent + "/" + ev.signupMax + "]" : "") + ": " +
							nameList(ev.signupNames, mes) +
							"."
						);
					return;
				}
		}
		irc.sendContextReply(mes, "Event not found.");
	}

	public String[] helpCommandList = {
		"Get a list of events.",
	};
	public void commandList(Message mes) throws ChoobException
	{
		ArrayList<EventItem> events=readEventsData();
		int c=events.size();

		if (c==0)
		{
			irc.sendContextReply(mes, "There are no events! :'(");
			return;
		}

		StringBuilder rep = new StringBuilder();
		while (c-- > 0)
		{
			EventItem ev= events.get(c);

			rep.append(Colors.BOLD + ev.name + Colors.NORMAL)
				.append(ev.finished() ? " (finished)" : "")
				.append(" at " + ev.location + " (" + ev.id + ")")
				.append(!ev.finished() ?
					(ev.inprogress() ?
						" (" + Colors.BOLD + "on right now" + Colors.NORMAL + ", started " + mods.date.timeMicroStamp((new Date()).getTime() - ev.start.getTime()) + " ago)"
					:
						" [" + microStampFromNow(ev.start) + "]"
					)
				:
					""
				)
				.append(ev.signupMax != 0 ? " [" + ev.signupCurrent + "/" + ev.signupMax + "]" : "")
				.append(c != 0 ? ", " : ".");
		}
		irc.sendContextReply(mes, "Events: " + rep.toString());
	}

	/** Convert an arraylist of names into a string, b'reaking them up to prevent pings if not in pm. */
	private static String nameList(ArrayList<String> names, Message mes)
	{
		String namelist = "";
		for (String name : names)
			namelist += name + ", ";

		// Remove the trailing commaspace.
		if (namelist.length() > 2)
			namelist = namelist.substring(0, namelist.length()-2);

		// If it's not in pm, break them up.
		if (!(mes instanceof PrivateEvent))
			namelist = namelist.replaceAll("([a-zA-Z])([^, ]+)","$1'$2");

		return namelist;
	}

	private static String absoluteDateFormat(Date da)
	{
		return new SimpleDateFormat("EEEE d MMM H:mma").format(da);
	}

}
