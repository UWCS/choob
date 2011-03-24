/*
 * ReleaseDate.java
 *
 * Created on 12 July 2006, 16:18
 */

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

/**
 * Plugin to retrieve the release date of a particular game from Gameplay.co.uk
 * @author Chris Hawley (Blood_God)
 */
public class ReleaseDate {

	Modules mods;
	private final IRCInterface irc;

	//ReleaseDate help
	public String[] helpCommandReleaseDate = {
//		"Attempts to look up an item's release date on Play.com or, if no results are found, Gameplay.co.uk. Results include date or stock information, title, platform and price.",
		"Attempts to look up an item's release date on Play.com. Results include date or stock information, title, platform and price.",
		"<TITLE>",
		"<TITLE> the title of an item that you wish to get information for. This may only contain A-Z0-9.,;:_- and spaces."
	};

	/**
	 * Creates a new instance of ReleaseDate
	 * @param mods The modules available.
	 * @param irc The IRCInterface.
	 */
	public ReleaseDate(final Modules mods, final IRCInterface irc) {
		this.mods = mods;
		this.irc = irc;
	}

	/**
	 * Get the information for the plugin.
	 * @return The information strings.
	 */
	public String[] info() {
		return new String[] {
			"Plugin to retrieve the release date of a particular item from Play.com Gameplay.co.uk",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	//TODO: Return multiple results, and filter out "guide" results - as per the JB version this is re-implementing
	//FIXME: Occaisionally gameplay add 'style="color: #aabbcc"' to some of the tags currently used for parsing.
	//       These need taking into consideration as they are currently ignored by the regular expressions.
	/**
	 * The command provided by this plugin.
	 * @param mes The command input from which parameters will be extracted.
	 */
	public void commandReleaseDate(final Message mes) {
		final String param = mods.util.getParamString(mes);
		try {
			//Check for only sensible input with at least one alpha-numeric character.
			final Pattern dodgyCharPattern = Pattern.compile("^[\\s\\w\\-\\:\\;\\.\\,]*[a-zA-Z0-9]+[\\s\\w\\-\\:\\;\\.\\,]*$");
			final Matcher dodgyCharMatcher = dodgyCharPattern.matcher(param);
			if (dodgyCharMatcher.matches()) {
			final String playResults = playSearch(param);
			/*
			 * - Gameplay's results are slow and sucky, let's not use it at present.
				//Check if we got a viable result from play.com, if so return this - otherwise return whatever gameplay gives us.
				if ((!playResults.equals("Error looking up results.")) && (!playResults.equals("Sorry, no information was found for \"" + param + "\"."))) {
				*/
					irc.sendContextReply(mes, playResults);
					/*
				} else {
					String gameplayResults = gameplaySearch(param);
					irc.sendContextReply(mes, gameplayResults);
				}
				*/
			} else {
				irc.sendContextReply(mes, "Sorry, I'm limited to A-Z0-9,._;: hyphen and space characters. At least one alpha-numeric character must be provided.");
			}
		} catch (final NullPointerException e) {
			irc.sendContextReply(mes, "Error looking up results.");
		} catch (final IllegalStateException e) {
			irc.sendContextReply(mes, "Error looking up results.");
		}
	}

	/**
	 * Search Gameplay.co.uk
	 * @param param The parameter to search for on the website.
	 * @returns A message string to display.
	 */
	protected String gameplaySearch(final String param) {
		try {
			final URL url = generateURL("http://shop.gameplay.co.uk/webstore/advanced_search.asp?keyword=", param);
			//Info from the following regepx: Title - group 1
			final Matcher gameplayNoResultMatcher = getMatcher(url, "(?s)" + "Sorry, your search for" + "(.*?)" + "returned no results.");
			//Info from the following regepx: Title - group 3, Category - group 5, Release date + price - Group 7
			final Matcher gameplayGotResultMatcher = getMatcher(url, "(?s)" + "<h2 class=\"vlgWhite\">" + "(.*?)" + "<a href=\"productpage.asp?" + "(.*?)" +  "class=\"vlgWhite\">" + "(.*?)" + "</a></td></h2>" + "(.*?)" + "<div class=\"vsmorange10\">" + "(.*?)" + "</div>" + "(.*?)" + "<td valign=\"bottom\">" + "(.*?)" + "((RRP)|(<a href=\"add_to_basket))");
			//Info from the following regepx: Title - group 1, releasedate - group 5, price - group 3
			final Matcher gameplaySingleResultMatcher = getMatcher(url, "(?s)" + "<h1 class=\"bbHeader\">" + "(.*?)" + "</h1>" + "(.*?)" + "<td style=\"padding: 6px;\">" + "(.*?)" + "<img src=\"http://shop" + "(.*?)" + "<td valign=\"bottom\" colspan=\"2\">" + "(.*?)" + "<b>Category:</b><a href=");
			if (gameplayNoResultMatcher.find()) {
				return "Sorry, no information was found for \"" + param + "\".";
			} else if (gameplayGotResultMatcher.find()) {
				return prettyReply(mods.scrape.readyForIrc(gameplayGotResultMatcher.group(3) + " (" + gameplayGotResultMatcher.group(5) + ")" + gameplayGotResultMatcher.group(7)), url.toString(), 1);
			} else if (gameplaySingleResultMatcher.find()) {
				return prettyReply(mods.scrape.readyForIrc(gameplaySingleResultMatcher.group(1) + gameplaySingleResultMatcher.group(5) + gameplaySingleResultMatcher.group(3)), url.toString(), 1);
			} else {
				return "There was an error parsing the results. See " + url.toString();
			}
		} catch (final LookupException e) {
			return "Error looking up results.";
		} catch (final NullPointerException e) {
			return "Error looking up results.";
		} catch (final IllegalStateException e) {
			return "Error looking up results.";
		}
	}

	/**
	 * Search Play.com
	 * @param param The parameter to search for on the website.
	 * @returns A message string to display.
	 */
	private String playSearch(final String param) {
		try {
			final URL url = generateURL("http://www.play.com/Search.aspx?searchtype=allproducts&searchstring=", param);
			final Matcher playNoResultMatcher = getMatcher(url, "(?s)" + "<td class=\"textblack\" align=\"center\">There were no results for your search");
			final Matcher playNoExactResultMatcher = getMatcher(url, "(?s)" + "<p class=\"searchterms\"><span>We could not find an exact match");
			//Info from the following regepx: Title - group 4, category - group 1, releasedate - group 5, price - group 6
			final Matcher playGotResultMatcher = getMatcher(url, "(?s)" + "View  results in " + "(.*?)" + " »" + "(.*?)" + "<div class=\"info\"><h5><a href=\"" + "(.*?)" + "Product.html\">" + "(.*?)" + "</a></h5><p class=\"stock\"><span></span>" + "(.*?)" + "</p><h6><span>our price:  </span>" + "(.*?)" + " ((Delivered</h6><p class=\"saving\">RRP)|(Delivered</h6><div class=\"buy\">))");
			//Info from the following regepx: title - group 2, releasedate - group 3, price - group 4
			final Matcher playAlternativeResultMatcher = getMatcher(url, "(?s)" + "<div class=\"info\"><h5><a href=\"" + "(.*?)" + "Product.html\">" + "(.*?)" + "</a></h5><p class=\"stock\"><span></span>" + "(.*?)" + "</p><h6><span>our price:  </span>" + "(.*?)" + " Delivered</h6><p class=\"saving\">RRP");
			//Info from the following regexp: title - group 6, category - group 1, releasedate - group 7, price - group 8
			final Matcher playResultMatcherThree = getMatcher(url, "(?s)<span>Searching for (.*?) titles containing(.*?)</span></p><p class=\"results\"><span>Results </span><STRONG>(.*?)</STRONG></p></div><div class=\"slice\"><table cellspacing=\"0\"><tr><td><div class=\"image\">(.*?)</div></td><td class=\"wide\"><div class=\"info\"><h5><a href=\"(.*?)\">(.*?)</a></h5><p class=\"stock\"><span></span>(.*?)</p><h6><span>our price:  </span>(.*?) Delivered</h6><div class=\"buy\">");
			//Info from the following regexp: title - group 1, releasedate - group 4, price - group 3
			final Matcher playProductPageMatcher = getMatcher(url, "(?s)" + "<div class=\"texthead\"><div><div><h2>" + "(.*?)" + "</h2></div></div></div><div class=\"box\">" + "(.*?)" + "<div class=\"info\"><h6><span><span>our price:</span> </span>" + "(.*?)" + " Delivered</h6><p class=\"stock\"><span>availability:  </span>" + "(.*?)" + "</p><p class=\"note\">");
			if (playNoResultMatcher.find()) {
				return "Sorry, no information was found for \"" + param + "\".";
			} else if (playNoExactResultMatcher.find()) {
				return "Sorry, no information was found for \"" + param + "\".";
			} else if (playGotResultMatcher.find()) {
				return prettyReply(mods.scrape.readyForIrc("<b>" + playGotResultMatcher.group(4) + "</b> (" + playGotResultMatcher.group(1) + ") " + playGotResultMatcher.group(5).replaceAll("<br/>", "<b> ") + "</b> Play.com price:" + playGotResultMatcher.group(6)), url.toString(), 1);
			} else if (playAlternativeResultMatcher.find()) {
				return prettyReply(mods.scrape.readyForIrc("<b>" + playAlternativeResultMatcher.group(2) + "</b> " + playAlternativeResultMatcher.group(3).replaceAll("<br/>", "<b> ") + "</b> Play.com price:" + playAlternativeResultMatcher.group(4)), url.toString(), 1);
			} else if (playResultMatcherThree.find()) {
				return prettyReply(mods.scrape.readyForIrc("<b>" + playResultMatcherThree.group(6) + "</b> (" + playResultMatcherThree.group(1) + ") " + playResultMatcherThree.group(7).replaceAll("<br/>", "<b> ") + "</b> Play.com price:" + playResultMatcherThree.group(8)), url.toString(), 1);
			} else if (playProductPageMatcher.find()) {
				return prettyReply(mods.scrape.readyForIrc("<b>" + playProductPageMatcher.group(1) + "</b> " + playProductPageMatcher.group(4).replaceAll("<br/>", "<b> ") + "</b> Play.com price: " + playProductPageMatcher.group(3)), url.toString(), 1);
			} else {
				return "There was an error parsing the results. See " + url.toString();
			}
		} catch (final LookupException e) {
			return "Error looking up results.";
		} catch (final NullPointerException e) {
			return "Error looking up results.";
		} catch (final IllegalStateException e) {
			return "Error looking up results.";
		}
	}

	/* -- Start of code stolen from Dict.java -- */
		private String prettyReply(String text, final String url, final int lines) {
		final int maxlen=(IRCInterface.MAX_MESSAGE_LENGTH-15)*lines - url.length();
		text=text.replaceAll("\\s+"," ");
		if (text.length()>maxlen) {
			return text.substring(0, maxlen) + "..., see " + url;
		}
		return text + " See " + url;
	}

	private URL generateURL(final String base, final String url) throws LookupException {
		try	{
			return new URL(base + URLEncoder.encode(url, "UTF-8"));
		} catch (final UnsupportedEncodingException e) {
			throw new LookupException("Unexpected exception generating url.", e);
		} catch (final MalformedURLException e) {
			throw new LookupException("Error, malformed url generated.", e);
		}
	}

	private Matcher getMatcher(final URL url, final String pat) throws LookupException {
		try	{
			return mods.scrape.getMatcher(url, pat);
		} catch (final FileNotFoundException e) {
			throw new LookupException("No article found (404).", e);
		} catch (final IOException e)	{
			throw new LookupException("Error reading from site. " + e, e);
		}
	}
	/* -- End of stolen code -- */

	class LookupException extends ChoobException {

		private static final long serialVersionUID = -1;

		public LookupException(final String text)	{
			super(text);
		}

		public LookupException(final String text, final Throwable e) {
			super(text, e);
		}

		@Override
		public String toString() {
			return getMessage();
		}

	}

}
