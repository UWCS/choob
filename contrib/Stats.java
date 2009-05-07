import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;

import java.util.*;
import java.util.regex.*;

import org.jibble.pircbot.Colors;

/**
 * Fun (live) stats for all the family.
 *
 * @author Faux
 */

public class EntityStat
{
	public int id;
	public String statName;
	public String entityName;
	//String chan; // ???
	public double value; // WMA; over 100 lines for people, 1000 lines for channels
}

public class Stats
{
	final int HISTORY = 1000;
	final double NICK_LENGTH = 100; // "Significant" lines in WMA calculations.
	final double CHAN_LENGTH = 1000;
	final double THRESHOLD = 0.005; // val * THRESHOLD is considered too small to be a part of WMA.
	final double NICK_ALPHA = Math.exp(Math.log(THRESHOLD) / (double)NICK_LENGTH);
	final double CHAN_ALPHA = Math.exp(Math.log(THRESHOLD) / (double)CHAN_LENGTH);

	public String[] info()
	{
		return new String[] {
			"Plugin for analysing speech.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date: 2008-08-18$"
		};
	}

	private Modules mods;
	private IRCInterface irc;
	public Stats(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

/*	private String getText(Message mes) throws ChoobException
	{
		List<Message> history = mods.history.getLastMessages( mes, HISTORY );
		final String target = mods.nick.getBestPrimaryNick(mods.util.getParamString(mes));

		StringBuilder wb = new StringBuilder();
		int mlines = 0;

		for (Message m : history)
			if (mods.nick.getBestPrimaryNick(m.getNick()).equalsIgnoreCase(target))
			{
				wb.append(". ").append(m.getMessage()).append(". ");
				mlines++;
			}

		if (mlines < 5)
			throw new ChoobException("Not enough lines for them!");


		return wb.toString().toLowerCase()
								.replaceAll("[a-z-]+[^a-z ]+[a-z-]+", ". ")	// Word with non-word in it (ie. url)
								.replaceAll("[^a-z ]+[a-z-]+", ". ")		// Word with illegal prefix.
								.replaceAll("[a-z]+[^a-z. ]+", "")			// Word with illegal suffix
								.replaceAll("[^a-z .-]", "")				// Remaining chars.
								.replaceAll(" *\\.+ +\\.", "")				// Nusiance 'sentances'.
								.replaceAll(" *\\.", ".")					// Spaces before ends.
								.replaceAll("  +", " ");					// Excess space.
	}

	public void commandFogg( Message mes )
	{

		// http://www.dantaylor.com/pages/fogg.html
		// Approximations ftw.

		final String workingText;
		try
		{
			workingText = getText(mes);
		}
		catch (ChoobException e)
		{
			irc.sendContextReply(mes, e.toString());
			return;
		}

		final int sentences = workingText.replaceAll("[^.]", "").length();
		final int words = countWords(workingText);

		final float A = (float)words / (float)sentences;
		final float B = countWords(workingText.replaceAll("\\b[a-z]{1,6}\\b", "")) / ((float)words/100.0f);

		final float div = 10;
		irc.sendContextReply(mes, "Based on " + mods.nick.getBestPrimaryNick(mods.util.getParamString(mes)) + "'s last few lines in here, their writing age is about (((" + words + "/" + sentences + ") + " + B + ") * 0.4 = " + (Math.round(((A+B) * 0.4) * div) / div) + ".");
	} */

/*
	public void commandSpammers( Message mes )
	{
		List<Message> history = mods.history.getLastMessages( mes, HISTORY );

		Map<String, Integer> scores = new HashMap<String, Integer>();
		for (Message m : history)
		{
			final String nick = mods.nick.getBestPrimaryNick(m.getNick());
			Integer i = scores.get(nick);
			if (i == null)
			{

				// frikkin' immutable integers
		}
	}
*/
	private void update( String thing, Message mes, double thisVal )
	{
		if ( mes instanceof ChannelEvent )
			updateObj( thing, mes.getContext(), thisVal, CHAN_ALPHA );
		updateObj( thing, mods.nick.getBestPrimaryNick( mes.getNick() ), thisVal, NICK_ALPHA );
	}

	private void updateObj ( String thing, String name, double thisVal, double alpha )
	{
		// I assume thing is safe. ^.^
		List<EntityStat> ret = mods.odb.retrieve( EntityStat.class, "WHERE entityName = \"" + mods.odb.escapeString(name) + "\" && statName = \"" + thing + "\"");
		EntityStat obj;
		if (ret.size() == 0) {
			obj = new EntityStat();
			obj.statName = thing;
			obj.entityName = name;
			obj.value = thisVal;
			mods.odb.save(obj);
		} else {
			obj = ret.get(0);
			obj.value = alpha * obj.value + (1 - alpha) * thisVal;
			mods.odb.update(obj);
		}
	}

	public void onMessage( Message mes )
	{
		if (Pattern.compile(irc.getTriggerRegex()).matcher(mes.getMessage()).find()) {
			// Ignore commands.
			return;
		}

		String content = mes.getMessage().replaceAll("^[a-zA-Z0-9`_|]+:\\s+", "");
		boolean referred = !content.equals(mes.getMessage());

		if (mes instanceof ActionEvent) {
			content = "*" + mes.getNick() + " " + content; // bizarrely, this is proper captuation grammar.
		}

		if (Pattern.compile("^<\\S+>").matcher(content).find()) {
			// Ignore quotes, too.
			return;
		}

		update( "captuation", mes, (double)apiCaptuation( content ) );
		int wc = apiWordCount( content );
		update( "wordcount", mes, (double)wc );
		update( "characters", mes, (double)apiLength( content ) );
		if (wc > 0)
			update( "wordlength", mes, apiWordLength( content ) );
		update( "referred", mes, referred ? 1.0 : 0.0 );
	}

	public String[] helpCommandGet = {
		"Get stat(s) about some person or channel.",
		"<Entity> [ <Stat> ]",
		"<Entity> is the name of the channel or person to get stats for",
		"<Stat> is the optional name of a specific statistic to get (omit it to get all of them)"
	};
	public void commandGet( Message mes )
	{
		String[] params = mods.util.getParamArray(mes);
		if (params.length == 3) {
			String nick = mods.nick.getBestPrimaryNick( params[1] );
			String thing = params[2].toLowerCase();
			List<EntityStat> ret = mods.odb.retrieve( EntityStat.class, "WHERE entityName = \"" + mods.odb.escapeString(nick) + "\" && statName = \"" + mods.odb.escapeString(thing) + "\"");
			EntityStat obj;
			if (ret.size() == 0) {
				irc.sendContextReply( mes, "Sorry, cannae find datta one." );
			} else {
				obj = ret.get(0);
				irc.sendContextReply( mes, "They be 'avin a score of " + (double)Math.round(obj.value * 100) / 100.0 + ".");
			}
		} else if (params.length == 2) {
			String nick = mods.nick.getBestPrimaryNick( params[1] );
			List<EntityStat> ret = mods.odb.retrieve( EntityStat.class, "WHERE entityName = \"" + mods.odb.escapeString(nick) + "\"");
			if (ret.size() == 0) {
				irc.sendContextReply( mes, "Sorry, cannae find datta one." );
			} else {
				StringBuilder results = new StringBuilder( "Stats:" );
				for (EntityStat obj: ret) {
					results.append( " " + obj.statName + " = " + (double)Math.round(obj.value * 100) / 100.0 + ";" );
				}
				irc.sendContextReply( mes, results.toString() );
			}
		} else {
			throw new ChoobBadSyntaxError();
		}
	}

	public int apiCaptuation( String str )
	{
		int score = 0;

		// remove smilies and trailing whitespace.
		str = str.replaceAll("(?:^|\\s+)[:pP)/;\\\\o()^.¬ -]{2,4}(\\s+|$)", "$1");

		// remove URLs
		str = str.replaceAll("[a-z0-9]+:/\\S+", "");

		// Nothing left?
		if (str.length() == 0)
			return 0;

		// No thingie on the end? PENALTY.
		// Must end with ., !, ? with or without optional terminating ) or ".
		if (!Pattern.compile("[\\.\\?\\!][\\)\"]?$").matcher(str).find())
			score += 1;

		// Now remove quoted stuff; it'll only give extra points where not needed.
		str = str.replaceAll("\".*?\"", "");

		// Small letter at start of new sentance/line? PENALTY.
		Matcher ma = Pattern.compile("(?:^|(?<!\\.)\\.\\s+)\\p{Ll}").matcher(str);
		while (ma.find())
			score += 1;

		// Penalty non-words.
		// Exceptions: id, Id, ill, cant, wont, hand,
		// Punish:
		//  Lowercase "I" in special cases.
		//  Missing apostrophes in special cases.
		//  Certain American spellings.
		//  Internetisms, like "zomg."
		//  Certain known abbreviations that aren't capitalised.
		//  Mixed case, like "tHis". "CoW" is fine, however.
		//  Leetspeak. Unfortunately, C0FF33 is still valid, as it's also hex.
		//   Also, words with trailing numbers are fine, since some nicknames
		//   etc. are like this.
		ma = Pattern.compile("(?:\\s|^)(?:[iI]m|i'm|i'?d|i'll|i|b|2b|u(?:2|r|t)?|cs:s|css|ui|zeus|dota|codd|backus|wtb|tsr|xml|ooo|lan|compsoc|cryfield|rootes|westwood|warwick|hurst|heronbank|earlsdon|cov(?:entry)?|leam(?:ington)?|claycroft|lakeside|zeeman|dcs|ramphal|(?!OOo)\\w*(.)\\1\\1\\w*|(?i:s?hes|they(?:ve|re|ll)|there(?:s|re|ll)|(?:has|was|sha|have|is|wo|ca)nt|(?:could|would|should)(?:ve|nt)|k?thz|pl[xz]|zomg|\\w+[xz]or|dee|tonite|sidewalk|captuation|moar|color|cud|yer|noes)|(?=[A-Z]*[a-z][A-Za-z]*\\b)(?i:bbq|l(?:ol)+|(?:rof)lmao|rofl|i?irc|afaik|hth|imh?o|fy|https?|ft[lwsp]|l4d|tf2)|[a-z]+[A-Z][a-zA-Z]*|(?!(?:[il]1[08]n))(?:\\w*[g-zG-Z]\\w*[0-9]\\w*[a-zA-Z]|\\w*[0-9]\\w*[g-zG-Z]\\w*[a-zA-Z])|american|british|english|european|gud|gra(?:m?me|ma)r)\\b").matcher(str);
		while (ma.find())
			score += 1;

		return score;
	}

	// http://schmidt.devlib.org/java/word-count.html#source
	public int apiWordCount(String str)
	{
		int numWords = 0;
		int index = 0;
		boolean prevWhitespace = true;
		while (index < str.length())
		{
			char c = str.charAt(index++);
			boolean currWhitespace = Character.isWhitespace(c);
			if (prevWhitespace && !currWhitespace)
				numWords++;
			prevWhitespace = currWhitespace;
		}
		return numWords;
	}

	public int apiLength(String str)
	{
		return str.replaceAll("\\s+", "").length();
	}

	public double apiWordLength(String str)
	{
		return (double)apiLength(str) / (double)apiWordCount(str);
	}

	public void commandCheck( Message mes ) {
		String content = mods.util.getParamString(mes);
		double captuation = apiCaptuation( content );
		int wc = apiWordCount( content );
		double leng = apiLength( content );
		double wordl = apiWordLength( content );
		boolean referred = !content.equals(mes.getMessage());

		irc.sendContextReply(mes, "Your message has: captuation = " + captuation + "; word count = " + wc + "; length = " + leng + "; word length = " + wordl + "; referred = " + referred + ";");
	}


/*
	public void commandText( Message mes ) throws ChoobException
	{
		final String workingText = getText(mes);
		irc.sendContextReply(mes, (String)mods.plugin.callAPI("Http", "StoreString", workingText));
	}
*/

}
