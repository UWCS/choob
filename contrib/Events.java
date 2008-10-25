import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jibble.pircbot.Colors;

import uk.co.uwcs.choob.modules.DateModule;
import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobError;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

public class Events
{

	// As per: (deadlink) http://bermuda.warwickcompsoc.co.uk/UWCSWebsite/Members/silver/document.2005-10-15.5186402265/. Don't fear the slashes, they're cuddly.

	final Pattern events_pattern=Pattern.compile("(\\d+) \"([^\"]*)\" ([^ ]+) ([^ ]+) ([^ ]+) ([^ ]+) ([^ ]+) \"([^\"]*)\" \"((?:\\\\\"|\\\\\\\\|[^\"])*)\" \"((?:\\\\\"|\\\\\\\\|[^\"])*)\"");
	//  Id:                                         1        2          3       4       5       6       7         8                     9                                 10
	//  Name:                                       id       name     start   end <signup>code max   current    names</signup>        desc.                             location

	enum SignupCodes
	{
		FINISHED   , // X : finished
		HASSIGNUPS , // S : has signups (that aren't open yet)
		SIGNUPSMEM , // SN: non-guest signups open
		SIGNUPSOPEN, // SO: signups are open (for all)
		CANCELLED  , // C : cancelled
		NOSIGNUPS  , // - : no signups are required
		RUNNING    , // R : running
		UNKNOWN    , //   : Anything else, ie. an error code.
	}

	/** A class for holding information about a single event */
	private class EventItem
	{
		/** Constructor from strings, as will be fed from the events_pattern matches */
		public EventItem(
			final String sid,
			final String sname,
			final String sstart,
			final String send,
			final String ssignupCode,
			final String ssignupMax,
			final String ssignupCurrent,
			final String ssignupNames,
			final String sdesc,
			final String slocation
		)
		{
			id            = parseId(sid);
			name          = sname;
			start         = convertTimestamp(sstart);
			end           = send.equals("-") ? start : convertTimestamp(send);
			signupMax     = Integer.parseInt(ssignupMax);
			signupCurrent = Integer.parseInt(ssignupCurrent);

			// ssignupCode comes as a short code (suprisingly enough) that is explained next to the enum above. Decode it:
			if      (ssignupCode.equals("X"))  signupCode = SignupCodes.FINISHED   ;
			else if (ssignupCode.equals("S"))  signupCode = SignupCodes.HASSIGNUPS ;
			else if (ssignupCode.equals("SN")) signupCode = SignupCodes.SIGNUPSMEM ;
			else if (ssignupCode.equals("SO")) signupCode = SignupCodes.SIGNUPSOPEN;
			else if (ssignupCode.equals("C"))  signupCode = SignupCodes.CANCELLED  ;
			else if (ssignupCode.equals("-"))  signupCode = SignupCodes.NOSIGNUPS  ;
			else if (ssignupCode.equals("R"))  signupCode = SignupCodes.RUNNING    ;
			else /*  ssignupCode is unkown */  signupCode = SignupCodes.UNKNOWN    ;

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
			for (final String nam : ssignupNames.split(", "))
				signupNames.add(nam);
		}

		private final String name;
		private SignupCodes signupCode;

		public int id;
		public Date start;
		public Date end;
		public int signupMax;
		public int signupCurrent;
		public ArrayList<String> signupNames;
		public String shortdesc;
		public String longdesc;
		public String location;

		private Date convertTimestamp(final String timestamp)
		{
			return new Date(Long.parseLong(timestamp)*1000);
		}

		public boolean finished()
		{
			return signupCode == SignupCodes.FINISHED || new Date().compareTo(end) > 0;
		}

		public boolean cancelled()
		{
			return signupCode == SignupCodes.CANCELLED;
		}

		public boolean inprogress()
		{
			return signupCode == SignupCodes.RUNNING || !finished() && new Date().compareTo(start) > 0;
		}

		public String boldName()
		{
			return Colors.BOLD + name + Colors.NORMAL +
				(cancelled() ? " (" + Colors.BOLD + "cancelled!" + Colors.NORMAL + ")" : "") +
				(inprogress() && !cancelled() ? " (" + Colors.BOLD + "on right now!" + Colors.NORMAL + ")" : "");
		}

		public String boldNameShortDetails()
		{
			return boldName() +
				shortDetails();
		}

		public String shortDetails()
		{
			return " (" + id +")" +
				(!finished() && !cancelled() && !inprogress() ? " [" + microStampFromNow(start) + "]" : "");
		}

		/** Note: This means that the event accepts /some/ signups, not necessary all */
		public boolean acceptsSignups()
		{
			return signupCode == SignupCodes.SIGNUPSOPEN || signupCode == SignupCodes.SIGNUPSMEM;
		}

		public boolean hasSignups()
		{
			return signupCode == SignupCodes.HASSIGNUPS || acceptsSignups();
		}

		public String shortSignups()
		{
			if (!hasSignups()) // No signups, nothing to say.
				return "";

			if (acceptsSignups())
				return  " [" + shortSignupsOutOf() + "]";

			return "";
		}

		public String shortSignupsOutOf()
		{
			return signupCurrent + "/" + (signupMax == 0 ? "*" : Integer.toString(signupMax));
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

		public String getFromMatcher(final Matcher ma)
		{
			return ma.group(ordinal());
		}
	}

	public static final String announceChannel="#bots";
	private static final long checkInterval=60000; // The crond job is run every minute.

	private final Modules mods;
	private final IRCInterface irc;

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

	public Events(final Modules mods, final IRCInterface irc) throws ChoobError
	{
		this.mods = mods;
		this.irc = irc;
		try
		{
			eventsurl = new URL("http://faux.uwcs.co.uk/events.data");
		}
		catch (final MalformedURLException e)
		{
			throw new ChoobError("Error in constant data.");
		}
		mods.interval.callBack(null, checkInterval);
	}

	private String microStampFromNow(final Date d)
	{
		return mods.date.timeMicroStamp(d.getTime() - new Date().getTime());
	}

	public void interval(final Object param) throws ChoobException
	{
		final ArrayList<EventItem> ne = readEventsData();

		if (current!=null && !current.equals(ne))
		{
			// Generate a hashmap of current (ie. before the change) event ids -> eventitems.
			final HashMap<Integer, EventItem>curr=new HashMap<Integer, EventItem>();

			for (final EventItem c : current)
				curr.put(Integer.valueOf(c.id), c);


			// Now, go through the new items..
			for (final EventItem n : ne)
			{
				// Get the corresponding event from the old items
				final EventItem corr=curr.get(Integer.valueOf(n.id));

				if (corr==null)
					// It doesn't exist, notify people:
					irc.sendMessage(announceChannel,
						"New event! " + n.boldNameShortDetails() + "."
					);
				else
					// The event existed, do the signups differ?
					if (!corr.signupNames.equals(n.signupNames))
					{
						// Diff the lists.

						final HashSet <String> beforeSet = new HashSet<String>();
						final HashSet <String> afterSet  = new HashSet<String>();

						// Create a set of names for each list, before (being the current names) and after (being the new ones).
						for (final String name : corr.signupNames)
							beforeSet.add(name.trim());

						for (final String name : n.signupNames)
							afterSet.add(name.trim());

						final Iterator <String>it = beforeSet.iterator();

						// Go through the list of names that's were there "before". If it exists in the "after" list, remove it from both.
						while (it.hasNext())
							if (afterSet.remove(it.next()))
								it.remove();

						final StringBuilder sig=new StringBuilder();

						// Now, anything left in "before" is not in "after", so it's been removed:
						for (final String name : beforeSet)
							if (name.length()>1)
								sig.append("-").append(name).append(", ");

						// Same applies for "after", anything in here isn't in "before", so it's been added.
						for (final String name : afterSet)
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
								"Signups for " + n.boldNameShortDetails() +
								" now " + n.shortSignupsOutOf() +
								" (" + sigts + ")."
							);
						}
					}
			}
		}

		current = ne;
		mods.interval.callBack(null, checkInterval);
	}

	private class DateComparator implements Comparator<EventItem>
	{
		public int compare(final EventItem a, final EventItem b)
		{
			// Hax. This means that if the events have the same (well, within a few milliseconds of each other) start time, pick the one with the most signups.
			final int stt = new Date(a.start.getTime() - a.signupCurrent).compareTo(new Date(b.start.getTime() - b.signupCurrent));

			if (stt == 0)
				return a.end.compareTo(b.end);

			return stt;
		}

	}

	private ArrayList<EventItem> readEventsData() throws ChoobException
	{
		final ArrayList<EventItem> events=new ArrayList<EventItem>();
		Matcher ma;

		try
		{
			ma=mods.scrape.getMatcher(eventsurl, checkInterval, events_pattern);
		}
		catch (final IOException e)
		{
			throw new ChoobException("Error reading events data file.");
		}

		while (ma.find())
			events.add(new EventItem(
				Groups.ID           .getFromMatcher(ma),
				Groups.NAME         .getFromMatcher(ma),
				Groups.START        .getFromMatcher(ma),
				Groups.END          .getFromMatcher(ma),
				Groups.SIGNUPCODE   .getFromMatcher(ma),
				Groups.SIGNUPMAX    .getFromMatcher(ma),
				Groups.SIGNUPCURRENT.getFromMatcher(ma),
				Groups.SIGNUPNAMES  .getFromMatcher(ma),
				Groups.DESC         .getFromMatcher(ma),
				Groups.LOCATION     .getFromMatcher(ma)
			));

		Collections.sort(events, new DateComparator());

		return events;
	}

	public String[] helpCommandInfo = {
		"Get info about a given event",
		"<Key>",
		"<Key> is a key to use for event searching"
	};
	public void commandInfo(final Message mes) throws ChoobException
	{
		infoOn(mes);
	}

	private void infoOn(final Message mes) throws ChoobException
	{
		final String comp=mods.util.getParamString(mes).toLowerCase();
		if (comp.equals(""))
		{
			final ArrayList<EventItem> events=readEventsData();
			int c=events.size();

			if (events.isEmpty())
			{
				irc.sendContextReply(mes, "There are no events! :'(");
				return;
			}

			final StringBuilder rep = new StringBuilder();
			for (final EventItem ev : events)
			{
				rep.append(ev.boldName());
				if (events.size() < 8)
					rep.append(" at ").append(ev.location);

				rep.append(ev.shortDetails())
					.append(ev.inprogress() ? " (started " + mods.date.timeMicroStamp(new Date().getTime() - ev.start.getTime()) + " ago)" : "")
					.append(ev.shortSignups())
					.append(--c != 0 ? ", " : ".");
			}
			irc.sendContextReply(mes, "Events: " + rep.toString());
			return;
		}

		final int eid=parseId(comp);

		final ArrayList<EventItem> events = readEventsData();

		for (final EventItem ev : events)
			if (!ev.finished())
				if (ev.name.toLowerCase().indexOf(comp) != -1 || ev.id == eid)
				{
					final String signup;

					if (ev.hasSignups())
					{
						if (ev.signupCurrent != 0)
							signup = " Currently " + ev.signupCurrent + " signup" + (ev.signupCurrent == 1 ? "" : "s") +
								(ev.signupMax == 0 ? ", no limit." : " out of " + ev.signupMax + ".");
						else
							signup = " Nobody has signed up yet" +
								(ev.acceptsSignups() ?
									" even though signups are open." :
									", probably because signups aren't open yet!"
								);
					}
					else
						signup="";

					irc.sendContextReply(mes,
						ev.boldName() +
						" at " + ev.location +
						( !"".equals(ev.shortdesc) ? " (" + ev.shortdesc + ")" : "") +
						" (" + ev.id +
						") " + (ev.start == ev.end ?
						        "at " + DateModule.absoluteDateFormat(ev.start) :
						        "from " + DateModule.absoluteDateFormat(ev.start) + " to " + DateModule.absoluteDateFormat(ev.end)) +
						"." +
						signup
					);
					return;
				}

		irc.sendContextReply(mes, "Event not found.");
	}

	public String[] helpCommandSignup = {
		"Get a signup link for a given event.",
		"<Key>",
		"<Key> is a key to use for event searching"
	};
	public void commandSignup(final Message mes) throws ChoobException
	{
		final String comp=mods.util.getParamString(mes).toLowerCase();
		if (comp.equals(""))
		{
			irc.sendContextReply(mes, "Please name the event you want info on.");
			return;
		}

		final int eid=parseId(comp);

		final ArrayList<EventItem> events = readEventsData();

		final StringBuilder rep = new StringBuilder();

		for (final EventItem ev : events)
			if (!ev.finished())
				if (ev.name.toLowerCase().indexOf(comp) != -1 || ev.id == eid)
				{
					if (ev.acceptsSignups())
						irc.sendContextReply(mes,
							"Please use http://www.warwickcompsoc.co.uk/events/details/options?id=" + ev.id + "&action=signup to sign-up for " +
							ev.boldNameShortDetails() +
							"."
						);
					else
					{
						rep.append(ev.boldNameShortDetails()).append(" matched, but is not currently accepting sign-ups... ");
						continue;
					}
					return;
				}

		irc.sendContextReply(mes, rep.toString() + "Event not found.");
	}

	public String[] helpCommandLink = {
		"Get an information link for a given event.",
		"<Key>",
		"<Key> is a key to use for event searching"
	};
	public void commandLink(final Message mes) throws ChoobException
	{
		final String comp=mods.util.getParamString(mes).toLowerCase();
		if (comp.equals(""))
		{
			irc.sendContextReply(mes, "Please name the event you want info on.");
			return;
		}

		final int eid=parseId(comp);

		final ArrayList<EventItem> events=readEventsData();

		for (final EventItem ev : events)
			if (!ev.finished())
				if (ev.name.toLowerCase().indexOf(comp) != -1 || ev.id == eid)
				{
					irc.sendContextReply(mes, "http://uwcs.co.uk/society/events/details/" + ev.id + ".");
					return;
				}

		irc.sendContextReply(mes, "Event not found.");
	}

	public String[] helpCommandSignups = {
		"Get the signups list for a given event.",
		"<Key>",
		"<Key> is a key to use for event searching"
	};
	public void commandSignups(final Message mes) throws ChoobException
	{
		String eventName = mods.util.getParamString(mes).toLowerCase();
		String signupRegexp = ""; // You suck, Java.

		// If there is a "/" in the string, we split it up into the event
		// name and signup regexp. E.g.
		//   !events.signups some event /name/
		//   eventName = "some event"
		//   signupRegexp = "name"
		final int index = eventName.indexOf('/');
		boolean searching = index != -1;
		if (searching)
		{
			signupRegexp = eventName.substring(index + 1);
			if (signupRegexp.length() != 0)
				signupRegexp = signupRegexp.substring(0, signupRegexp.length() - 1).trim();
			else
				searching = false;
			eventName = eventName.substring(0, index).trim();
		}

		if (eventName.equals(""))
		{
			irc.sendContextReply(mes, "Please name the event you want info on.");
			return;
		}

		final int eid = parseId(eventName);
		final ArrayList<EventItem> events = readEventsData();

		for (final EventItem ev : events)
			if (!ev.finished())
				if (ev.name.toLowerCase().indexOf(eventName) != -1 || ev.id == eid)
				{
					if (ev.signupCurrent == 0)
					{
						irc.sendContextReply(mes,
							"No signups for " + ev.boldNameShortDetails() +
							" at " + ev.location +
							(ev.acceptsSignups() ?
								" even though signups are open." :
								", probably because signups aren't open yet!"
							)
						);
					}
					else
					{
						if (searching)
						{
							final List<String> names = new ArrayList<String>();
							final Pattern p = Pattern.compile(signupRegexp, Pattern.CASE_INSENSITIVE);
							for (final String n : ev.signupNames)
								if (p.matcher(n).find())
									names.add(n);
							if (names.size() > 0) {
								irc.sendContextReply(mes,
									"Signups matching /" + signupRegexp + "/i for " + ev.boldNameShortDetails() +
									" at " + ev.location +
									ev.shortSignups() + ": " +
									nameList(names, mes, 0, "") +
									"."
								);
							}
							else
							{
								irc.sendContextReply(mes,
									"No signups matched /" + signupRegexp + "/i for " + ev.boldNameShortDetails() +
									" at " + ev.location +
									ev.shortSignups() + "."
								);
							}
						}
						else
						{
							irc.sendContextReply(mes,
								"Signups for " + ev.boldNameShortDetails() +
								" at " + ev.location +
								ev.shortSignups() + ": " +
								nameList(ev.signupNames, mes, ev.signupMax, Colors.BOLD + "Reserves: " + Colors.NORMAL) +
								"."
							);
						}
					}
					return;
				}
		irc.sendContextReply(mes, "Event not found.");
	}

	public String[] helpCommandList = {
		"Get a list of events.",
	};
	public void commandList(final Message mes) throws ChoobException
	{
		infoOn(mes);
	}

	private static String nameList(final List<String> names, final Message mes, final int after, final String message)
	{
		final StringBuilder namelistb = new StringBuilder();
		int i=0;
		for (final String name : names)
		{
			if (i++ == after && after != 0)
				namelistb.append(message);
			namelistb.append(name).append(", ");
		}

		String namelist = namelistb.toString();

		// Remove the trailing commaspace.
		if (namelist.length() > 2)
			namelist = namelist.substring(0, namelist.length()-2);

		return namelist;
	}

	private static int parseId(final String s)
	{
		try
		{
			return Integer.parseInt(s);
		}
		catch (final NumberFormatException e)
		{
			return 0;
		}
	}
}
