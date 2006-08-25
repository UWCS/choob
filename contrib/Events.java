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
			id            = parseId(sid);
			name          = sname;
			start         = convertTimestamp(sstart);
			end           = (send.equals("-") ? start : convertTimestamp(send));
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
			for (String name : ssignupNames.split(", "))
				signupNames.add(name);
		}

		private String name;
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

		private Date convertTimestamp(String timestamp)
		{
			return new Date(Long.parseLong(timestamp)*(long)1000);
		}

		public boolean finished()
		{
			return signupCode == SignupCodes.FINISHED || (new Date()).compareTo(end) > 0;
		}

		public boolean cancelled()
		{
			return signupCode == SignupCodes.CANCELLED;
		}

		public boolean inprogress()
		{
			return signupCode == SignupCodes.RUNNING || !finished() && (new Date()).compareTo(start) > 0;
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
				" (" + id +") " +
				(!finished() && !cancelled() && !inprogress() ? "[" + microStampFromNow(start) + "]" : "");
		}

		/** Note: This means that the event accepts /some/ signups, not necessary all */
		public boolean acceptsSignups()
		{
			return signupCode == SignupCodes.SIGNUPSOPEN || signupCode == SignupCodes.SIGNUPSMEM;
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
								"Signups for " + n.boldNameShortDetails() +
								" now " + n.signupCurrent + "/" + n.signupMax +
								" (" + sigts + ")."
							);
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

		final int eid=parseId(comp);

		ArrayList<EventItem> events = readEventsData();

		for (EventItem ev : events)
			if (!ev.finished())
				if (ev.name.toLowerCase().indexOf(comp) != -1 || ev.id == eid)
				{
					final String signup;

					if (ev.signupMax != 0)
					{
						if (ev.signupCurrent != 0)
							signup = " Currently " + ev.signupCurrent + " signup" + (ev.signupCurrent == 1 ? "" : "s") + " out of " + ev.signupMax + ".";
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
						        "at " + absoluteDateFormat(ev.start) :
						        "from " + absoluteDateFormat(ev.start) + " to " + absoluteDateFormat(ev.end)) +
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
	public void commandSignup(Message mes) throws ChoobException
	{
		String comp=mods.util.getParamString(mes).toLowerCase();
		if (comp.equals(""))
		{
			irc.sendContextReply(mes, "Please name the event you want info on.");
			return;
		}

		final int eid=parseId(comp);

		ArrayList<EventItem> events = readEventsData();

		StringBuilder rep = new StringBuilder();

		for (EventItem ev : events)
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
	public void commandLink(Message mes) throws ChoobException
	{
		String comp=mods.util.getParamString(mes).toLowerCase();
		if (comp.equals(""))
		{
			irc.sendContextReply(mes, "Please name the event you want info on.");
			return;
		}

		final int eid=parseId(comp);

		ArrayList<EventItem> events=readEventsData();

		for (EventItem ev : events)
			if (!ev.finished())
				if (ev.name.toLowerCase().indexOf(comp) != -1 || ev.id == eid)
				{
					irc.sendContextReply(mes, "http://www.warwickcompsoc.co.uk/events/details/?id=" + ev.id + ".");
					return;
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

		final int eid=parseId(comp);
		ArrayList<EventItem> events = readEventsData();

		for (EventItem ev : events)
			if (!ev.finished())
				if (ev.name.toLowerCase().indexOf(comp) != -1 || ev.id == eid)
				{
					if (ev.signupCurrent == 0)
						irc.sendContextReply(mes,
							"No signups for " + ev.boldNameShortDetails() +
							" at " + ev.location +
							(ev.acceptsSignups() ?
								" even though signups are open." :
								", probably because signups aren't open yet!"
							)
						);
					else
						irc.sendContextReply(mes,
							"Signups for " + ev.boldNameShortDetails() +
							" at " + ev.location +
							(ev.signupMax != 0 ? " [" + ev.signupCurrent + "/" + ev.signupMax + "]" : "") + ": " +
							nameList(ev.signupNames, mes) +
							"."
						);
					return;
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

		if (events.isEmpty())
		{
			irc.sendContextReply(mes, "There are no events! :'(");
			return;
		}

		StringBuilder rep = new StringBuilder();
		for (EventItem ev : events)
		{
			rep.append(ev.boldName())
				.append(" at " + ev.location + " (" + ev.id + ")")
				.append(ev.inprogress() ? " (started " + mods.date.timeMicroStamp((new Date()).getTime() - ev.start.getTime()) + " ago)" : "")
				.append(ev.signupMax != 0 ? " [" + ev.signupCurrent + "/" + ev.signupMax + "]" : "")
				.append(--c != 0 ? ", " : ".");
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

	/** Prettyprint a date */
	private final static String absoluteDateFormat(Date da)
	{
		// Some definitions.
			final SimpleDateFormat formatter = new SimpleDateFormat("EEEE d MMM h:mma");
			final SimpleDateFormat dayNameFormatter = new SimpleDateFormat("EEEE");
			final Calendar cda = new GregorianCalendar();
				cda.setTime(da);

			final Calendar cnow = new GregorianCalendar();
			final Date now = cnow.getTime();
			final Date midnight = new GregorianCalendar(cnow.get(Calendar.YEAR), cnow.get(Calendar.MONTH), cnow.get(Calendar.DAY_OF_MONTH), 24, 0, 0).getTime();
			final Date midnightTommorow = new GregorianCalendar(cnow.get(Calendar.YEAR), cnow.get(Calendar.MONTH), cnow.get(Calendar.DAY_OF_MONTH), 48, 0, 0).getTime();
			final Date endOfThisWeek = new GregorianCalendar(cnow.get(Calendar.YEAR), cnow.get(Calendar.MONTH), cnow.get(Calendar.DAY_OF_MONTH) + 7, 0, 0, 0).getTime();
		// </definitions>

		if (da.compareTo(now) > 0) // It's in the future, we can cope with it.
		{
			if (da.compareTo(midnight) < 0) // It's before midnight tonight.
				return shortTime(cda) + " " +            // 9pm
					(cda.get(Calendar.HOUR_OF_DAY) < 18 ? "today" : "tonight");

			if (da.compareTo(midnightTommorow) < 0) // It's before midnight tommorow and not before midnight today, it's tommorow.
				return shortTime(cda) +                  // 9pm
					" tommorow " +                       // tommorow
					futurePeriodOfDayString(cda);        // evening

			if (da.compareTo(endOfThisWeek) < 0) // It's not tommrow, but it is some time when the week-day names alone mean something.
				return shortTime(cda) + " " +            // 9pm
					dayNameFormatter.format(da) + " " +  // Monday
					futurePeriodOfDayString(cda);        // evening

		}

		return formatter.format(da);
	}

	/** Convert a Calendar to "8pm", "7am", "7:30am" etc. */
	private final static String shortTime(Calendar cda)
	{
		final SimpleDateFormat nomins = new SimpleDateFormat("ha");
		final SimpleDateFormat wimins = new SimpleDateFormat("h:mma");

		// Don't show the minutes if they're 0.
		if (cda.get(Calendar.MINUTE) != 0)
			return wimins.format(cda.getTime()).toLowerCase();

		return nomins.format(cda.getTime()).toLowerCase();
	}

	/** Work out if a calendar is in the morning, afternoon or evening. */
	private final static String futurePeriodOfDayString(Calendar cda)
	{
		final int hour = cda.get(Calendar.HOUR_OF_DAY);

		if (hour < 12)
			return "morning";

		if (hour < 18)
			return "afternoon";

		return "evening";
	}

	private static int parseId(String s)
	{
		try
		{
			return Integer.parseInt(s);
		}
		catch (NumberFormatException e) {}

		return 0;
	}
}
