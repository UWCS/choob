import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.net.*;

public class Weather
{

	Modules mods;
	private IRCInterface irc;

	public Weather (Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public String[] helpCommandWeather = {
		"Return weather data for a location",
		"<LOCATION>",
		"<LOCATION> can be any of a text location eg: 'berlin, germany' or a UK postcode or a US zip code"
	};

	public String[] optionsUser = { "Location" };
	public String[] optionsUserDefaults = { "CV4" };

	public String[] helpOptionWeatherLocation = {
		"Set your home location for the weather command with no parameters."
	};

	public boolean optionCheckUserWeather(String value, String nick) {
		return true; //hmm
	}
	
	private String checkOption(String userNick) {
		try {
 			return (String)mods.plugin.callAPI("Options", "GetUserOption", userNick,"Location", optionsUserDefaults[0]);
		} catch (ChoobNoSuchCallException e) {
			return optionsUserDefaults[0];
		}
	}

	public void commandWeather( Message mes )
	{

		try
		{
			int ret = (Integer)mods.plugin.callAPI("Flood", "IsFlooding", "Weather" + mes.getNick(), 900000, 2);
			if (ret != 0)
			{
				irc.sendContextReply(mes, "This command may only be used once every 15 minutes.");
				return;
			}
		}
		catch (ChoobNoSuchCallException e){ }

		List<String> params = mods.util.getParams(mes,1);
		String item = null;
		if (params.size()<2)
		{
			item = checkOption(mes.getNick());
// 			irc.sendContextReply(mes, "Please give me a location to lookup.");
// 			return;
		}
		
		try{
			
			if (item == null) item = mods.util.getParams(mes, 1).get(1);
			if (item.equals(""))
			{
				irc.sendContextReply(mes, "Please give me a location to lookup.");
				return;
			}
	
			//check for US Zip Code
			if (item.matches("[0-9][0-9][0-9][0-9][0-9]"))
			{
				URL url=generateURL("http://www.weather.com/weather/local/", item);

				Matcher ma=getMatcher(url, "(?s)<TABLE BORDER=0 CELLPADDING=0 CELLSPACING=0 WIDTH=\"100%\">(.*?)</TABLE>");
				if (ma.find())
				{
					irc.sendContextReply(mes, prettyReply(mods.scrape.readyForIrc(stripUndesirables(ma.group(1))), url.toString(), 2));
					return;
				}
				else
				{
					irc.sendContextReply(mes, "Unable to find any weather data for that location");
					return;
				}
			}
	
			//check for UK postcode
			if (item.matches("[A-Za-z][A-Za-z][0-9][0-9][0-9][A-Za-z][A-Za-z]") 
					|| item.matches("[A-Za-z][A-Za-z][0-9][0-9] [0-9][A-Za-z][A-Za-z]")
					|| item.matches("[A-Za-z][A-Za-z][0-9][0-9][A-Za-z][A-Za-z]")
					|| item.matches("[A-Za-z][A-Za-z][0-9] [0-9][A-Za-z][A-Za-z]")
			)
			{
				URL url=generateURL("http://uk.weather.com/weather/local/", item.substring(0,4));
		
		
				//Matcher ma=getMatcher(url, "(?s)<div><p class=g>(.*?)<td class=j>");
				Matcher ma=getMatcher(url, "(?s)CLASS=\"obsText\" ALIGN=\"CENTER\">(.*?)</TABLE>");
				if (ma.find())
				{
					irc.sendContextReply(mes,  prettyReply(mods.scrape.readyForIrc(stripUndesirables(ma.group(1))), url.toString(), 2));
					return;
				}
				else
				{
					url=generateURL("http://uk.weather.com/weather/local/", item.substring(0,3));
			
					ma=getMatcher(url, "(?s)CLASS=\"obsText\" ALIGN=\"CENTER\">(.*?)</TABLE>");
					if (ma.find())
					{
						irc.sendContextReply(mes,  prettyReply(mods.scrape.readyForIrc(stripUndesirables(ma.group(1))), url.toString(), 2));
						return;
					}else
					{
							irc.sendContextReply(mes, "Unable to find any weather data for that location");
							return;
					}
				}
			}
	
			URL url=generateURL("http://uk.weather.com/search/search?where=", item);

			Matcher ma=getMatcher(url, "(?s)CLASS=\"obsText\" ALIGN=\"CENTER\">(.*?)</TABLE>");
			Matcher maa = getMatcher(url, "(?s)Towns/Cities.*?href=\"/weather/local/(.*?)\">");
			if (ma.find())
			{
				irc.sendContextReply(mes,  prettyReply(mods.scrape.readyForIrc(stripUndesirables(ma.group(1))), url.toString(), 2));
				return;
			}
			else
			{
				if (maa.find())
				{
					URL urla=generateURL("http://uk.weather.com/weather/local/", maa.group(1));
					Matcher maaa = getMatcher(urla, "(?s)CLASS=\"obsText\" ALIGN=\"CENTER\">(.*?)</TABLE>");
					if (maaa.find())
					{
						irc.sendContextReply(mes,  prettyReply(mods.scrape.readyForIrc(stripUndesirables(maaa.group(1))), urla.toString(), 2));
						return;
					}
					else
					{
						irc.sendContextReply(mes, "Unable to find any weather data for that location");
						return;
					}
				}
			}
			irc.sendContextReply(mes, "Unable to find any weather data for that location");
		}
		catch (LookupException e) {irc.sendContextReply(mes, "An error occured while looking up results");}
	}


	private String stripUndesirables(String original)
	{
		String toReturn = original;
		int x = 0;
		while (toReturn.matches(".*\\s\\s.*") && x < 100)
		{
			toReturn = toReturn.replaceAll("\\s\\s"," ");
			x++;
		}
		toReturn = toReturn.replaceAll("\t","").replaceAll("\r","").replaceAll("\n"," ");
		return toReturn;
	}

	private URL generateURL(String base, String url) throws LookupException
	{
		try
		{
			return new URL(base + URLEncoder.encode(url, "UTF-8"));
		}
		catch (UnsupportedEncodingException e)
		{
			throw new LookupException("Unexpected exception generating url.", e);
		}
		catch (MalformedURLException e)
		{
			throw new LookupException("Error, malformed url generated.", e);
		}
	}

	private Matcher getMatcher(URL url, String pat) throws LookupException
	{
		try
		{
			return mods.scrape.getMatcher(url,0, pat);
		}
		catch (FileNotFoundException e)
		{
			throw new LookupException("No article found (404).", e);
		}
		catch (IOException e)
		{

			throw new LookupException("Error reading from site. " + e, e);
		}
	}

	private String prettyReply(String text, String url, int lines)
	{
		int maxlen=((irc.MAX_MESSAGE_LENGTH-100)*lines) -url.length();

		String returnText = text;

		if (returnText.length()>maxlen)
			return returnText.substring(0, maxlen) + "..., see " + url;
		else
			return returnText + ". See " + url;
	}
}

class LookupException extends ChoobException
{
	public LookupException(String text)
	{
		super(text);
	}
	public LookupException(String text, Throwable e)
	{
		super(text, e);
	}
	public String toString()
	{
		return getMessage();
	}

}
