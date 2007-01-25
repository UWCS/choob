import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.net.*;

import net.zuckerfrei.jcfd.*;
import org.jibble.pircbot.Colors;

public class DictionaryException extends ChoobException
{
	public DictionaryException(String text)
	{
		super(text);
	}
	public DictionaryException(String text, Throwable e)
	{
		super(text, e);
	}
	public String toString()
	{
		return getMessage();
	}

}

public class Dict
{
	public String[] info()
	{
		return new String[] {
			"Dict.org dictionary lookup plugin.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private Modules mods;
	private IRCInterface irc;
	public Dict(Modules mods, IRCInterface irc)
	{
		this.mods=mods;
		this.irc=irc;
	}

	public String[] helpCommandDict = {
		"Look up a word.",
		"[<dictionary>] <word>",
		"<dictionary> is the dictionary to use, valid dictioanries include 'com' (dictionary.com), 'wikipedia', 'urbandict', 'acronym'(finder) and 'dict'(.org)",
		"<word> is the word to look up"
	};

	String spelt(String item)
	{
		try
		{
			final String rep=apiCorrectBy(item);
			if (rep==null)
				return apiSuggestions(item);
			else
				return "'" + item + "' is spelt correctly according to " + rep + ".";
		}
		catch (DictionaryException e)
		{
			 return "Unexpected error while checking spelling: " + e;
		}

	}

	final public String filterSpRegex = "\\b([a-zA-Z]+) ?\\(sp\\?\\)";

	public void filterSp( Message mes )
	{
		final Matcher ma=Pattern.compile(filterSpRegex).matcher(mes.getMessage());
		if (ma.find())
			irc.sendContextReply(mes, spelt(ma.group(1)));

	}
	public void commandSpelt( Message mes )
	{
		irc.sendContextReply(mes, spelt(mods.util.getParamString(mes)));
	}

	public String apiDefault( String item ) throws DictionaryException
	{
		try
		{
			return apiDictionaryCom(item);
		}
		catch (DictionaryException e) {}
		try
		{
			return apiDictionary(item);
		}
		catch (DictionaryException e) {}
		try
		{
			return apiAcronymFinder(item);
		}
		catch (DictionaryException e) {}

		return apiSuggestions(item);
	}

	public String apiCorrectBy( String item ) throws DictionaryException
	{
		try
		{
			apiDictionaryCom(item);
			return "dictionary.com";
		}
		catch (DictionaryException e) {}
		try
		{
			apiDictionary(item);
			return "dict.org";
		}
		catch (DictionaryException e) {}
		try
		{
			apiAcronymFinder(item);
			return "acronym finder";
		}
		catch (DictionaryException e) {}

		return null;
	}

	public String apiSuggestions(String item) throws DictionaryException
	{
		if (item.equals(""))
			throw new DictionaryException("Lookup what?");

		URL url=generateURL("http://dictionary.reference.com/search?q=", item);

		String page;
		try
		{
			page=mods.scrape.getContentsCached(url);
		}
		catch (Exception e)
		{
			throw new DictionaryException("Error reading site: " + e, e);
		}

		if (page.indexOf("<p>No spelling suggestions were found.</p>")!=-1)
			return "No suggestions were found for '" + item + "'.";

		Matcher ma=Pattern.compile("(?s)\r?\nSuggestions:<br>(.*?)<p>No entry was found in the dictionary. Would you like to").matcher(page);

		if (ma.find())
			return prettyReply(mods.scrape.readyForIrc("Suggestions for '" + item + "': " + ma.group(1).replaceAll("<br>",", ")), url.toString(), 1);
		else
			throw new DictionaryException("Error parsing dictionary.com's suggestions reply.");

	}

	public void commandDict(Message mes)
	{
		List<String> parm=mods.util.getParams(mes, 2);
		if (parm.size()<=1)
		{
			irc.sendContextReply(mes, "Please give me a word to lookup.");
			return;
		}
		try
		{
			if (parm.size()>2)
			{
				String tionary=parm.get(1);
				if (tionary.equalsIgnoreCase("wikipedia"))
					irc.sendContextReply(mes, apiWikipedia(parm.get(2)));
				else if (tionary.equalsIgnoreCase("urbandict"))
					irc.sendContextReply(mes, apiUrbanDictionary(parm.get(2)));
				else if (tionary.equalsIgnoreCase("dict"))
					irc.sendContextReply(mes, apiDictionary(parm.get(2)));
				else if (tionary.equalsIgnoreCase("acronym"))
					irc.sendContextReply(mes, apiAcronymFinder(parm.get(2)));
				else if (tionary.equalsIgnoreCase("com"))
					irc.sendContextReply(mes, apiDictionaryCom(parm.get(2)));
				else
					irc.sendContextReply(mes, apiDefault(tionary + parm.get(2)));
			}
			else
				irc.sendContextReply(mes, apiDefault(parm.get(1)));
		}
		catch (DictionaryException e)
		{
			irc.sendContextReply(mes, "Lookup failed: " + e);
			e.printStackTrace();
			if (e.getCause()!=null)
				e.getCause().printStackTrace();
		}
	}

	private String prettyReply(String text, String url, int lines)
	{
		int maxlen=((irc.MAX_MESSAGE_LENGTH-15)*lines) - url.length();
		text=text.replaceAll("\\s+"," ");
		if (text.length()>maxlen)
			return text.substring(0, maxlen) + "..., see " + url + ".";
		else
			return text + " See " + url + ".";
	}

	private URL generateURL(String base, String url) throws DictionaryException
	{
		try
		{
			return new URL(base + URLEncoder.encode(url, "UTF-8"));
		}
		catch (UnsupportedEncodingException e)
		{
			throw new DictionaryException("Unexpected exception generating url.", e);
		}
		catch (MalformedURLException e)
		{
			throw new DictionaryException("Error, malformed url generated.", e);
		}
	}

	private Matcher getMatcher(URL url, String pat) throws DictionaryException
	{
		try
		{
			return mods.scrape.getMatcher(url, pat);
		}
		catch (FileNotFoundException e)
		{
			throw new DictionaryException("No article found (404).", e);
		}
		catch (IOException e)
		{
			throw new DictionaryException("Error reading from site. " + e, e);
		}

	}

	public String apiDictionaryCom( String item ) throws DictionaryException
	{
		return apiDictionaryCom(item, 2);
	}

	public String apiDictionaryCom( String item, int lines ) throws DictionaryException
	{
		if (item.equals(""))
			throw new DictionaryException("Lookup what?");

		URL url=generateURL("http://dictionary.reference.com/search?q=", item);

		String page;
		try
		{
			page=mods.scrape.getContentsCached(url);
		}
		catch (Exception e)
		{
			throw new DictionaryException("Error reading site: " + e, e);
		}

		if (page.indexOf("No entry found")!=-1)
			throw new DictionaryException("No matches found.");

		Matcher ma=Pattern.compile("(?s)<!-- google_ad_region_start=def -->(.*?)<!-- google_ad_region_end=def -->").matcher(page);

		if (ma.find())
		{
			final String foo=ma.group(1).replaceAll("(?s)<a href=\"https://secure.reference.com/.*?<BR>","").replaceAll("&nbsp;","").replaceAll("<SUP><FONT SIZE=\"-1\">.*?</FONT></SUP>","");
			System.out.println("[[[" + foo + "]]]");
			return prettyReply(mods.scrape.readyForIrc(foo), url.toString(), lines);
		}
		else
			throw new DictionaryException("Error parsing dictionary.com's default reply.");
	}


	public String apiWikipedia( String item ) throws DictionaryException
	{
		return apiWikipedia(item, 2);
	}

	public String apiWikipedia( String item, int lines ) throws DictionaryException
	{
		if (item.equals(""))
			throw new DictionaryException("Lookup what?");

		URL url=generateURL("http://en.wikipedia.org/wiki/", item);
		Matcher ma=getMatcher(url, "(?s)(?:(?:<!-- start content -->.*?</table>\n<p>)|(?:<!-- start content -->.*?<p>))(?!<[^bi])(.*?)</p>");

		if (ma.find())
			return prettyReply(mods.scrape.readyForIrc(ma.group(1)), url.toString(), lines);
		else
			throw new DictionaryException("Error parsing wikipedia's reply.");
	}

	public String apiUrbanDictionary( String item ) throws DictionaryException
	{
		return apiUrbanDictionary(item, 2);
	}

	public String apiUrbanDictionary( String item, int lines ) throws DictionaryException
	{
		if (item.equals(""))
			throw new DictionaryException("Lookup what?");

		URL url=generateURL("http://www.urbandictionary.com/define.php?term=", item);
		Matcher ma=getMatcher(url, "(?s)<div class=\"definition\">(.*?)</div>");

		if (ma.find())
			return prettyReply(mods.scrape.readyForIrc(ma.group(1)), url.toString(), lines);
		else
			throw new DictionaryException("Error parsing urbandictionary's reply.");
	}

	public String apiAcronymFinder( String item ) throws DictionaryException
	{
		return apiAcronymFinder(item, 1);
	}

	public String apiAcronymFinder( String item, int lines ) throws DictionaryException
	{
		if (item.equals(""))
			throw new DictionaryException("Lookup what?");

		URL url=generateURL("http://www.acronymfinder.com/af-query.asp?String=exact&Find=Find&Acronym=", item);

		String page;

		try
		{
			page=mods.scrape.getContentsCached(url);
		}
		catch (Exception e)
		{
			throw new DictionaryException("Error reading site: " + e, e);
		}

		if (page.indexOf("support Java-based browsers/utilities, screen-scraping")!=-1)
			throw new DictionaryException("Oh nose, acronymfinder have detected our screen-scraping h4x! Run, run and hide!");

		Matcher ma=Pattern.compile("(?s)src=\"l.gif\" title=\"direct link\" ></a>&nbsp;(.*?)</td>").matcher(page);

		StringBuilder s=new StringBuilder();

		while (ma.find() && s.length()<((irc.MAX_MESSAGE_LENGTH-77-50)*lines))
			s.append(mods.scrape.readyForIrc(ma.group(1))).append(", ");

		if (s.length()==0)
			throw new DictionaryException("No matches identified on acronymfinder.");

		s.setLength(s.length()-2);
		s.append(".");

		if (ma.find())
			s.append(".. See " + url.toString());

		return s.toString();
	}


	public String apiDictionary( String item ) throws DictionaryException
	{
		return apiDictionary(item, 2);
	}

	public String apiDictionary( String item, int lines ) throws DictionaryException
	{
		try
		{
			String word = item;

			net.zuckerfrei.jcfd.Dict dict = DictFactory.getInstance().getDictClient();

			DefinitionList defList = dict.define(word);

			StringBuilder rep=new StringBuilder();

			int count=defList.count();
			if (count==0)
				throw new DictionaryException("No definitions found.");

			rep.append("There " + (count==1 ? "is" : "are") + " " + count + " definition" + (count==1 ? "" : "s") + " for " + word + ": ");
			Definition def;

			if (defList.hasNext())
			{
				def = defList.next();

				rep.append(def.getContent() + " (" + def.getDatabase().getName() + ")");

				String[] links = def.getLinks();
				if (links.length!=0)
					rep.append(" See also: ");
				for (int i = 0; i < links.length; i++)
					rep.append(links[i] + ", ");

			}

			dict.close();
			return prettyReply(mods.scrape.readyForIrc(rep.toString().replaceAll("\r\n","\n").replaceAll("\n\n"," || ").replaceAll("\n", " ")), "http://www.dict.org/bin/Dict?Form=Dict2&Database=*&Query=" + item, lines);
		}
		catch (net.zuckerfrei.jcfd.DictException e)
		{
			throw new DictionaryException("Unexpected exception.", e);
		}
	}

	final int quotes = 4;
	/**
	 * @param id ID of quote to return, any invalid for "all".
	 */
	public String[] apiQuoteOfTheDay( int id ) throws DictionaryException
	{
		URL url=generateURL("http://quotationspage.com/qotd.html", "");

		String page;

		try
		{
			page=mods.scrape.getContentsCached(url);
		}
		catch (Exception e)
		{
			throw new DictionaryException("Error reading site: " + e, e);
		}

		Matcher ma=Pattern.compile("<dt class=\"quote\"><a title=\"Click for further information about this quotation\" href=\"/quote/[0-9]+.html\">(.*?)</a>.*?<a href=\"/quotes/.+?/\">(.+?)</a>").matcher(page);
		List<String> ret = new ArrayList<String>();

		int i=0;
		while (ma.find())
			ret.add(mods.scrape.readyForIrc(++i + ": \"" + ma.group(1) + "\" -- " + ma.group(2)));

		System.out.println(ret.get(0));
		if (ret.size() !=4 )
			throw new DictionaryException("Unable to read quotes.");

		if (id >=1 && id <=4)
			return new String[] { ret.get(id-1) };

		return ret.toArray(new String[] {});
	}

	private final int randomQuote()
	{
		return new Random().nextInt(quotes)+1;
	}

	public String[] helpCommandQuoteOfTheDay = {
		"Returns one of the current Quotes of the Day from http://quotationspage.com/.",
		"[<id>]",
		"<id> is the optional quoteid to return. 'all' to recieve all of the quotes via. pm.",
	};

	public void commandQuoteOfTheDay(Message mes)
	{
		List<String> params = mods.util.getParams(mes);

		int passid;
		if (params.size() <= 1)
			passid = randomQuote();
		else
			if (params.get(1).equalsIgnoreCase("all"))
				passid = 0;
			else
				try
				{
					passid = Integer.parseInt(params.get(1));
					if (passid > 4 || passid < 1)
						passid = randomQuote();
				}
				catch (NumberFormatException e)
				{
					passid = randomQuote();
				}

		try
		{
			irc.sendContextReply(mes, apiQuoteOfTheDay(passid));
		}
		catch (DictionaryException e)
		{
			irc.sendContextReply(mes, "Unexpected error: " + e);
		}

	}
}
