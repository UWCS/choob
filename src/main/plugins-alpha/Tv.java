/** @author Faux */

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

public class Tv
{
	private final Modules mods;
	private final IRCInterface irc;

	final static String[] cts = { "bbc1", "bbc2", "itv1", "ch4", "five", "sky_one", "sky_cinema1", "sky_cinema2", "sky_movies1", "sky_movies2", "sky_movies3", "sky_movies4", "sky_movies5", "sky_movies6", "sky_movies7", "sky_movies8", "sky_movies9" };

	ChannelInfo[] chans=new ChannelInfo[cts.length];

	final static String propend = "app=Choob&email=chrisrwest@gmail.com";

	public Tv(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
		mods.interval.reset();

		for (int i=0; i<chans.length; i++)
			chans[i]=new ChannelInfo();


		mods.interval.callBack(null, 0); // Don't block the constructor.
	}

	public void interval (final Object o)
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
		catch (final Throwable t)
		{
			t.printStackTrace();
		}

		mods.interval.callBack(null, (int)((new Random().nextFloat()*3.0f+1.0f)*60.0f*60.0f*1000)); // 1->4h in ms.
	}

	public ChannelInfo getChannelInfo(final String parm)
	{
		final ChannelInfo c=new ChannelInfo();
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
		catch (final Throwable t)
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


	public void commandSearch (final Message mes)
	{
		final List <String>parms=mods.util.getParams(mes, 1);
		if (parms.size()<2)
		{
			irc.sendContextReply(mes, "Not enough params, expected 'searchstring'.");
			return;
		}

		final List<String> reps=new ArrayList<String>();

		for (final ChannelInfo c : chans)
		{

			final Iterator<Programme> i=c.programmes.iterator();

			try
			{
				while (i.hasNext())
				{
					final Programme p=i.next();
					if (p.title.toLowerCase().indexOf(parms.get(1).toLowerCase())!=-1)
						reps.add(c.key + ", " + new Date(p.start).toString() + " -> " + new Date(p.end).toString() + ": " + p.title + ": " + p.desc);
				}
			}
			catch (final Throwable t)
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
			final Iterator <String>p=reps.iterator();
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


	public synchronized void commandInfo (final Message mes)
	{
		final List <String>parms=mods.util.getParams(mes, 2);
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

		final ChannelInfo c=getChannelInfo(parms.get(1));
		final Iterator<Programme> i=c.programmes.iterator();
		final List<String> reps=new ArrayList<String>();

		try
		{
			while (i.hasNext())
			{
				final Programme p=i.next();
				if (p.title.toLowerCase().indexOf(parms.get(2).toLowerCase())!=-1)
					//irc.sendContextReply(mes, parms.get(1) + ", " + (new Date(p.start)).toString() + " -> " + (new Date(p.end)).toString() + ": " + p.title + ": " + p.desc);
					reps.add(parms.get(1) + ", " + new Date(p.start).toString() + " -> " + new Date(p.end).toString() + ": " + p.title + ": " + p.desc);
			}
		}
		catch (final Throwable t)
		{
			t.printStackTrace();
		}

		if (reps.size()==0)
			irc.sendContextReply(mes, "Dunno.");
		else if (reps.size()==1)
			irc.sendContextReply(mes, reps.get(0));
		else
		{
			final Iterator <String>p=reps.iterator();
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

	@Override
	public int compareTo(final Programme other) throws ClassCastException
	{
		return new Date(this.end).compareTo(new Date(other.end));
	}

}

class ChannelInfo
{
	String date, key, source;
	ArrayList<Programme> programmes;

	public ChannelInfo(final String date, final String key, final String source)
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
			final Calendar t=new GregorianCalendar();

			while (i.hasNext())
			{
				final Programme p=i.next();
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
		catch (final ParseException e)
		{
			// Nothing we can do about any error.
		}
	}
}

class ParseHandler extends DefaultHandler
{
	ChannelInfo c;

	Programme cp=null;
	int op;

	public ParseHandler(final ChannelInfo chan)
	{
		c=chan;
	}

	@Override
	public void startElement(final String namespaceURI, final String lName, final String qName, final Attributes attrs) throws SAXException
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
	@Override
	public void startDocument () throws SAXException
	{
		// Ignore
	}
	@Override
	public void endDocument () throws SAXException
	{
		// Ignore
	}
	@Override
	public void endElement (final String namespaceURI, final String sName, final String qName) throws SAXException
	{
		op=0;
	}

	@Override
	public void characters(final char buf[], final int offset, final int len) throws SAXException
	{
		final String s = new String(buf, offset, len);
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

