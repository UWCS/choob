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
			mods.util.getVersion()
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
		"<dictionary> is the dictionary to use, valid dictioanries include 'wikipedia', 'urbandict', 'acronym'(finder) and 'dict'(.org)",
		"<word> is the word to look up"
	};

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
					irc.sendContextReply(mes, apiUrbanDictionary(parm.get(2)));
				else if (tionary.equalsIgnoreCase("acronym"))
					irc.sendContextReply(mes, apiAcronymFinder(parm.get(2)));
				else
					irc.sendContextReply(mes, apiDictionary(tionary + parm.get(2)));
			}
			else
				irc.sendContextReply(mes, apiDictionary(parm.get(1)));
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
		int maxlen=((irc.MAX_MESSAGE_LENGTH-15)*lines) -url.length();
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
			throw new DictionaryException("Error parsing reply.");
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
			throw new DictionaryException("Error parsing reply.");
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
		Matcher ma=getMatcher(url, "src=\"l.gif\" title=\"direct link\" ></a>&nbsp;(.*)");

		StringBuilder s=new StringBuilder();

		while (ma.find() && s.length()<((irc.MAX_MESSAGE_LENGTH-77-50)*lines))
			s.append(mods.scrape.readyForIrc(ma.group(1))).append(", ");

		if (s.length()==0)
			throw new DictionaryException("No matches identified.");

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
}
