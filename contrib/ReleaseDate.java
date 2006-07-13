/*
 * ReleaseDate.java
 *
 * Created on 12 July 2006, 16:18
 */

import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.net.*;

/**
 * Plugin to retrieve the release date of a particular game from Gameplay.co.uk
 * @author Chris Hawley (Blood_God)
 */
public class ReleaseDate {
	
	Modules mods;
	private IRCInterface irc;

	//ReleaseDate help
	public String[] helpCommandReleaseDate = {
		"Attempts to look up a game's release date on Gameplay.co.uk. Results include date, title, platform and Gameplay price.",
		"<TITLE>",
		"<TITLE> the title of a game that you wish to get information for."
	};	
	
	/** 
	 * Creates a new instance of ReleaseDate 
	 * @param mods The modules available.
	 * @param irc The IRCInterface.
	 */
	public ReleaseDate(Modules mods, IRCInterface irc) {
		this.mods = mods;
		this.irc = irc;
	}
	
	/**
	 * Get the information for the plugin.
	 * @return The information strings.
	 */
	public String[] info() {
		return new String[] {
			"Plugin to retrieve the release date of a particular game from Gameplay.co.uk",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}
	
	//TODO: Return multiple results, and filter out "guide" results - as per the JB version this is re-implementing
	/**
	 * The command provided by this plugin.
	 * @param mes The command input from which parameters will be extracted.
	 */
	public void commandReleaseDate(Message mes) {
		String param = mods.util.getParamString(mes);
		try {
			URL url = generateURL("http://shop.gameplay.co.uk/webstore/advanced_search.asp?keyword=", param);
			Matcher noResultMatcher = getMatcher(url, "(?s)" + "Sorry, your search for" + "(.*?)" + "returned no results.");
			if (noResultMatcher.find()) {
				irc.sendContextReply(mes, "Sorry, no information was found for \"" + param + "\".");
			} else {
				Matcher gotResultMatcher = getMatcher(url, "(?s)" + "<h2 class=\"vlgWhite\">" + "(.*?)" + "<a href=\"productpage.asp?" + "(.*?)" +  "class=\"vlgWhite\">" + "(.*?)" + "</a></td></h2>" + "(.*?)" + "<div class=\"vsmorange10\">" + "(.*?)" + "</div>" + "(.*?)" + "<td valign=\"bottom\">" + "(.*?)" + "RRP");
				if (gotResultMatcher.find()) {
					irc.sendContextReply(mes, mods.scrape.readyForIrc(gotResultMatcher.group(3)).replaceAll("\\s+"," ") +  " (" +  mods.scrape.readyForIrc(gotResultMatcher.group(5)) + ")" + mods.scrape.readyForIrc(prettyReply(gotResultMatcher.group(7), url.toString(), 1)));
				} else {
					irc.sendContextReply(mes, "There was an error parsing the results. See " + url.toString());
				}
			}

		} catch (LookupException e) {
			irc.sendContextReply(mes, "Error looking up results.");
		} catch (NullPointerException e) {
			irc.sendContextReply(mes, "Error looking up results.");
		}
	}
	
	/* -- Start of code stolen from Dict.java -- */
		private String prettyReply(String text, String url, int lines) {
		int maxlen=((irc.MAX_MESSAGE_LENGTH-15)*lines) - url.length();
		text=text.replaceAll("\\s+"," ");
		if (text.length()>maxlen) {
			return text.substring(0, maxlen) + "..., see " + url + ".";
		} else {
			return text + " See " + url + ".";
		}
	}

	private URL generateURL(String base, String url) throws LookupException {
		try	{
			return new URL(base + URLEncoder.encode(url, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new LookupException("Unexpected exception generating url.", e);
		} catch (MalformedURLException e) {
			throw new LookupException("Error, malformed url generated.", e);
		}
	}

	private Matcher getMatcher(URL url, String pat) throws LookupException {
		try	{
			return mods.scrape.getMatcher(url, pat);
		} catch (FileNotFoundException e) {
			throw new LookupException("No article found (404).", e);
		} catch (IOException e)	{
			throw new LookupException("Error reading from site. " + e, e);
		}
	}
	/* -- End of stolen code -- */
}

public class LookupException extends ChoobException {
	
	public LookupException(String text)	{
		super(text);
	}
	
	public LookupException(String text, Throwable e) {
		super(text, e);
	}
	
	public String toString() {
		return getMessage();
	}

}
