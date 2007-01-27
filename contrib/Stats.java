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

public class Stats
{
	final int HISTORY = 1000;

	public String[] info()
	{
		return new String[] {
			"Plugin for analysing speech.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev: 485 $$Date: 2005-12-18 16:19:43 +0000 (Sun, 18 Dec 2005) $"
		};
	}

	private Modules mods;
	private IRCInterface irc;
	public Stats(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	// http://schmidt.devlib.org/java/word-count.html#source
	private static int countWords(String line)
	{
		int numWords = 0;
		int index = 0;
		boolean prevWhitespace = true;
		while (index < line.length())
		{
			char c = line.charAt(index++);
			boolean currWhitespace = Character.isWhitespace(c);
			if (prevWhitespace && !currWhitespace)
				numWords++;
			prevWhitespace = currWhitespace;
		}
		return numWords;
	}

	private String getText(Message mes) throws ChoobException
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
	}

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

	public void commandCaptuation( Message mes )
	{
		List<Message> history = mods.history.getLastMessages( mes, HISTORY );
		final String args = mods.util.getParamString(mes);
		final String target;
		if (!args.trim().equals(""))
			target = mods.nick.getBestPrimaryNick(args);
		else
			target = null;

		StringBuilder wb = new StringBuilder();
		int mlines = 0;
		int score = 0;
		String tex;
		for (Message m : history)
			if ((tex = m.getMessage()).length() > 4 &&
				(target == null || mods.nick.getBestPrimaryNick(m.getNick()).equalsIgnoreCase(target)) &&
				!Pattern.compile(irc.getTriggerRegex()).matcher(tex).find())
			{
				// remove smilies and trailing whitespace.
				tex = tex.replaceAll("\\s[:pP)/;\\\\o()^.¬ -]{2,4}$", "").replaceAll("\\s+$", "").replaceAll("^[a-zA-Z_`0-9|]+: +", "").replaceAll("\".*\"", "").replaceAll("http", "");

				if (tex.length() < 4)
					continue;

				// Small letter at start of line? PENALTY.
				if (m instanceof ChannelMessage && Pattern.compile("^\\p{Ll}").matcher(tex).find())
					score += 1;

				// Small letter at start of new sentance? PENALTY.
				Matcher ma = Pattern.compile("\\.\\s+\\p{Ll}").matcher(tex);
				while (ma.find())
					score += 1;

				// No thingie on the end? PENALTY.
				if (Pattern.compile("[^\\.\\?\\!]$").matcher(tex).find())
					score += 1;

				// Penalty non-words.
				for (String s : new String[] { "im", "Im", "id", "Id", "i", "i'll", "i'm", "i'd", "hes", "shes", "theyve", "theyre", "hasnt" })
				{
					ma = Pattern.compile("\\b" + s + "\\b").matcher(tex);
					while (ma.find())
						score+=2;
				}

				// Penalty starters, case insensitive.
				for (String s : new String[] { "or", "that", "again", "although", "but", "and", "also", "with", "this", "h?m+", "pf+t", "gr+", "there's", "theres", "there is", "there are", "therere", "there're" })
					if (Pattern.compile("^\\s*" + s + "\\b", Pattern.CASE_INSENSITIVE).matcher(tex).find()) score+=1;

				// Ditto, case sensitive.
				for (String s : new String[] { "Https?" })
					if (Pattern.compile("^\\s*" + s + "\\b").matcher(tex).find()) score+=10;

				// Enders.
				for (String s : new String[] { "/\\." })
					if (Pattern.compile(s + "$").matcher(tex).find()) score+=5;



				mlines++;
			}

		irc.sendContextReply(mes, (target == null ? "Channel average score is " : target + " scores ") + ((Math.round((((float)score)/2.0f/((float)mlines))*1000.0f))/10.0f) + "% for the" + (target == null ? "" : "ir") + " last " + mlines + " lines (zero is the perfect score).");
	}


/*
	public void commandText( Message mes ) throws ChoobException
	{
		final String workingText = getText(mes);
		irc.sendContextReply(mes, (String)mods.plugin.callAPI("Http", "StoreString", workingText));
	}
*/

}
