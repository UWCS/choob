import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.xml.parsers.*;

import org.xml.sax.*;
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
		static ElementName fromString(String one)
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
		static String attrName(ElementName t)
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
	public void setDocumentLocator(Locator l) { }
	@Override
	public void startDocument() throws SAXException { }
	@Override
	public void endDocument() throws SAXException { }
	@Override
	public void endElement(String namespaceURI, String sName, String qName)	throws SAXException { }
	@Override
	public void characters(char buf[], int offset, int len) throws SAXException { }
	@Override
	public void ignorableWhitespace(char buf[], int offset, int len) throws SAXException { }
	@Override
	public void processingInstruction(String target, String data) throws SAXException {	}

	// These aren't our problem either. Booooooooooooooooooooooooooring.
	@Override
	public void error(SAXParseException e) throws SAXParseException { throw e; }
	@Override
	public void warning(SAXParseException e) throws SAXParseException { throw e; }

	// Yaaay, do something.
	@Override
	public void startElement(String namespaceURI, String lName, String qName, Attributes attrs) throws SAXException
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
			ElementName c = ElementName.fromString(eName);
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
	static String getAttr(Attributes attrs, String name)
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
	private Modules mods;
	private IRCInterface irc;
	public Greetings(Modules mods, IRCInterface irc) throws MalformedURLException
	{
		this.mods = mods;
		this.irc = irc;
		dataURL = new URL("http://faux.uwcs.co.uk/greetingevents.xml");
	}

	// This is the bit Quote calls.
	public String apiGreetingFor(ChannelJoin ev)
	{
		try
		{
			// td is the date in the format it'll appear in the XML file.
			final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd");
			final String td = sdf.format(today());

			// Grab the data file.
			Map<String, Map<GreetingsParserHandler.ElementName, List<String>>> data = readXMLStuff(dataURL);

			// Go through it.
			for (Map.Entry<String, Map<GreetingsParserHandler.ElementName, List<String>>> es : data.entrySet())
			{
				// Im is the data from this <event>.
				Map<GreetingsParserHandler.ElementName, List<String>> im = es.getValue();
				List<String> dates = im.get(GreetingsParserHandler.ElementName.DATE);

				// See if the date matches.
				if (dates.contains("*") || dates.contains(td))
				{
					List<String> nicks = im.get(GreetingsParserHandler.ElementName.USER);

					// See if the nick matches.
					if (nicks.contains("*") || nicks.contains(mods.nick.getBestPrimaryNick(ev.getNick())))
					{

						// Return a random greeting.
						List<String> greetings = im.get(GreetingsParserHandler.ElementName.GREETING);
						assert greetings.size() > 0;
						String [] greets = greetings.toArray(new String[] {} );

						return greets[new Random().nextInt(greets.length)];
					}

				}
			}

		}
		// Not much we can do with these.
		catch (ChoobException ce)
		{
			ce.printStackTrace();
		}

		return "Hello, ";
	}

	Date today()
	{
		return new Date();
	}


	public void commandTest(Message mes) throws ChoobException
	{
		Map<String, Map<GreetingsParserHandler.ElementName, List<String>>> data = readXMLStuff(dataURL);
		for (Map.Entry<String, Map<GreetingsParserHandler.ElementName, List<String>>> es : data.entrySet())
			irc.sendContextReply(mes, es.getKey() + ": " + es.getValue().get(GreetingsParserHandler.ElementName.DATE).get(0));

	}

	// Grab the file into the datatype of d00m.
	synchronized Map<String, Map<GreetingsParserHandler.ElementName, List<String>>> readXMLStuff(URL path) throws ChoobException
	{
		GreetingsParserHandler handler = new GreetingsParserHandler();
		// Use the validating parser
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setValidating(true);

		try
		{
			// Parse the input
			SAXParser saxParser = factory.newSAXParser();
			saxParser.parse( new ByteArrayInputStream(mods.scrape.getContentsCached(path).getBytes()) , handler);

			return handler.data;
		}
		catch (SAXParseException spe)
		{
			throw new ChoobException("Parse exception", spe);
		}
		catch (SAXException sxe)
		{
			throw new ChoobException("Parser exception", sxe);
		}
		catch (ParserConfigurationException pce)
		{
			throw new ChoobException("Parser configuration exception", pce);
		}
		catch (IOException ioe)
		{
			throw new ChoobException("IO exception", ioe);
		}
	}

}
