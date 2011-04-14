import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.zuckerfrei.jcfd.Definition;
import net.zuckerfrei.jcfd.DefinitionList;
import net.zuckerfrei.jcfd.DictFactory;
import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

class DictionaryException extends ChoobException
{
	private static final long serialVersionUID = 1L;

	public DictionaryException(final String text)
	{
		super(text);
	}
	public DictionaryException(final String text, final Throwable e)
	{
		super(text, e);
	}

	@Override
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

	private final Modules mods;
	private final IRCInterface irc;
	public Dict(final Modules mods, final IRCInterface irc)
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

	String spelt(final String item)
	{
		try
		{
			final String rep=apiCorrectBy(item);
			if (rep==null)
				return apiSuggestions(item);
			return "'" + item + "' is spelt correctly according to " + rep + ".";
		}
		catch (final DictionaryException e)
		{
			 return "Unexpected error while checking spelling: " + e;
		}

	}

	final public String filterSpRegex = "\\b([a-zA-Z]+) ?\\(sp\\?\\)";

	public void filterSp( final Message mes )
	{
		final Matcher ma=Pattern.compile(filterSpRegex).matcher(mes.getMessage());
		if (ma.find())
			irc.sendContextReply(mes, spelt(ma.group(1)));

	}
	public void commandSpelt( final Message mes )
	{
		irc.sendContextReply(mes, spelt(mods.util.getParamString(mes)));
	}

	public String apiDefault( final String item ) throws DictionaryException
	{
		try
		{
			return apiDictionaryCom(item);
		}
		catch (final DictionaryException e)
		{
			// Fall through..
		}
		try
		{
			return apiDictionary(item);
		}
		catch (final DictionaryException e)
		{
			// Fall through..
		}
		try
		{
			return apiAcronymFinder(item);
		}
		catch (final DictionaryException e)
		{
			// Fall through..
		}

		return apiSuggestions(item);
	}

	public String apiCorrectBy( final String item )
	{
		try
		{
			apiDictionaryCom(item);
			return "dictionary.com";
		}
		catch (final DictionaryException e)
		{
			// Fall through..
		}
		try
		{
			apiDictionary(item);
			return "dict.org";
		}
		catch (final DictionaryException e)
		{
			// Fall through..
		}
		try
		{
			apiAcronymFinder(item);
			return "acronym finder";
		}
		catch (final DictionaryException e)
		{
			// Fall through..
		}

		return null;
	}

	public String apiSuggestions(final String item) throws DictionaryException
	{
		if (item.equals(""))
			throw new DictionaryException("Lookup what?");

		final URL url=generateURL("http://dictionary.reference.com/search?q=", item);

		String page;
		try
		{
			page=mods.scrape.getContentsCached(url);
		}
		catch (final Exception e)
		{
			throw new DictionaryException("Error reading site: " + e, e);
		}

		if (page.indexOf("<p>No spelling suggestions were found.</p>")!=-1)
			return "No suggestions were found for '" + item + "'.";

		final Matcher ma=Pattern.compile("(?s)\r?\nSuggestions:<br>(.*?)<p>No entry was found in the dictionary. Would you like to").matcher(page);

		if (ma.find())
			return prettyReply(mods.scrape.readyForIrc("Suggestions for '" + item + "': " + ma.group(1).replaceAll("<br>",", ")), url.toString(), 1);
		throw new DictionaryException("Error parsing dictionary.com's suggestions reply.");

	}

	public void commandDict(final Message mes)
	{
		final List<String> parm=mods.util.getParams(mes, 2);
		if (parm.size()<=1)
		{
			irc.sendContextReply(mes, "Please give me a word to lookup.");
			return;
		}
		try
		{
			if (parm.size()>2)
			{
				final String tionary=parm.get(1);
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
		catch (final DictionaryException e)
		{
			irc.sendContextReply(mes, "Lookup failed: " + e);
			e.printStackTrace();
			if (e.getCause()!=null)
				e.getCause().printStackTrace();
		}
	}

	private String prettyReply(String text, final String url, final int lines)
	{
		final int maxlen=(IRCInterface.MAX_MESSAGE_LENGTH-15)*lines - url.length();
		text=text.replaceAll("\\s+"," ");
		if (text.length()>maxlen)
			return text.substring(0, maxlen) + "..., see " + url + ".";
		return text + " See " + url + ".";
	}

	private URL generateURL(final String base, final String url) throws DictionaryException
	{
		try
		{
			return new URL(base + URLEncoder.encode(url, "UTF-8"));
		}
		catch (final UnsupportedEncodingException e)
		{
			throw new DictionaryException("Unexpected exception generating url.", e);
		}
		catch (final MalformedURLException e)
		{
			throw new DictionaryException("Error, malformed url generated.", e);
		}
	}

	private Matcher getMatcher(final URL url, final String pat) throws DictionaryException
	{
		try
		{
			return mods.scrape.getMatcher(url, pat);
		}
		catch (final FileNotFoundException e)
		{
			throw new DictionaryException("No article found (404).", e);
		}
		catch (final IOException e)
		{
			throw new DictionaryException("Error reading from site. " + e, e);
		}

	}

	public String apiDictionaryCom( final String item ) throws DictionaryException
	{
		return apiDictionaryCom(item, 2);
	}

	public String apiDictionaryCom( final String item, final int lines ) throws DictionaryException
	{
		if (item.equals(""))
			throw new DictionaryException("Lookup what?");

		final URL url=generateURL("http://dictionary.reference.com/search?q=", item);

		String page;
		try
		{
			page=mods.scrape.getContentsCached(url);
		}
		catch (final Exception e)
		{
			throw new DictionaryException("Error reading site: " + e, e);
		}

		if (page.indexOf("No entry found")!=-1)
			throw new DictionaryException("No matches found.");

		final Matcher ma=Pattern.compile("(?s)<!-- google_ad_region_start=def -->(.*?)<!-- google_ad_region_end=def -->").matcher(page);

		if (ma.find())
		{
			final String foo=ma.group(1).replaceAll("(?s)<a href=\"https://secure.reference.com/.*?<BR>","").replaceAll("&nbsp;","").replaceAll("<SUP><FONT SIZE=\"-1\">.*?</FONT></SUP>","");
			System.out.println("[[[" + foo + "]]]");
			return prettyReply(mods.scrape.readyForIrc(foo), url.toString(), lines);
		}
		throw new DictionaryException("Error parsing dictionary.com's default reply.");
	}


	public String apiWikipedia( final String item ) throws DictionaryException
	{
		return apiWikipedia(item, 2);
	}

	public String apiWikipedia( final String item, final int lines ) throws DictionaryException
	{
		if (item.equals(""))
			throw new DictionaryException("Lookup what?");

		final URL url=generateURL("http://en.wikipedia.org/wiki/", item);
		final Matcher ma=getMatcher(url, "(?s)(?:(?:<!-- start content -->.*?</table>\n<p>)|(?:<!-- start content -->.*?<p>))(?!<[^bi])(.*?)</p>");

		if (ma.find())
			return prettyReply(mods.scrape.readyForIrc(ma.group(1)), url.toString(), lines);
		throw new DictionaryException("Error parsing wikipedia's reply.");
	}

	public String apiUrbanDictionary( final String item ) throws DictionaryException
	{
		return apiUrbanDictionary(item, 2);
	}

	public String apiUrbanDictionary( final String item, final int lines ) throws DictionaryException
	{
		if (item.equals(""))
			throw new DictionaryException("Lookup what?");

		final URL url=generateURL("http://www.urbandictionary.com/define.php?term=", item);
		final Matcher ma=getMatcher(url, "(?s)<div class=\"definition\">(.*?)</div>");

		if (ma.find())
			return prettyReply(mods.scrape.readyForIrc(ma.group(1)), url.toString(), lines);
		throw new DictionaryException("Error parsing urbandictionary's reply.");
	}

	public String apiAcronymFinder( final String item ) throws DictionaryException
	{
		return apiAcronymFinder(item, 1);
	}

	public String apiAcronymFinder( final String item, final int lines ) throws DictionaryException
	{
		if (item.equals(""))
			throw new DictionaryException("Lookup what?");

		final URL url=generateURL("http://www.acronymfinder.com/af-query.asp?String=exact&Find=Find&Acronym=", item);

		String page;

		try
		{
			page=mods.scrape.getContentsCached(url);
		}
		catch (final Exception e)
		{
			throw new DictionaryException("Error reading site: " + e, e);
		}

		if (page.indexOf("support Java-based browsers/utilities, screen-scraping")!=-1)
			throw new DictionaryException("Oh nose, acronymfinder have detected our screen-scraping h4x! Run, run and hide!");

		final Matcher ma=Pattern.compile("(?s)src=\"l.gif\" title=\"direct link\" ></a>&nbsp;(.*?)</td>").matcher(page);

		final StringBuilder s=new StringBuilder();

		while (ma.find() && s.length()<(IRCInterface.MAX_MESSAGE_LENGTH-77-50)*lines)
			s.append(mods.scrape.readyForIrc(ma.group(1))).append(", ");

		if (s.length()==0)
			throw new DictionaryException("No matches identified on acronymfinder.");

		s.setLength(s.length()-2);
		s.append(".");

		if (ma.find())
			s.append(".. See " + url.toString());

		return s.toString();
	}


	public String apiDictionary( final String item ) throws DictionaryException
	{
		return apiDictionary(item, 2);
	}

	public String apiDictionary( final String item, final int lines ) throws DictionaryException
	{
		try
		{
			final String word = item;

			final net.zuckerfrei.jcfd.Dict dict = DictFactory.getInstance().getDictClient();

			final DefinitionList defList = dict.define(word);

			final StringBuilder rep=new StringBuilder();

			final int count=defList.count();
			if (count==0)
				throw new DictionaryException("No definitions found.");

			rep.append("There " + (count==1 ? "is" : "are") + " " + count + " definition" + (count==1 ? "" : "s") + " for " + word + ": ");
			Definition def;

			if (defList.hasNext())
			{
				def = defList.next();

				rep.append(def.getContent() + " (" + def.getDatabase().getName() + ")");

				final String[] links = def.getLinks();
				if (links.length!=0)
					rep.append(" See also: ");
				for (final String link : links)
					rep.append(link + ", ");

			}

			dict.close();
			return prettyReply(mods.scrape.readyForIrc(rep.toString().replaceAll("\r\n","\n").replaceAll("\n\n"," || ").replaceAll("\n", " ")), "http://www.dict.org/bin/Dict?Form=Dict2&Database=*&Query=" + item, lines);
		}
		catch (final net.zuckerfrei.jcfd.DictException e)
		{
			throw new DictionaryException("Unexpected exception.", e);
		}
	}

	final int quotes = 4;
	/**
	 * @param id ID of quote to return, any invalid for "all".
	 */
	public String[] apiQuoteOfTheDay( final int id ) throws DictionaryException
	{
		final URL url=generateURL("http://quotationspage.com/qotd.html", "");

		String page;

		try
		{
			page=mods.scrape.getContentsCached(url);
		}
		catch (final Exception e)
		{
			throw new DictionaryException("Error reading site: " + e, e);
		}

		final Matcher ma=Pattern.compile("<dt class=\"quote\"><a title=\"Click for further information about this quotation\" href=\"/quote/[0-9]+.html\">(.*?)</a>.*?<a href=\"/quotes/.+?/\">(.+?)</a>").matcher(page);
		final List<String> ret = new ArrayList<String>();

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

	public void commandQuoteOfTheDay(final Message mes)
	{
		final List<String> params = mods.util.getParams(mes);

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
				catch (final NumberFormatException e)
				{
					passid = randomQuote();
				}

		try
		{
			irc.sendContextReply(mes, apiQuoteOfTheDay(passid));
		}
		catch (final DictionaryException e)
		{
			irc.sendContextReply(mes, "Unexpected error: " + e);
		}

	}
}
