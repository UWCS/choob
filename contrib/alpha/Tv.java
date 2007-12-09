/** @author Faux */

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.text.DateFormat;
import java.util.*;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

public class Tv
{
	private Modules mods;
	private IRCInterface irc;

	final static String[] cts = { "bbc1", "bbc2", "itv1", "ch4", "five", "sky_one", "sky_cinema1", "sky_cinema2", "sky_movies1", "sky_movies2", "sky_movies3", "sky_movies4", "sky_movies5", "sky_movies6", "sky_movies7", "sky_movies8", "sky_movies9" };

	ChannelInfo[] chans=new ChannelInfo[cts.length];

	final static String propend = "app=Choob&email=chrisrwest@gmail.com";

	public Tv(Modules mods, IRCInterface irc) throws ChoobException
	{
		this.mods = mods;
		this.irc = irc;
		mods.interval.reset();

		for (int i=0; i<chans.length; i++)
			chans[i]=new ChannelInfo();


		mods.interval.callBack(null, 0); // Don't block the constructor.
	}

	public void interval (Object o)
	{
		try
		{
			for (int i=0; i<cts.length; i++)
			{
				synchronized (chans[i])
				{
					chans[i]=getChannelInfo(cts[i]);
				}
				Thread.sleep(4000);
			}
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}

		mods.interval.callBack(null, (int)(((new Random()).nextFloat()*3.0f+1.0f)*60.0f*60.0f*1000)); // 1->4h in ms.
	}

	public ChannelInfo getChannelInfo(String parm)
	{
		ChannelInfo c=new ChannelInfo();
		try
		{
			final DefaultHandler handler = new ParseHandler(c);
			final SAXParserFactory factory = SAXParserFactory.newInstance();
			final SAXParser saxParser = factory.newSAXParser();
			final URL url=new URL("http://bleb.org/tv/data/listings/0/" + parm + ".xml?" + propend);
			synchronized (c)
			{
				synchronized (handler)
				{
					saxParser.parse( new ByteArrayInputStream(mods.scrape.getContentsCached(url, 4*60*60*1000).getBytes()), handler);
				}
			}
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
		c.cleanProgrammes();
		return c;
	}

	public String[] helpCommandSearch = {
		"Find a programme.",
		"<SearchTerms>",
		"<SearchTerms> is part of the title of the program you wish to search for."
	};


	public void commandSearch (Message mes)
	{
		List <String>parms=mods.util.getParams(mes, 1);
		if (parms.size()<2)
		{
			irc.sendContextReply(mes, "Not enough params, expected 'searchstring'.");
			return;
		}

		List<String> reps=new ArrayList<String>();

		for (int n=0; n<chans.length; n++)
		{

			ChannelInfo c=chans[n];
			Iterator<Programme> i=c.programmes.iterator();

			try
			{
				while (i.hasNext())
				{
					Programme p=i.next();
					if (p.title.toLowerCase().indexOf(parms.get(1).toLowerCase())!=-1)
						reps.add(c.key + ", " + (new Date(p.start)).toString() + " -> " + (new Date(p.end)).toString() + ": " + p.title + ": " + p.desc);
				}
			}
			catch (Throwable t)
			{
				t.printStackTrace();
			}

		}

		if (reps.size()==0)
			irc.sendContextReply(mes, "No hits.");
		else if (reps.size()==1)
			irc.sendContextReply(mes, reps.get(0));
		else
		{
			Iterator <String>p=reps.iterator();
			irc.sendContextReply(mes, "See pm.");

			while (p.hasNext())
				irc.sendMessage(mes.getNick(), p.next());
		}

	}



	public String[] helpCommandInfo = {
		"Get info for a programme on a channel called.. ",
		"<Channel> <SearchTerms>",
		"<Channel> is a tag from the list: http://bleb.org/tv/data/listings/0/, without .xml, eg. 'bbc1'",
		"<SearchTerms> is part of the title of the program you wish to search for."
	};


	public synchronized void commandInfo (Message mes)
	{
		List <String>parms=mods.util.getParams(mes, 2);
		if (parms.size()<3)
		{
			irc.sendContextReply(mes, "Not enough params, expected 'tag searchstring'. Tags: http://bleb.org/tv/data/listings/0/ for list. eg. 'bbc1'.");
			return;
		}

		if ("".equals(parms.get(1)) || parms.get(1).length() > 22)
		{
			irc.sendContextReply(mes, "Invalid tag, see help for more information.");
			return;
		}

		ChannelInfo c=getChannelInfo(parms.get(1));
		Iterator<Programme> i=c.programmes.iterator();
		List<String> reps=new ArrayList<String>();

		try
		{
			while (i.hasNext())
			{
				Programme p=i.next();
				if (p.title.toLowerCase().indexOf(parms.get(2).toLowerCase())!=-1)
					//irc.sendContextReply(mes, parms.get(1) + ", " + (new Date(p.start)).toString() + " -> " + (new Date(p.end)).toString() + ": " + p.title + ": " + p.desc);
					reps.add(parms.get(1) + ", " + (new Date(p.start)).toString() + " -> " + (new Date(p.end)).toString() + ": " + p.title + ": " + p.desc);
			}
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}

		if (reps.size()==0)
			irc.sendContextReply(mes, "Dunno.");
		else if (reps.size()==1)
			irc.sendContextReply(mes, reps.get(0));
		else
		{
			Iterator <String>p=reps.iterator();
			irc.sendContextReply(mes, "See pm.");

			while (p.hasNext())
				irc.sendMessage(mes.getNick(), p.next());
		}
	}

}

class Programme implements Comparable<Programme>
{
	public String title="", desc="";

	public long start=0, end=Integer.MAX_VALUE;

	public int compareTo(Programme other) throws ClassCastException
	{
		return (new Date(this.end)).compareTo((new Date(other.end)));
	}

}

class ChannelInfo
{
	String date, key, source;
	ArrayList<Programme> programmes;

	public ChannelInfo(String date, String key, String source)
	{
		this();
		this.date=date;
		this.source=source;
		this.key=key;
	}

	public ChannelInfo()
	{
		programmes=new ArrayList<Programme>();
	}

	public void cleanProgrammes()
	{
		try
		{
			final Date day=DateFormat.getDateInstance(DateFormat.SHORT, Locale.UK).parse(date);
			final Iterator<Programme> i=programmes.iterator();
			Calendar t=new GregorianCalendar();

			while (i.hasNext())
			{
				Programme p=i.next();
				t.setTime(day);
				t.add(Calendar.HOUR_OF_DAY, (int)(p.start/100));
				t.add(Calendar.MINUTE, (int)(p.start%100));
				if ((int)(p.start/100)<5)
					t.add(Calendar.DAY_OF_MONTH, (int)(p.start%100));
				p.start=t.getTimeInMillis();


				t.setTime(day);
				t.add(Calendar.HOUR_OF_DAY, (int)(p.end/100));
				t.add(Calendar.MINUTE, (int)(p.end%100));
				if ((int)(p.end/100)<5)
					t.add(Calendar.DAY_OF_MONTH, (int)(p.end%100));

				p.end=t.getTimeInMillis();
			}
		}
		catch (java.text.ParseException e)
		{

		}
	}
}

class ParseHandler extends DefaultHandler
{
	ChannelInfo c;

	Programme cp=null;
	int op;

	public ParseHandler(ChannelInfo chan)
	{
		c=chan;
	}

	public void startElement(String namespaceURI, String lName, String qName, Attributes attrs) throws SAXException
	{
		String eName = lName; // element name
		if ("".equals(eName))
			eName = qName; // namespaceAware = false

		if (eName.equals("channel"))
		{
			if (attrs != null)
			{
				for (int i = 0; i < attrs.getLength(); i++)
				{
					String aName = attrs.getLocalName(i); // Attr name
					if ("".equals(aName))
						aName = attrs.getQName(i);

					if (aName.equals("id"))
						c.key=attrs.getValue(i);
					else if (aName.equals("date"))
						c.date=attrs.getValue(i);
					else if (aName.equals("source"))
						c.source=attrs.getValue(i);

				}
			}
		}
		else if (eName.equals("programme"))
		{
			cp=new Programme();
			c.programmes.add(cp);
		}
		else if (eName.equals("desc"))
			op=1;
		else if (eName.equals("start"))
			op=2;
		else if (eName.equals("end"))
			op=3;
		else if (eName.equals("title"))
			op=4;
	}
	public void startDocument () throws SAXException {}
	public void endDocument () throws SAXException {}
	public void endElement (String namespaceURI, String sName, String qName) throws SAXException
	{
		op=0;
	}

	public void characters(char buf[], int offset, int len) throws SAXException
	{
		String s = new String(buf, offset, len);
		if (!s.trim().equals(""))
			switch(op)
			{
				case 1: cp.desc+=s; break;
				case 4: cp.title+=s; break;

				case 2: cp.start=Integer.parseInt(s); break;
				case 3: cp.end=Integer.parseInt(s); break;
			}
	}
}

