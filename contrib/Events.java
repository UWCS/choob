import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;

//irc.sendContextReply(mes, "On " + new SimpleDateFormat("EEEE d MMM H:mma").format(new Date(Long.parseLong(ma.group(3))*(long)1000)));

public class Events
{
	private GetContentsCached eventsdata;
	private Modules mods;
	private IRCInterface irc;

	public Events(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
		try {
			eventsdata = new GetContentsCached(new URL("http://faux.uwcs.co.uk/events.data")); /* <-- default timeout is okay. */
		}
		// XXX
		catch (Exception e) {} // Never going to be thrown.
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

	private ArrayList<String[]> readEventsData()
	{
		ArrayList<String[]> events=new ArrayList();
		String s=null;
		try { s=eventsdata.getContents(); } catch (IOException e) 
		{
			return null; // H4X!
		}

		//System.out.println("BAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADGERS" + s);
		//int c=ids.replaceAll("[^ ]*","").length()+1;
		//int d=0;
		Pattern pa=Pattern.compile("([0-9]+) \\\"([a-zA-Z0-9 _-]+)\\\" ([0-9]+) ([0-9]+) ([A-Za-z0-9-]+) ([0-9]+) ([0-9]+) \\\"(.*?)\\\" \\\"(.+?)\\\" \\\"(.*?)\\\"");
		//                             1                 2                  3       4            5           6         7           8             9             10
		//                             id               name               date     date   <signup> code      max      cur    names</signup>   desc.       location
		String rep="";
		Matcher ma=pa.matcher(s);
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
	public void commandInfo(Message mes)
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
			boolean finished=(new Date()).compareTo(da)>0;
			if (!finished)
				if (ev[2].toLowerCase().indexOf(comp)!=-1 || Integer.parseInt(ev[1])==Integer.parseInt(comp))
				{
					irc.sendContextReply(mes, ev[2] + " at " + ev[10] + " (" + ev[9] + ") (" + ev[1] + ") from " + (new SimpleDateFormat("EEEE d MMM H:mma").format(da)) + " to " + (new SimpleDateFormat("EEEE d MMM H:mma").format(dat)) + "." );
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
	public void commandSignup(Message mes)
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
			boolean finished=(new Date()).compareTo(da)>0;
			int javaislamebecauseitmakesmewritelinesofunnecessarycode=0;
			try
			{
				javaislamebecauseitmakesmewritelinesofunnecessarycode=Integer.parseInt(comp);
			}
			catch (Exception e) { }
			if (!finished)
				if (ev[2].toLowerCase().indexOf(comp)!=-1 || Integer.parseInt(ev[1])==javaislamebecauseitmakesmewritelinesofunnecessarycode)
				{
					if (!ev[5].equals("X") && !ev[5].equals("-"))
						irc.sendContextReply(mes, "Please use http://www.warwickcompsoc.co.uk/events/details/options?id=" + ev[1] + "&action=signup to sign-up for " + ev[2] + (!finished ? " [" + stupidStamp(da.getTime() - (new Date()).getTime()) + "]" : "") + ".");
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
	public void commandLink(Message mes)
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
		int javaislamebecauseitmakesmewritelinesofunnecessarycode=0;
		try
		{
			javaislamebecauseitmakesmewritelinesofunnecessarycode=Integer.parseInt(comp);
		}
		catch (Exception e) { }

		while (c--!=0)
		{
			String[] ev= events.get(c);
			Date da=new Date(Long.parseLong(ev[3])*(long)1000);
			boolean finished=(new Date()).compareTo(da)>0;
			if (!finished)
				if (ev[2].toLowerCase().indexOf(comp)!=-1 || Integer.parseInt(ev[1])==javaislamebecauseitmakesmewritelinesofunnecessarycode)
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
	public void commandSignups(Message mes)
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
		int javaislamebecauseitmakesmewritelinesofunnecessarycode=0;
		try
		{
			javaislamebecauseitmakesmewritelinesofunnecessarycode=Integer.parseInt(comp);
		}
		catch (Exception e) { }
		while (c--!=0)
		{
			String[] ev= events.get(c);
			Date da=new Date(Long.parseLong(ev[3])*(long)1000);
			boolean finished=(new Date()).compareTo(da)>0;
			if (!finished)
				if (ev[2].toLowerCase().indexOf(comp)!=-1 || Integer.parseInt(ev[1])==javaislamebecauseitmakesmewritelinesofunnecessarycode)
				{
					irc.sendContextReply(mes, "Signups for " + ev[2] + (finished ? " (finished)" : "") + " at " + ev[10] + " (" + ev[1] + ")" + (!finished ? " [" + stupidStamp(da.getTime() - (new Date()).getTime()) + "]" : "") + (!ev[6].equals("0") ? " [" + ev[7] + "/" + ev[6] + "]" : "") + ": " + ev[8].replaceAll("([a-zA-Z])([^, ]+)","$1'$2") + ".");
					return;
				}
		}
		irc.sendContextReply(mes, rep + "Event not found.");
	}

	public String[] helpCommandList = {
		"Get a list of events.",
	};
	public void commandList(Message mes)
	{
		ArrayList<String[]> events=readEventsData();
		int c=events.size();
		String rep="";
		while (c--!=0)
		{
			String[] ev= events.get(c);
			Date da=new Date(Long.parseLong(ev[3])*(long)1000);
			boolean finished=(new Date()).compareTo(da)>0;
			rep+=ev[2] + (finished ? " (finished)" : "") + " at " + ev[10] + " (" + ev[1] + ")" + (!finished ? " [" + stupidStamp(da.getTime() - (new Date()).getTime()) + "]" : "") + (!ev[6].equals("0") ? " [" + ev[7] + "/" + ev[6] + "]" : "") + (c!=0 ? ", " : ".");
		}
		irc.sendContextReply(mes, "Events: " + rep);	
	}
}
