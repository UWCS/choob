import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.ChannelJoin;
import uk.co.uwcs.choob.support.events.Message;

/**
 * Take advantage of Quote's API for theming greetings.
 *
 * @author Faux
 *
 */

// The XML parser callback.
class GreetingsParserHandler extends DefaultHandler
{

	// Enum for the subelements of <event>
	public static enum ElementName
	{
		GREETING,
		USER,
		DATE;

		/** @returns null on failure. <-- warning. */
		static ElementName fromString(final String one)
		{
			if (one.equalsIgnoreCase("greeting"))
				return GREETING;
			if (one.equalsIgnoreCase("user"))
				return USER;
			if (one.equalsIgnoreCase("date"))
				return DATE;

			return null;
		}

		// Gets the attribute name for a given ElementName.
		static String attrName(final ElementName t)
		{
			switch (t)
			{
				case GREETING:
					return "text";
				case USER:
					return "nick";
				case DATE:
					return "day";
				default:
					assert false;
			}

			assert false; throw new IllegalArgumentException("Gotcha in some unreachable code, y'bastard.");
		}
	}

	// Data structure of doom.
	public Map<String, Map<ElementName, List<String>>> data = new HashMap<String, Map<ElementName, List<String>>>();

	// Pointer to the contents of the current <event> element we're inside.
	Map<ElementName, List<String>> currentEvent;

	// Ignore these. Boooooooooring.
	@Override
	public void setDocumentLocator(final Locator l) { }
	@Override
	public void startDocument() throws SAXException { }
	@Override
	public void endDocument() throws SAXException { }
	@Override
	public void endElement(final String namespaceURI, final String sName, final String qName)	throws SAXException { }
	@Override
	public void characters(final char buf[], final int offset, final int len) throws SAXException { }
	@Override
	public void ignorableWhitespace(final char buf[], final int offset, final int len) throws SAXException { }
	@Override
	public void processingInstruction(final String target, final String dat) throws SAXException {	}

	// These aren't our problem either. Booooooooooooooooooooooooooring.
	@Override
	public void error(final SAXParseException e) throws SAXParseException { throw e; }
	@Override
	public void warning(final SAXParseException e) throws SAXParseException { throw e; }

	// Yaaay, do something.
	@Override
	public void startElement(final String namespaceURI, final String lName, final String qName, final Attributes attrs) throws SAXException
	{
		String eName = lName;
		if ("".equals(eName)) eName = qName;

		// If we've just come across an <event> tag..
		if (eName.equalsIgnoreCase("event"))
		{
			// Put it in our map.
			currentEvent = new HashMap<ElementName, List<String>>();
			data.put(getAttr(attrs, "title"), currentEvent);
		}
		else
		{
			// It's not an event tag, see if it's a valid internal?
			final ElementName c = ElementName.fromString(eName);
			if (c == null)
				// Nope.
				return;

			// Try and get the internal interal's list:
			List<String> list = currentEvent.get(c);
			if (list == null)
			{
				// Ensure there is one.
				list = new ArrayList<String>();
				currentEvent.put(c, list);
			}

			// Add our data to it.
			list.add(getAttr(attrs, ElementName.attrName(c)));
		}

	}

	// Get an attribute of the given name from an attrs.
	static String getAttr(final Attributes attrs, final String name)
	{
		for (int i = 0; i < attrs.getLength(); i++) // attrs.getLength() will 'always' be 1.
		{
			String aName = attrs.getLocalName(i); // Attr name
			if ("".equalsIgnoreCase(aName))
				aName = attrs.getQName(i);
			if (aName.equalsIgnoreCase(name))
				return attrs.getValue(i);
		}

		return "";

	}
}


public class Greetings
{

	URL dataURL;

	public String[] info()
	{
		return new String[] {
			"Plugin to theme Quote",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	// Normal premable.
	private final Modules mods;
	private final IRCInterface irc;
	public Greetings(final Modules mods, final IRCInterface irc) throws MalformedURLException
	{
		this.mods = mods;
		this.irc = irc;
		dataURL = new URL("http://faux.uwcs.co.uk/greetingevents.xml");
	}

	// This is the bit Quote calls.
	public String apiGreetingFor(final ChannelJoin ev)
	{
		try
		{
			// td is the date in the format it'll appear in the XML file.
			final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd");
			final String td = sdf.format(today());

			// Grab the data file.
			final Map<String, Map<GreetingsParserHandler.ElementName, List<String>>> data = readXMLStuff(dataURL);

			// Go through it.
			for (final Map.Entry<String, Map<GreetingsParserHandler.ElementName, List<String>>> es : data.entrySet())
			{
				// Im is the data from this <event>.
				final Map<GreetingsParserHandler.ElementName, List<String>> im = es.getValue();
				final List<String> dates = im.get(GreetingsParserHandler.ElementName.DATE);

				// See if the date matches.
				if (dates.contains("*") || dates.contains(td))
				{
					final List<String> nicks = im.get(GreetingsParserHandler.ElementName.USER);

					// See if the nick matches.
					if (nicks.contains("*") || nicks.contains(mods.nick.getBestPrimaryNick(ev.getNick())))
					{

						// Return a random greeting.
						final List<String> greetings = im.get(GreetingsParserHandler.ElementName.GREETING);
						assert greetings.size() > 0;
						final String [] greets = greetings.toArray(new String[] {} );

						return greets[new Random().nextInt(greets.length)];
					}

				}
			}

		}
		// Not much we can do with these.
		catch (final ChoobException ce)
		{
			ce.printStackTrace();
		}

		return "Hello, ";
	}

	Date today()
	{
		return new Date();
	}


	public void commandTest(final Message mes) throws ChoobException
	{
		final Map<String, Map<GreetingsParserHandler.ElementName, List<String>>> data = readXMLStuff(dataURL);
		for (final Map.Entry<String, Map<GreetingsParserHandler.ElementName, List<String>>> es : data.entrySet())
			irc.sendContextReply(mes, es.getKey() + ": " + es.getValue().get(GreetingsParserHandler.ElementName.DATE).get(0));

	}

	// Grab the file into the datatype of d00m.
	synchronized Map<String, Map<GreetingsParserHandler.ElementName, List<String>>> readXMLStuff(final URL path) throws ChoobException
	{
		final GreetingsParserHandler handler = new GreetingsParserHandler();
		// Use the validating parser
		final SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setValidating(true);

		try
		{
			// Parse the input
			final SAXParser saxParser = factory.newSAXParser();
			saxParser.parse( new ByteArrayInputStream(mods.scrape.getContentsCached(path).getBytes()) , handler);

			return handler.data;
		}
		catch (final SAXParseException spe)
		{
			throw new ChoobException("Parse exception", spe);
		}
		catch (final SAXException sxe)
		{
			throw new ChoobException("Parser exception", sxe);
		}
		catch (final ParserConfigurationException pce)
		{
			throw new ChoobException("Parser configuration exception", pce);
		}
		catch (final IOException ioe)
		{
			throw new ChoobException("IO exception", ioe);
		}
	}

}
