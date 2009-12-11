import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jibble.pircbot.Colors;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.PrivateEvent;


class KarmaObject
{
	public int id;
	public String string;
	public int up;
	public int down;
	public int value;
	String instName;

	public boolean equals(final KarmaObject obj)
	{
		return this.string.equalsIgnoreCase(obj.string);
	}
}

class KarmaReasonObject
{
	public int id;
	public String string;
	public int direction;
	public String reason;
}

class KarmaChangeHolder
{
	KarmaObject karma;
	List<KarmaReasonObject> reasons;
	int change;
	boolean flood;
	String instanceName;

	KarmaChangeHolder(final String instanceName)
	{
		this.reasons = new ArrayList<KarmaReasonObject>();
		this.instanceName = instanceName;
	}
}

class KarmaReasonEnumerator
{
	public KarmaReasonEnumerator()
	{
		// Unhiding.
	}

	public KarmaReasonEnumerator(final String enumSource, final int[] idList)
	{
		this.enumSource = enumSource;
		this.idList = "";
		for (int i = 0; i < idList.length; i++) {
			if (i > 0)
				this.idList += ",";
			this.idList += idList[i];
		}
		this.index = (int)Math.floor(Math.random() * idList.length);
		this.lastUsed = System.currentTimeMillis();
	}

	public int getNext()
	{
		if (intIdList == null)
			setupIDListInt();

		index++;
		if (index >= intIdList.length) {
			index = 0;
		}
		lastUsed = System.currentTimeMillis();
		return intIdList[index];
	}

	private void setupIDListInt()
	{
		final String[] list = this.idList.split("\\s*,\\s*");
		this.intIdList = new int[list.length];
		for (int i = 0; i < list.length; i++) {
			this.intIdList[i] = Integer.parseInt(list[i]);
		}
	}

	public int getSize()
	{
		if (intIdList == null)
			setupIDListInt();
		return intIdList.length;
	}

	public int id;
	public String enumSource;
	public String idList;
	private int[] intIdList = null;
	public int index;
	public long lastUsed;
}

class KarmaSortByAbsValue implements Comparator<KarmaObject>
{
	public int compare(final KarmaObject o1, final KarmaObject o2) {
		if (Math.abs(o1.value) > Math.abs(o2.value)) {
			return -1;
		}
		if (Math.abs(o1.value) < Math.abs(o2.value)) {
			return 1;
		}
		if (o1.up > o2.up) {
			return -1;
		}
		if (o1.up < o2.up) {
			return 1;
		}
		if (o1.down > o2.down) {
			return -1;
		}
		if (o1.down < o2.down) {
			return 1;
		}
		return 0;
	}

	@Override
	public boolean equals(final Object obj) {
		return false;
	}
}

class KarmaSortByValue implements Comparator<KarmaObject>
{
	public int compare(final KarmaObject o1, final KarmaObject o2) {
		if (o1.value > o2.value) {
			return -1;
		}
		if (o1.value < o2.value) {
			return 1;
		}
		return 0;
	}

	@Override
	public boolean equals(final Object obj) {
		return false;
	}
}

public class Karma
{
	// Non-null == ignore.
	private final static Set<String> exceptions = new HashSet<String>();
	private final static Set<String> reasonExceptions = new HashSet<String>();
	static
	{
		exceptions.add("c");
		exceptions.add("dc");
		exceptions.add("visualj");
		exceptions.add("vc");
		exceptions.add("tolua");
		reasonExceptions.add("for that");
		reasonExceptions.add("because of that");
	}
	static final int    FLOOD_RATE     = 15 * 60 * 1000;
	static final String FLOOD_RATE_STR = "15 minutes";
	private static long ENUM_TIMEOUT = 5 * 60 * 1000; // 5 minutes

	public String[] info()
	{
		return new String[] {
			"Plugin to keep track of the \"karma\" of stuff.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	final Modules mods;
	private final IRCInterface irc;
	public Karma (final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
		mods.interval.callBack("clean-enums", 60000, 1);
	}

	// Interval
	public void interval(final Object param)
	{
		if ("clean-enums".equals(param)) {
			// Clean up dead enumerators.
			final long lastUsedCutoff = System.currentTimeMillis() - ENUM_TIMEOUT;
			final List<KarmaReasonEnumerator> deadEnums = mods.odb.retrieve(KarmaReasonEnumerator.class, "WHERE lastUsed < " + lastUsedCutoff);
			for (int i = 0; i < deadEnums.size(); i++) {
				mods.odb.delete(deadEnums.get(i));
			}
			mods.interval.callBack(param, 60000, 1);
		}
	}

	private KarmaReasonObject pickRandomKarmaReason(final List<KarmaReasonObject> reasons, String enumSource)
	{
		if (enumSource == null) {
			final int index = (int)Math.floor(Math.random() * reasons.size());
			return reasons.get(index);
		}

		int reasonId = -1;
		enumSource = enumSource.toLowerCase();
		final List<KarmaReasonEnumerator> enums = mods.odb.retrieve(KarmaReasonEnumerator.class, "WHERE enumSource = '" + mods.odb.escapeString(enumSource) + "'");
		KarmaReasonEnumerator krEnum = null;
		if (enums.size() >= 1) {
			krEnum = enums.get(0);
			if (krEnum.getSize() != reasons.size()) {
				// Count has changed: invalidated!
				mods.odb.delete(krEnum);
				krEnum = null;
			} else {
				// Alright, step to the next one.
				reasonId = krEnum.getNext();
				mods.odb.update(krEnum);
			}
		}
		if (krEnum == null) {
			// No enumerator, create one.
			final int[] idList = new int[reasons.size()];
			for (int i = 0; i < reasons.size(); i++)
				idList[i] = reasons.get(i).id;

			krEnum = new KarmaReasonEnumerator(enumSource, idList);
			reasonId = krEnum.getNext();
			mods.odb.save(krEnum);
		}

		KarmaReasonObject rvReason = null;
		for (int i = 0; i < reasons.size(); i++) {
			final KarmaReasonObject reason = reasons.get(i);
			if (reason.id == reasonId) {
				rvReason = reason;
				break;
			}
		}
		return rvReason;
	}

	private String[] getReasonResult(final List<KarmaReasonObject> reasons, final String enumSource)
	{
		if (reasons.size() == 0) {
			return null;
		}
		final KarmaReasonObject reason = pickRandomKarmaReason(reasons, enumSource);
		return new String[] {
			reason.string,
			reason.reason,
			reason.direction == 1 ? "gained" : (reason.direction == -1 ? "lost" : "unchanged")
		};
	}

	public String[] apiReason()
	{
		System.err.println("WARNING: Karma.apiReason called with no enumSource. No enumeration supported with this call.");
		return apiReasonEnum(null);
	}

	public String[] apiReason(final int direction)
	{
		System.err.println("WARNING: Karma.apiReason called with no enumSource. No enumeration supported with this call.");
		return apiReasonEnum(null, direction);
	}

	public String[] apiReason(final String name)
	{
		System.err.println("WARNING: Karma.apiReason called with no enumSource. No enumeration supported with this call.");
		return apiReasonEnum(null, name);
	}

	public String[] apiReason(final String name, final int direction)
	{
		System.err.println("WARNING: Karma.apiReason called with no enumSource. No enumeration supported with this call.");
		return apiReasonEnum(null, name, direction);
	}

	public String[] apiReasonEnum(final String enumSource)
	{
		final List<KarmaReasonObject> results = mods.odb.retrieve(KarmaReasonObject.class, "");
		return getReasonResult(results, enumSource);
	}

	public String[] apiReasonEnum(final String enumSource, final int direction)
	{
		final List<KarmaReasonObject> results = mods.odb.retrieve(KarmaReasonObject.class, "WHERE direction = " + direction);
		return getReasonResult(results, enumSource + ":" + (direction == 1 ? "up" : (direction == -1 ? "down" : "unchanged")));
	}

	public String[] apiReasonEnum(final String enumSource, final String name)
	{
		final List<KarmaReasonObject> results = mods.odb.retrieve(KarmaReasonObject.class, "WHERE string = \"" + mods.odb.escapeString(name) + "\"");
		return getReasonResult(results, enumSource + "::" + name);
	}

	public String[] apiReasonEnum(final String enumSource, final String name, final int direction)
	{
		final List<KarmaReasonObject> results = mods.odb.retrieve(KarmaReasonObject.class, "WHERE string = \"" + mods.odb.escapeString(name) + "\" AND direction = " + direction);
		return getReasonResult(results, enumSource + ":" + (direction == 1 ? "up" : (direction == -1 ? "down" : "unchanged")) + ":" + name);
	}

	public String[] helpCommandReason = {
		"Find out why something rocks or sucks.",
		"[<Object>]",
		"<Object> is the optional thing to ask about."
	};
	public void commandReason( final Message mes )
	{
		String name = mods.util.getParamString(mes);

		String[] reason;
		if (name.equals(""))
		{
			reason = apiReasonEnum(mes.getContext());
			if (reason != null)
				name = reason[0];
		}
		else
		{
			Matcher ma = karmaItemPattern.matcher(name);
			if (ma.find())
			{
				reason = apiReasonEnum(mes.getContext(), getName(ma));
				if (reason != null)
					name = reason[0];
			}
			else
			{
				if (name.startsWith("\""))
				{
					irc.sendContextReply(mes, "Unable to match your query to a valid karma string.");
					return;
				}

				// Value wasn't quoted, so try it again, but quoted.
				ma = karmaItemPattern.matcher("\"" + name + "\"");
				if (!ma.find())
				{
					irc.sendContextReply(mes, "Unable to match your query to a valid karma string.");
					return;
				}
				reason = apiReasonEnum(mes.getContext(), getName(ma));
				if (reason != null)
					name = reason[0];
			}
		}

		if (reason != null)
			irc.sendContextReply(mes, formatKarmaNameForIRC(name) + " has " + reason[2] + " karma " + reason[1]);
		else
			irc.sendContextReply(mes, "Nobody has ever told me why " + formatKarmaNameForIRC(name) + " has changed karma. :(");
	}

	private void nullReason(final Message mes, final int direction)
	{
		String[] reason;
		String name;
		reason = apiReasonEnum(mes.getContext(), direction);
		if (reason != null)
			name = reason[0];
		else
		{
			irc.sendContextReply(mes, "No karma reasons.");
			return;
		}

		irc.sendContextReply(mes, formatKarmaNameForIRC(name) + " has " + (direction == 1 ? "gained" : (direction == -1 ? "lost" : "unchanged")) + " karma " + reason[1]);
	}

	public String[] helpCommandReasonUp = {
		"Find out why something rocks.",
		"[<Object>]",
		"<Object> is the optional thing to ask about."
	};
	public void commandReasonUp( final Message mes )
	{
		String name = mods.util.getParamString(mes);

		String[] reason;
		if (name.equals(""))
		{
			reason = apiReasonEnum(mes.getContext(), 1);
			if (reason != null)
				name = reason[0];
		}
		else
		{
			final Matcher ma=karmaItemPattern.matcher(name);
			if (ma.find())
			{
				reason = apiReasonEnum(mes.getContext(), getName(ma), 1);
				if (reason !=null)
					name = reason[0];
			}
			else
			{
				nullReason(mes, 1);
				return;
			}
		}

		if (reason != null)
			irc.sendContextReply(mes, formatKarmaNameForIRC(name) + " has gained karma " + reason[1]);
		else
			irc.sendContextReply(mes, "Nobody has ever told me why " + formatKarmaNameForIRC(name) + " has gained karma. :(");
	}

	public String[] helpCommandReasonDown = {
		"Find out why something sucks.",
		"[<Object>]",
		"<Object> is the optional thing to ask about."
	};
	public void commandReasonDown( final Message mes )
	{
		String name = mods.util.getParamString(mes);

		String[] reason;
		if (name.equals(""))
		{
			reason = apiReasonEnum(mes.getContext(), -1);
			if (reason != null)
				name = reason[0];
		}
		else
		{
			final Matcher ma=karmaItemPattern.matcher(name);
			if (ma.find())
			{
				reason = apiReasonEnum(mes.getContext(), getName(ma), -1);
				if (reason != null)
					name = reason[0];
			}
			else
			{
				nullReason(mes, -1);
				return;
			}
		}

		if (reason != null)
			irc.sendContextReply(mes, formatKarmaNameForIRC(name) + " has lost karma " + reason[1]);
		else
			irc.sendContextReply(mes, "Nobody has ever told me why " + formatKarmaNameForIRC(name) + " has lost karma. :(");
	}

	public String[] helpTopics = { "Using" };
	public String[] helpUsing = {
		  "To change the karma of something, simply send the message"
		+ " 'something--' or 'something++'. You can also specify a reason, by"
		+ " sending a line starting with a karma change then giving a reason,"
		+ " like: 'ntl-- because they suck' or 'ntl-- for sucking teh c0k'.",
		  "For strings shorter than 3 chars, or spaced strings or whatever,"
		+ " you can use quotes, like this: '\"a\"--' or '\"big bananas\"++'."
		+ " You can't yet Set these types of string."
	};

	// Let's just do a simple filter to pick up karma. This is lightweight and
	// picks up anything that /could/ be karma (though not necessarily only
	// karma)

	// ++, --, +- or -+:
	final private static String plusplus_or_minusminus = "([\\+\\-]{2})";

	// Quoted string:
	final private static String c_style_quoted_string = "(?:"
			+ "\""
			+ "("
				+ "(?:\\\\.|[^\"\\\\])+" // C-style quoting
			+ ")"
			+ "\""
			+ ")";

	// Plain string of >=2 chars.
	final private static String plain_karma = "("
				+ "[a-zA-Z0-9_]{2,}"
			+ ")";

	// Either a quoted or a valid plain karmaitem.
	final private static String karma_item = "(?x:" + c_style_quoted_string + "|" + plain_karma + ")";

	final private static Pattern karmaItemPattern = Pattern.compile(karma_item);

	// If you change this, change reasonPattern too.
	final private static Pattern karmaPattern = Pattern.compile(
		  "(?x:"
		+ "(?: ^ | (?<=[\\s\\(]) )" // Anchor at start of string, whitespace or open bracket.
		+ karma_item
		+ plusplus_or_minusminus
		+ "[\\)\\.,]?" // Allowed to terminate with full stop/close bracket etc.
		+ "(?: (?=\\s) | $ )" // Need either whitespace or end-of-string now.
		+ ")"
	);

	// If you change the first part of this, change karmaPattern too.
	final private static Pattern karmaPatternWithReason = Pattern.compile(
		"(?x:"
		+ "^" // Anchor at start of string.
		+ karma_item
		+ plusplus_or_minusminus
		+ "\\s+"
		+ "("
			// A "natural English" reason
			+ "(?i: because | for)\\s" // Must start sensibly
			+ ".+"                     // Rest of reason
		+ "|"
			// A bracketted reason
			+ "\\("
			+ ".+?" // Contains any text.
			+ "\\)"
		+ ")"
		+ ".*?" // Keep this trailing part as small as possible.
		+ "$" // Anchor at the end of string.
		+ ")"
	);

	public final String filterKarmaRegex = plusplus_or_minusminus + "\\B";

	public synchronized void filterKarma(final Message mes)
	{
		// Ignore lines that look like commands.
		if (mes.getFlags().containsKey("command"))
			return;

		final String message = mes.getMessage();
		final String nick = mods.nick.getBestPrimaryNick(mes.getNick());

		//System.err.println("LINE       : <" + message + ">");
		final List<Integer> matchStarts = new ArrayList<Integer>();
		final Matcher karmaScan = karmaPattern.matcher(message);
		while (karmaScan.find())
			matchStarts.add(karmaScan.start());
		matchStarts.add(message.length());

		final List<List<String>> matches = new ArrayList<List<String>>();
		for (int matchIndex = 1; matchIndex < matchStarts.size(); matchIndex++)
		{
			//System.err.println("");
			final String submessage = message.substring(matchStarts.get(matchIndex - 1), matchStarts.get(matchIndex));
			//System.err.println("  SEGMENT  : <" + submessage + ">");

			Matcher karmaMatch = karmaPatternWithReason.matcher(submessage);
			if (!karmaMatch.find())
			{
				karmaMatch = karmaPattern.matcher(submessage);
				karmaMatch.find();
			}

			//System.err.println("  MATCH    : <" + karmaMatch.group() + ">");
			//for (int i = 1; i <= karmaMatch.groupCount(); i++)
			//	System.err.println("    GROUP " + i + ": <" + karmaMatch.group(i) + ">");

			List<String> groups = new ArrayList<String>();
			for (int i = 0; i <= karmaMatch.groupCount(); i++)
				groups.add(karmaMatch.group(i));
			matches.add(groups);
		}

		// List of all karma changes that will be applied and hash of them
		// so we can handle duplicates sanely.
		final List<KarmaChangeHolder> karmas = new ArrayList<KarmaChangeHolder>();
		final Map<String,KarmaChangeHolder> karmaMap = new HashMap<String,KarmaChangeHolder>();
		for (final List<String> match : matches)
		{
			// Group 1: quoted karma item
			// Group 2: raw karma item
			// Group 3: "++" or "--"
			// Group 4: reason (optional)
			String name = "";
			if (match.get(1) != null)
			{
				name = match.get(1).replaceAll("\\\\(.)", "$1");
			}
			else
			{
				name = match.get(2);
				if (exceptions.contains(name.toLowerCase()))
					continue;
			}
			KarmaChangeHolder karma;
			if (karmaMap.containsKey(name.toLowerCase()))
				karma = karmaMap.get(name.toLowerCase());
			else
				karma = new KarmaChangeHolder(name);

			// If it's "me", replace with user's nickname and force to down.
			if (karma.instanceName.equals/*NOT IgnoreCase*/("me") || karma.instanceName.equals/*NOT IgnoreCase*/("Me") || karma.instanceName.equalsIgnoreCase(nick))
			{
				karma.instanceName = nick;
				karma.change--;
			}
			// Up or down?
			else if (match.get(3).equals("++"))
			{
				karma.change++;
			}
			else if (match.get(3).equals("--"))
			{
				karma.change--;
			}

			// Get the reason, if it's not excluded!
			if (match.size() > 4 && !reasonExceptions.contains(match.get(4)))
			{
				KarmaReasonObject reason = new KarmaReasonObject();
				reason.reason = match.get(4);
				reason.direction = match.get(3).equals("++") ? 1 : (match.get(3).equals("--") ? -1 : 0);
				karma.reasons.add(reason);
			}

			if (!karmaMap.containsKey(name.toLowerCase()))
			{
				karmas.add(karma);
				karmaMap.put(karma.instanceName.toLowerCase(), karma);
			}
		}

		// No karma changes? Boring!
		if (karmas.size() == 0)
			return;

		// Wait until we know there's a real karma change going on.
		if (mes instanceof PrivateEvent)
		{
			irc.sendContextReply(mes, "I'm sure other people want to hear what you have to think!");
			return;
		}

		// Right. We have a list of karma to apply, we've checked it's a public
		// place and we have no duplicates. Time for the flood checking.
		for (final KarmaChangeHolder karma : karmas)
		{
			try
			{
				// 15 minute block for each karma item, irespective of who or direction.
				final int ret = (Integer)mods.plugin.callAPI("Flood", "IsFlooding", "Karma:" + normalise(karma.instanceName), FLOOD_RATE, 2);
				if (ret != 0)
				{
					karma.flood = true;
					continue;
				}
			}
			catch (final ChoobNoSuchCallException e)
			{ } // ignore
			catch (final Throwable e)
			{
				System.err.println("Couldn't do antiflood call: " + e);
			}
		}

		// Apply all the karma changes!
		for (final KarmaChangeHolder karma : karmas)
		{
			// Flooded? Skip it!
			if (karma.flood)
				continue;

			// Fetch the existing karma data for this item.
			karma.karma = retrieveKarmaObject(karma.instanceName);

			// Do up or down change first.
			if (karma.change > 0)
			{
				karma.karma.up++;
				karma.karma.value++;
			}
			else if (karma.change < 0)
			{
				karma.karma.down++;
				karma.karma.value--;
			}

			// Save the new karma data.
			mods.odb.update(karma.karma);

			// Now add the reason(s). Note that there's nothing to retrieve
			// here so we can save the local object directly.
			for (KarmaReasonObject reason : karma.reasons)
			{
				reason.string = karma.karma.string;
				mods.odb.save(reason);
			}
		}

		// Generate a pretty reply, all actual processing is done now:
		if (karmas.size() == 1)
		{
			final KarmaChangeHolder karma = karmas.get(0);

			if (karma.flood)
				irc.sendContextReply(mes, "Denied change to " + formatKarmaNameForIRC(karma.instanceName) + "! Karma changes limited to one change per item per " + FLOOD_RATE_STR + ".");
			else if (karma.karma.string.equals(nick))
				// This doesn't mention if there was a reason.
				irc.sendContextReply(mes, "Fool, that's less karma to you! That leaves you with " + karma.karma.value + ".");
			else
				irc.sendContextReply(mes, (karma.change > 0 ? "Given more karma" : karma.change < 0 ? "Given less karma" : "No change") + " to " + formatKarmaNameForIRC(karma.instanceName) + (karma.reasons.size() == 1 ? " and understood your reason" : (karma.reasons.size() > 1 ? " and understood your reasons" : "")) + ". " + (karma.change == 0 ? "Karma remains at " : "New karma is ") + karma.karma.value + ".");
		}
		else
		{
			final StringBuffer output = new StringBuffer("Karma adjustments: ");
			for (int i = 0; i < karmas.size(); i++)
			{
				final KarmaChangeHolder karma = karmas.get(i);
				output.append(formatKarmaNameForIRC(karma.instanceName));
				if (karma.flood)
				{
					output.append(" ignored (flood)");
				}
				else
				{
					if (karma.change > 0)
						output.append(" up");
					else if (karma.change < 0)
						output.append(" down");
					else
						output.append(" unchanged");
					if (karma.reasons.size() > 1)
						output.append(" with reasons");
					else if (karma.reasons.size() == 1)
						output.append(" with reason");
					if (karma.change == 0)
						output.append(" (remains " + karma.karma.value + ")");
					else
						output.append(" (now " + karma.karma.value + ")");
				}
				if (i < karmas.size() - 1)
					output.append(", ");
				else if (i == karmas.size() - 2)
					output.append(" and ");
			}
			output.append(".");
			irc.sendContextReply(mes, output.toString());
		}
	}

	private String postfix(final int n)
	{
		if (n % 100 >= 11 && n % 100 <= 13)
			return "th";

		switch (n % 10)
		{
			case 1: return "st";
			case 2: return "nd";
			case 3: return "rd";
		}
		return "th";
	}

	private void commandScores(final Message mes, final boolean asc)
	{
		final List<KarmaObject> karmaObjs = retrieveKarmaObjects("SORT " + (asc ? "ASC" : "DESC") + " INTEGER value LIMIT (5)");

		final StringBuffer output = new StringBuffer((asc ? "Low" : "High" ) + " Scores: ");

		for (int i=0; i<karmaObjs.size(); i++)
		{
			output.append(String.valueOf(i+1) + postfix(i+1));
			output.append(": ");
			output.append(formatKarmaNameForIRC(karmaObjs.get(i).string));
			output.append(" (with " + karmaObjs.get(i).value + ")");
			if (i != karmaObjs.size() - 1)
			{
				if (i == karmaObjs.size() - 2)
					output.append(" and ");
				else
					output.append(", ");
			}
		}
		output.append(".");
		irc.sendContextReply( mes, output.toString());
	}

	public String[] helpCommandHighScores = {
		"Find out what has the highest karma"
	};
	public void commandHighScores(final Message mes)
	{
		commandScores(mes, false);
	}

	public String[] helpCommandLowScores = {
		"Find out what has the lowest karma"
	};
	public void commandLowScores(final Message mes)
	{
		commandScores(mes, true);
	}

	private void saveKarmaObjects(final List<KarmaObject> karmaObjs)
	{
		for (final KarmaObject karmaObj: karmaObjs)
			mods.odb.update(karmaObj);
	}

	private KarmaObject retrieveKarmaObject(String name)
	{
		name = normalise(name);
		final List<KarmaObject> results = mods.odb.retrieve(KarmaObject.class, "WHERE string = \"" + mods.odb.escapeString(name) + "\"");
		if (results.size() == 0)
		{
			final KarmaObject newObj = new KarmaObject();
			newObj.string = name;
			mods.odb.save(newObj);
			return newObj;
		}
		return results.get(0);
	}

	private List<KarmaObject> retrieveKarmaObjects(final String clause)
	{
		return mods.odb.retrieve(KarmaObject.class, clause);
	}

	public String[] helpCommandFight = {
		"Pit the karma of two objects against each other to find the leetest (or least lame).",
		"<Object 1> <Object 2>",
	};

	public void commandFight (final Message mes)
	{
		final List<String> params = new ArrayList<String>();

		final Matcher ma=karmaItemPattern.matcher(mods.util.getParamString(mes));
		while (ma.find())
			params.add(getName(ma));

		final List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();
		//Nab the params
		if (params.size() == 2)
		{
			for (int i=0; i<2; i++)
			{
				final String name = params.get(i);
				if (name!=null)
				{
					final KarmaObject karmaObj = retrieveKarmaObject(name);
					karmaObj.instName = name;
					karmaObjs.add(karmaObj);
				}
			}
		}
		if (karmaObjs.size() == 2)
		{
			//Check that they aint the same thing!
			if (karmaObjs.get(0).equals(karmaObjs.get(1)))
			{
				irc.sendContextReply(mes, "Fighters must be unique!");
			}
			else
			{
				final int result = new KarmaSortByValue().compare(karmaObjs.get(0),karmaObjs.get(1));
				if (result == -1)
				{
					//Winner is Object 0
					irc.sendContextReply(mes, formatKarmaNameForIRC(karmaObjs.get(0).instName) + " was victorious over " + formatKarmaNameForIRC(karmaObjs.get(1).instName) + "! (" + karmaObjs.get(0).value + " vs " + karmaObjs.get(1).value + ")");
				}
				else if (result == 1)
				{
					//Winner is Object 1
					irc.sendContextReply(mes, formatKarmaNameForIRC(karmaObjs.get(1).instName) + " was victorious over " + formatKarmaNameForIRC(karmaObjs.get(0).instName) + "! (" + karmaObjs.get(1).value + " vs " + karmaObjs.get(0).value + ")");
				}
				else
				{
					//Should only be a draw
					irc.sendContextReply(mes, "The battle between " + formatKarmaNameForIRC(karmaObjs.get(0).instName) + " and " + formatKarmaNameForIRC(karmaObjs.get(1).instName) + " was a draw! (" + karmaObjs.get(0).value + " vs " + karmaObjs.get(1).value + ")");
				}

			}

		}
		else
		{
			//Too many, or perhaps too few, things
			irc.sendContextReply(mes, "You must supply exactly two objects to fight!");
		}

	}

	public String[] helpCommandReal = {
		"Find out the \"real\" karma of some object or another.",
		"<Object> [<Object> ...]",
		"<Object> is the name of something to get the karma of."
	};
	public void commandReal (final Message mes)
	{
		final List<String> params = new ArrayList<String>();
		final Matcher ma=karmaItemPattern.matcher(mods.util.getParamString(mes));

		while (ma.find())
			params.add(getName(ma));

		final List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();

		if (params.size() > 0)
			for (int i=0;i<params.size();i++)
			{
				final String name = params.get(i);
				if (name != null) {
					final KarmaObject karmaObj = retrieveKarmaObject(name);
					karmaObj.instName = name;
					karmaObjs.add(karmaObj);
				}
			}

		if (karmaObjs.size() == 1)
		{
			final int realkarma = karmaObjs.get(0).up - karmaObjs.get(0).down;
			irc.sendContextReply(mes, formatKarmaNameForIRC(karmaObjs.get(0).instName) + " has a \"real\" karma of " + realkarma + " (" + karmaObjs.get(0).up + " up, " + karmaObjs.get(0).down + " down).");
			return;
		}

		final StringBuffer output = new StringBuffer("\"Real\" Karmas: ");

		if (karmaObjs.size()!=0)
		{
			for (int i=0;i<karmaObjs.size();i++)
			{
				final int realkarma = karmaObjs.get(i).up - karmaObjs.get(i).down;
				output.append(formatKarmaNameForIRC(karmaObjs.get(i).instName) + ": " + realkarma);
				if (i != karmaObjs.size() -1)
				{
					if (i == karmaObjs.size() -2)
						output.append(" and ");
					else
						output.append(", ");
				}
			}
			output.append(".");
			irc.sendContextReply(mes, output.toString());
		} else {
			irc.sendContextReply(mes, "Check the \"real\" karma of what?");
		}
	}

	public String[] helpCommandGet = {
		"Find out the karma of some object or other.",
		"<Object> [<Object> ...]",
		"<Object> is the name of something to get the karma of"
	};

	public String commandGet (final String mes)
	{
		final List<String> params = new ArrayList<String>();

		final Matcher ma = karmaItemPattern.matcher(mes);

		while (ma.find())
			params.add(getName(ma));

		final List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();
		if (params.size() > 0)
			for (int i=0; i<params.size(); i++)
			{
				final String name = params.get(i);
				if (name!=null)
				{
					final KarmaObject karmaObj = retrieveKarmaObject(name);
					karmaObj.instName = name;
					karmaObjs.add(karmaObj);
				}
			}

		if (karmaObjs.size() == 1)
			return formatKarmaNameForIRC(karmaObjs.get(0).instName) + " has a karma of " + karmaObjs.get(0).value + " (" + karmaObjs.get(0).up + " up, " + karmaObjs.get(0).down + " down).";

		final StringBuffer output = new StringBuffer("Karmas: ");

		if (karmaObjs.size()!=0)
		{
			for (int i=0; i<karmaObjs.size(); i++)
			{
				output.append(formatKarmaNameForIRC(karmaObjs.get(i).instName));
				output.append(": " + karmaObjs.get(i).value);
				if (i != karmaObjs.size() - 1)
				{
					if (i == karmaObjs.size() - 2)
						output.append(" and ");
					else
						output.append(", ");
				}
			}
			output.append(".");
			return output.toString();
		}
		return "Check the karma of what?";
	}

	public String[] helpCommandSet = {
		"Set out the karma of some object or other.",
		"<Object>=<Value> [<Object>=<Value> ...]",
		"<Object> is the name of something to set the karma of",
		"<Value> is the value to set the karma to"
	};
	public synchronized void commandSet( final Message mes )
	{
		mods.security.checkNickPerm(new ChoobPermission("plugins.karma.set"), mes);

		final List<String> params = mods.util.getParams( mes );
		final List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();
		for (int i=1; i<params.size(); i++)
		{
			final String param = params.get(i);
			final String[] items = param.split("=");
			if (items.length != 2)
			{
				irc.sendContextReply(mes, "Bad syntax: Use <Object>=<Value> [<Object>=<Value> ...]");
				return;
			}

			final String name = items[0].trim();
			int val;
			try
			{
				val = Integer.parseInt(items[1]);
			}
			catch (final NumberFormatException e)
			{
				irc.sendContextReply(mes, "Bad syntax: Karma value " + items[1] + " is not a valid integer.");
				return;
			}

			final KarmaObject karmaObj = retrieveKarmaObject(name);

			karmaObj.instName = name;
			karmaObj.value = val;
			karmaObjs.add(karmaObj);
		}

		if (karmaObjs.size()==0)
		{
			irc.sendContextReply(mes, "You need to specify some bobdamn karma to set!");
			return;
		}

		saveKarmaObjects(karmaObjs);

		final StringBuffer output = new StringBuffer("Karma adjustment");
		output.append(karmaObjs.size() == 1 ? "" : "s");
		output.append(": ");
		for (int i=0; i<karmaObjs.size(); i++)
		{
			output.append(formatKarmaNameForIRC(karmaObjs.get(i).instName));
			output.append(": now ");
			output.append(karmaObjs.get(i).value);
			if (i != karmaObjs.size() - 1)
			{
				if (i == karmaObjs.size() - 2)
					output.append(" and ");
				else
					output.append(", ");
			}
		}
		output.append(".");
		irc.sendContextReply( mes, output.toString());

	}

	final private static String slashed_regex_string = "(?:"
			+ "/"
			+ "("
				+ "(?:\\\\.|[^/\\\\])+" // C-style quoting
			+ ")"
			+ "/"
			+ ")";

	class KarmaSearchItem
	{
		KarmaSearchItem(final String name, final boolean regex)
		{
			this.name = name;
			this.regex = regex;
		}

		String name;
		boolean regex;
	}

	public String[] helpCommandSearch = {
		"Finds existing karma items.",
		"<Query> [<Query> ...]",
		"<Query> is some text or a regular expression (in /.../) to find"
	};
	public void commandSearch(final Message mes)
	{
		final List<KarmaSearchItem> params = new ArrayList<KarmaSearchItem>();

		final Matcher ma = Pattern.compile(karma_item + "|" + slashed_regex_string).matcher(mods.util.getParamString(mes));

		while (ma.find())
		{
			final String name = getName(ma);
			params.add(
				name == null ?
				new KarmaSearchItem(normalise(ma.group(3)), true) :
				new KarmaSearchItem(normalise(name), false)
			);
		}

		if (params.size() == 0)
			irc.sendContextReply(mes, "Please specify at least one valid karma item, or a regex.");

		// Only 3 items please!
		while (params.size() > 3)
			params.remove(params.size() - 1);

		for (final KarmaSearchItem item : params)
		{
			String odbQuery;

			final String andNotZero =  " AND NOT (up = 0 AND down = 0 AND value = 0)";

			if (item.regex) {
				// Regexp
				odbQuery = "WHERE string RLIKE \"" + mods.odb.escapeForRLike(item.name) + "\"" + andNotZero;
			} else {
				// Substring
				odbQuery = "WHERE string LIKE \"%" + mods.odb.escapeForLike(item.name) + "%\"" + andNotZero;
			}
			System.out.println("    Query: " + odbQuery);

			final List<KarmaObject> odbItems = mods.odb.retrieve(KarmaObject.class, odbQuery);

			if (odbItems.size() == 0) {
				irc.sendContextReply(mes, "No karma items matched " + (item.regex ? "/" : "\"") + item.name + Colors.NORMAL + (item.regex ? "/" : "\"") + ".");
			} else {
				Collections.sort(odbItems, new KarmaSortByAbsValue());

				String rpl = "Karma items matching " + (item.regex ? "/" : "\"") + item.name + Colors.NORMAL + (item.regex ? "/" : "\"") + ": ";
				boolean cutOff = false;
				for (int j = 0; j < odbItems.size(); j++) {
					final KarmaObject ko = odbItems.get(j);
					if (rpl.length() + ko.string.length() > 350) {
						cutOff = true;
						break;
					}
					if (j > 0) {
						rpl += ", ";
					}
					rpl += formatKarmaNameForIRC(ko.string) + " (" + ko.value + ")";
				}
				if (cutOff) {
					rpl += ", ...";
				} else {
					rpl += ".";
				}
				irc.sendContextReply(mes, rpl);
			}
		}





		//List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();
		//List<String> names = new ArrayList<String>();

		//if (params.size() > 0)
		//	for (int i=0; i<params.size(); i++)
		//	{
		//	}
	}


	public String[] helpCommandList = {
		"Get a list of all karma objects.",
	};
	public String commandList (final String mes)
	{
		return "No chance, matey.";
	}

	public void webList(final PrintWriter out, final String params, final String[] user)
	{
		out.println("HTTP/1.0 200 OK");
		out.println("Content-Type: text/html");
		out.println();
		out.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">");
		out.println("<HTML>");
		out.println("<HEAD>");
		out.println("</HEAD>");
		out.println("<BODY>");

		// Parse input data.
		String karmaSearch = "";
		for (final String param : params.split("&")) {
			if (param.startsWith("s=")) {
				karmaSearch = param.substring(2);
			} else if (param.indexOf("=") == -1) {
				karmaSearch = normalise(param);
			}
		}

		// Search form!
		out.println("<FORM METHOD='GET' STYLE='float: right;'>");
		out.println("/<INPUT TYPE='TEXT' NAME='s' SIZE='20'>/i <INPUT TYPE='SUBMIT' VALUE='Search'>");
		out.println("</FORM>");

		// Fetch results.
		List<KarmaObject> karmaObjects = null;
		if (karmaSearch != "") {
			karmaObjects = retrieveKarmaObjects("WHERE string RLIKE \"" + mods.odb.escapeForRLike(karmaSearch) + "\" AND NOT (up = 0 AND down = 0 AND value = 0) SORT INTEGER value");
			out.println("<H1>" + karmaObjects.size() + " karma item" + (karmaObjects.size() == 1 ? "" : "s") + " matching /" + mods.scrape.escapeForHTML(karmaSearch) + "/i</H1>");
		} else {
			karmaObjects = retrieveKarmaObjects("WHERE 1 SORT INTEGER value");
			out.println("<H1>All Karma items</H1>");
		}
		Collections.sort(karmaObjects, new KarmaSortByAbsValue());

		// Show table of karma items.
		out.println("  <TABLE>");
		out.print("    <TR>");
		out.print("<TH>Item</TH>");
		out.print("<TH>Value</TH>");
		out.print("<TH>Up</TH>");
		out.print("<TH>Down</TH>");
		out.println("</TR>");
		for (final KarmaObject karmaObject: karmaObjects) {
			if (karmaObject != null) {
				out.println("    <TR>");
				out.print("<TD><A HREF='Karma.View?id=" + karmaObject.id + "'>" + mods.scrape.escapeForHTML(karmaObject.string) + "</A></TD>");
				out.print("<TD>" + karmaObject.value + "</TD>");
				out.print("<TD>" + karmaObject.up + "</TD>");
				out.print("<TD>" + karmaObject.down + "</TD>");
				out.println("</TR>");
			}
		}

		out.println("  </TABLE>");
		out.println("</BODY>");
		out.println("</HTML>");
	}

	public void webView(final PrintWriter out, final String params, final String[] user)
	{
		out.println("HTTP/1.0 200 OK");
		out.println("Content-Type: text/html");
		out.println();
		out.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">");
		out.println("<HTML>");
		out.println("<HEAD>");
		out.println("</HEAD>");
		out.println("<BODY>");

		// Parse input data.
		int karmaId = 0;
		for (final String param : params.split("&")) {
			if (param.startsWith("id=")) {
				karmaId = Integer.parseInt(param.substring(3));
			} else if (param.indexOf("=") == -1) {
				final List<KarmaObject> temp = mods.odb.retrieve(KarmaObject.class, "WHERE string = \"" + mods.odb.escapeString(param) + "\"");
				if (temp.size() == 1) {
					karmaId = temp.get(0).id;
				}
			}
		}

		// Fetch karma item.
		final List<KarmaObject> karmaObjects = mods.odb.retrieve(KarmaObject.class, "WHERE id = " + karmaId);
		if (karmaObjects.size() >= 1) {
			final KarmaObject karmaObject = karmaObjects.get(0);
			out.println("<H1>Karma item \"" + mods.scrape.escapeForHTML(karmaObject.string) + "\"</H1>");

			// Show table of data about this item.
			out.println("  <TABLE>");
			out.println("    <TR><TH ALIGN='LEFT'>Item</TH><TD>" + mods.scrape.escapeForHTML(karmaObject.string) + "</TD></TR>");
			out.println("    <TR><TH ALIGN='LEFT'>Value</TH><TD>" + karmaObject.value + "</TD></TR>");
			out.println("    <TR><TH ALIGN='LEFT'>Up</TH><TD>" + karmaObject.up + "</TD></TR>");
			out.println("    <TR><TH ALIGN='LEFT'>Down</TH><TD>" + karmaObject.down + "</TD></TR>");
			out.println("  </TABLE>");

			out.println("  <TABLE>");
			boolean inUp = false;
			boolean inUnchanged = false;
			boolean inDown = false;
			final List<KarmaReasonObject> reasons = mods.odb.retrieve(KarmaReasonObject.class, "WHERE string = \"" + mods.odb.escapeString(karmaObject.string) + "\" SORT INTEGER direction");
			for (final KarmaReasonObject reason : reasons) {
				if (!inUp && reason.direction == 1) {
					//out.println("  <TR><TH>Reasons for gaining karma</TH></TR>");
					inUp = true;
				} else if (!inUnchanged && reason.direction == 0) {
					//out.println("  <TR><TH>Reasons for unchanged karma</TH></TR>");
					inUnchanged = true;
				} else if (!inDown && reason.direction == -1) {
					//out.println("  <TR><TH>Reasons for losing karma</TH></TR>");
					inDown = true;
				}
				out.println("  <TR><TD>" + mods.scrape.escapeForHTML(karmaObject.string + " has " + (reason.direction > 0 ? "gained" : (reason.direction < 0 ? "lost" : "unchanged")) + " karma " + reason.reason) + "</TD></TR>");
			}
			out.println("  </TABLE>");
		} else {
			out.println("  <P>The karma ID " + karmaId + " is not valid.</P>");
		}

		out.println("</BODY>");
		out.println("</HTML>");
	}

	private String normalise(final String name)
	{
		return name.replaceAll(" ", "_");
	}

	private String formatKarmaNameForIRC(final String name)
	{
		return "\"" + name + Colors.NORMAL + "\"";
	}

	private String getName (final Matcher ma)
	{
		if (ma.group(1) != null)
			return ma.group(1).replaceAll("\\\\(.)", "$1");
		return ma.group(2);
	}

	class DKarmaCache
	{
		final static String CACHE_TABLE = "_dkarma_cache";
		final Connection connection;

		DKarmaCache() throws SQLException
		{
			connection = mods.odb.getConnection();
			final Statement statement = connection.createStatement();
			try
			{
				final ResultSet rs = statement.executeQuery("SHOW TABLE STATUS FROM choob LIKE \"" + CACHE_TABLE + "\"");
				try
				{
					if (rs.next())
						genIfNeeded(rs.getDate("Create_time"));
				}
				finally
				{
					rs.close();
				}
			}
			finally
			{
				statement.close();
			}

		}

		private void genIfNeeded(final Date tableDate) throws SQLException {
			final Calendar c = Calendar.getInstance();
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			System.out.println(c.getTime() + " -- " + tableDate);
			if (tableDate.before(c.getTime()))
				genCache();
		}

		private void genCache() throws SQLException
		{
			final Statement statement = connection.createStatement();
			statement.execute("DROP TABLE IF EXISTS " + CACHE_TABLE);
			statement.execute("CREATE TABLE " + CACHE_TABLE
							+ " AS SELECT `Nick`,`Text`,`Time` from `History` where `Text` LIKE \"%++%\" OR `Text` LIKE \"%--%\"");
		}

		void close() {
			mods.odb.freeConnection(connection);
		}
	}

	private static final int minumumscore = 10;

	public String commandSimpleDivide(final String arg) throws SQLException
	{
		final String likeClause = "%" + arg
			.trim()
			.replaceAll("%", "\\%")
			.replaceAll(" ", "_")
			+ "%";

		final DKarmaCache c = new DKarmaCache();

		try
		{
			final Map<String, AtomicInteger> sc = getNickSpaminess(c.connection, likeClause);
			final PreparedStatement ps = c.connection.prepareStatement("select Nick,Text from _dkarma_cache where Text like ?");
			try
			{
				ps.setString(1, likeClause);
				final ResultSet rs = ps.executeQuery();
				try
				{
					double score = 0.0;
					while (rs.next())
					{
						final AtomicInteger mod = sc.get(mods.nick.getBestPrimaryNick(rs.getString(1)).toLowerCase());
						if (null != mod)
						{
							final Matcher ma = karmaPattern.matcher(rs.getString(2));
							while (ma.find())
							{
								final String item = null == ma.group(1) ? ma.group(2) : ma.group(1);
								final int up = ma.group(3).equals("++") ? 1 : -1;
								if (item.replaceAll("_", " ").equalsIgnoreCase(arg))
									score += up / (double)mod.get();
							}
						}
					}
					for (Map.Entry<String, AtomicInteger> e : sc.entrySet())
						System.out.println(e);

					return String.valueOf(score);
				}
				finally
				{
					rs.close();
				}
			}
			finally
			{
				ps.close();
			}
		}
		finally
		{
			c.close();
		}
	}

	private Map<String, AtomicInteger> getNickSpaminess(final Connection connection, String likeClause) throws SQLException {

		final Map<String, AtomicInteger> sc = new HashMap<String, AtomicInteger>();

		final PreparedStatement ps = connection.prepareStatement(
				"select Nick,count(*) as cnt " +
				"from _dkarma_cache " +
				"where Text like ? " +
				"group by nick " +
				"having cnt > " + minumumscore);
		try
		{
			ps.setString(1, likeClause);
			final ResultSet rs = ps.executeQuery();
			try
			{
				while (rs.next()) {
					final String nick = mods.nick.getBestPrimaryNick(rs.getString(1)).toLowerCase();

					AtomicInteger i = sc.get(nick);
					if (null == i)
						sc.put(nick, i = new AtomicInteger());

					i.addAndGet(rs.getInt(2));
				}
			}
			finally
			{
				rs.close();
			}
		}
		finally
		{
			ps.close();
		}
		return sc;
	}

}
