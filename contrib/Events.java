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


	private static final String announceChannel="#bots";
	private static final long checkInterval=60000; // The crond job is run every minute.

	private Modules mods;
	private IRCInterface irc;

	private final URL eventsurl;

	private ArrayList<String[]> current;

	private final static int ID=1;
	private final static int NAME=2;
	private final static int SIGNUPNAMES=8;
	private final static int SIGNUPCURRENT=7;
	private final static int SIGNUPMAX=6;

	public String[] info()
	{
		return new String[] {
			"CompSoc events watcher/info plugin.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			mods.util.getVersion()
		};
	}

	public Events(Modules mods, IRCInterface irc) throws ChoobException
	{
		this.mods = mods;
		this.irc = irc;
		try
		{
			//eventsurl = new URL("http://localhost/events.data");
			eventsurl = new URL("http://faux.uwcs.co.uk/events.data");
		}
		catch (MalformedURLException e)
		{
			throw new ChoobException("Error in constant data.");
		}
		mods.interval.callBack(null, checkInterval);
	}


	public void interval(Object param) throws ChoobException
	{
		ArrayList<String[]> ne=readEventsData();
		if (current!=null && !current.equals(ne))
		{

			ListIterator<String[]> ci=current.listIterator();
			ListIterator<String[]> ni=ne.listIterator();

			HashMap<Integer, String[]>curr=new HashMap<Integer, String[]>();
			while (ci.hasNext())
			{
				String c[]=ci.next();
				curr.put(Integer.parseInt(c[ID]), new String[] {"","",c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9], c[10] }); // Strashmaps ftw.
			}

			while (ni.hasNext())
			{
				String[] n=ni.next();
				String[] c=curr.get(Integer.parseInt(n[ID]));
				if (c==null)
					irc.sendMessage(announceChannel, "New event! " + n[NAME] + " at " + n[10] + " in " + stupidStamp((new Date(Long.parseLong(n[3])*(long)1000)).getTime() - (new Date()).getTime()) + ".");
				else
				{
					if (!c[SIGNUPCURRENT].equals(n[SIGNUPCURRENT]))
					{
						// OH MY GOD WHATTF HAXBQ!

						String[] cn=c[SIGNUPNAMES].split(",");
						String[] nn=n[SIGNUPNAMES].split(",");

						HashSet <String> q=new HashSet<String>();
						HashSet <String> r=new HashSet<String>();

						for (int i=0; i<cn.length; i++)
							q.add(cn[i].trim());

						for (int i=0; i<nn.length; i++)
							r.add(nn[i].trim());

						Iterator <String>it=q.iterator();

						while (it.hasNext())
						{
							Object o=it.next();
							if (r.remove(o))
								it.remove();
						}

						it=r.iterator();

						while (it.hasNext())
						{
							Object o=it.next();
							if (q.remove(o))
								it.remove();
						}


						StringBuilder sig=new StringBuilder();

						it=q.iterator();
						while (it.hasNext())
						{
							String name=it.next();
							if (name.length()>1)
								sig.append("-" + name + ", ");
						}

						it=r.iterator();
						while (it.hasNext())
						{
							String name=it.next();
							if (name.length()>1)
								sig.append("+" + name + ", ");
						}
						String sigts=sig.toString();
						if (sigts.length()>2) sigts=sigts.substring(0, sigts.length()-2);
						irc.sendMessage(announceChannel, "Signups for " + Colors.BOLD + n[NAME] + Colors.NORMAL + " (" + n[ID] + ") [" + stupidStamp((new Date(Long.parseLong(n[3])*(long)1000)).getTime() - (new Date()).getTime()) + "] now " + n[SIGNUPCURRENT] + "/" + n[SIGNUPMAX] + " (" + sigts + ").");
					}
				}
			}
		}
		current=ne;
		mods.interval.callBack(null, checkInterval);
	}

	private String stupidStamp(long i)
	{
		System.out.println(i);

		long w= (i / (7*24*60*60*1000)); i -= w*(7*24*60*60*1000);
		long d= (i / (24*60*60*1000)); i -= d*(24*60*60*1000);
		long h= (i / (60*60*1000)); i -= h*(60*60*1000);
		long m= (i / (60*1000)); i -= m*(60*1000);
		long s= (i / (1000)); i -= s*1000;
		long ms=(i); i -=ms;
		long st[]={w,d,h,m,s,ms};
		String pr[]={"w","d","h","m","s","ms"};
		String t="";
		int c=2;
		while (0!=c--)
			for (int j=0; j<st.length; j++)
				if (st[j]!=0)
				{
					t+=st[j] + pr[j];
					st[j]=0;
					break;
				}

		return t;
	}

	private ArrayList<String[]> readEventsData() throws ChoobException
	{
		ArrayList<String[]> events=new ArrayList();
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
		{
			String [] nine=ma.group(9).split("\\|");
			events.add(0, new String[] {"", ma.group(1), ma.group(2), ma.group(3), ma.group(4), ma.group(5), ma.group(6), ma.group(7), ma.group(8), nine[0], ma.group(10), nine[1]});
		}
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

		ArrayList<String[]> events=readEventsData();
		int c=events.size();
		String rep="";
		while (c--!=0)
		{
			String[] ev= events.get(c);
			Date da=new Date(Long.parseLong(ev[3])*(long)1000);
			Date dat=new Date(Long.parseLong(ev[4])*(long)1000);
			boolean finished=(new Date()).compareTo(dat)>0;
			int eid=0;
			try
			{
				eid=Integer.parseInt(comp);
			}
			catch (NumberFormatException e) {}
			if (!finished)
				if (ev[2].toLowerCase().indexOf(comp)!=-1 || Integer.parseInt(ev[1])==eid)
				{
					irc.sendContextReply(mes, Colors.BOLD + ev[2] + Colors.NORMAL + " at " + ev[10] + " (" + ev[9] + ") (" + ev[1] + ") from " + (new SimpleDateFormat("EEEE d MMM H:mma").format(da)) + " to " + (new SimpleDateFormat("EEEE d MMM H:mma").format(dat)) + "." );
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

		ArrayList<String[]> events=readEventsData();
		int c=events.size();
		String rep="";
		System.out.println(comp);
		while (c--!=0)
		{
			String[] ev= events.get(c);
			Date da=new Date(Long.parseLong(ev[3])*(long)1000);
			Date dat=new Date(Long.parseLong(ev[4])*(long)1000);
			boolean finished=(new Date()).compareTo(dat)>0;

			int eid=0;
			try
			{
				eid=Integer.parseInt(comp);
			}
			catch (NumberFormatException e) { }
			if (!finished)
				if (ev[2].toLowerCase().indexOf(comp)!=-1 || Integer.parseInt(ev[1])==eid)
				{
					if (!ev[5].equals("X") && !ev[5].equals("-"))
						irc.sendContextReply(mes, "Please use http://www.warwickcompsoc.co.uk/events/details/options?id=" + ev[1] + "&action=signup to sign-up for " + Colors.BOLD + ev[2] + Colors.NORMAL + (!finished ? " [" + stupidStamp(da.getTime() - (new Date()).getTime()) + "]" : "") + ".");
					else
					{
						rep+="Event " + ev[1] + " matched, but does not accept sign-ups... ";
						continue;
					}
					return;
				}
		}
		irc.sendContextReply(mes, rep + "Event not found.");
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

		ArrayList<String[]> events=readEventsData();
		int c=events.size();
		String rep="";
		System.out.println(comp);
		int eid=0;
		try
		{
			eid=Integer.parseInt(comp);
		}
		catch (NumberFormatException e) { }

		while (c--!=0)
		{
			String[] ev= events.get(c);
			Date da=new Date(Long.parseLong(ev[3])*(long)1000);
			Date dat=new Date(Long.parseLong(ev[4])*(long)1000);
			boolean finished=(new Date()).compareTo(dat)>0;
			if (!finished)
				if (ev[2].toLowerCase().indexOf(comp)!=-1 || Integer.parseInt(ev[1])==eid)
				{
					irc.sendContextReply(mes, "http://www.warwickcompsoc.co.uk/events/details/?id=" + ev[1] + ".");
					return;
				}
		}
		irc.sendContextReply(mes, rep + "Event not found.");
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

		ArrayList<String[]> events=readEventsData();
		int c=events.size();
		String rep="";
		System.out.println(comp);
		int eid=0;
		try
		{
			eid=Integer.parseInt(comp);
		}
		catch (NumberFormatException e) { }
		while (c--!=0)
		{
			String[] ev= events.get(c);
			Date da=new Date(Long.parseLong(ev[3])*(long)1000);
			Date dat=new Date(Long.parseLong(ev[4])*(long)1000);
			boolean finished=(new Date()).compareTo(dat)>0;
			if (!finished)
				if (ev[2].toLowerCase().indexOf(comp)!=-1 || Integer.parseInt(ev[1])==eid)
				{
					irc.sendContextReply(mes, "Signups for " + Colors.BOLD + ev[2] + Colors.NORMAL  + (finished ? " (finished)" : "") + " at " + ev[10] + " (" + ev[1] + ")" + (!finished ? " [" + stupidStamp(da.getTime() - (new Date()).getTime()) + "]" : "") + (!ev[6].equals("0") ? " [" + ev[7] + "/" + ev[6] + "]" : "") + ": " + ev[8].replaceAll("([a-zA-Z])([^, ]+)","$1'$2") + ".");
					return;
				}
		}
		irc.sendContextReply(mes, rep + "Event not found.");
	}

	public String[] helpCommandList = {
		"Get a list of events.",
	};
	public void commandList(Message mes) throws ChoobException
	{
		ArrayList<String[]> events=readEventsData();
		int c=events.size();

		if (c==0)
		{
			irc.sendContextReply(mes, "There are no events! :'(");
			return;
		}
		String rep="";
		while (c-->0)
		{
			String[] ev= events.get(c);
			Date da=new Date(Long.parseLong(ev[3])*(long)1000);
			Date dat=new Date(Long.parseLong(ev[4])*(long)1000);
			boolean finished=(new Date()).compareTo(dat)>0;
			rep+=Colors.BOLD + ev[2] + Colors.NORMAL + (finished ? " (finished)" : "") + " at " + ev[10] + " (" + ev[1] + ")" + (!finished ? " [" + stupidStamp(da.getTime() - (new Date()).getTime()) + "]" : "") + (!ev[6].equals("0") ? " [" + ev[7] + "/" + ev[6] + "]" : "") + (c!=0 ? ", " : ".");
		}
		irc.sendContextReply(mes, "Events: " + rep);
	}
}
