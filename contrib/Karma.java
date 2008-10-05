import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jibble.pircbot.Colors;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.*;
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
	
	public boolean equals(KarmaObject obj)
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
	KarmaReasonObject reason;
	int change;
	boolean flood;
	String instanceName;
	
	KarmaChangeHolder(String instanceName)
	{
		this.instanceName = instanceName;
	}
}

class KarmaReasonEnumerator
{
	public KarmaReasonEnumerator()
	{
		// Unhiding.
	}
	
	public KarmaReasonEnumerator(String enumSource, int[] idList)
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
		String[] list = this.idList.split("\\s*,\\s*");
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
	public int compare(KarmaObject o1, KarmaObject o2) {
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
	public boolean equals(Object obj) {
		return false;
	}
}

class KarmaSortByValue implements Comparator<KarmaObject>
{
	public int compare(KarmaObject o1, KarmaObject o2) {
		if (o1.value > o2.value) {
			return -1;
		}
		if (o1.value < o2.value) {
			return 1;
		}
		return 0;
	}

	@Override
	public boolean equals(Object obj) {
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

	private Modules mods;
	private IRCInterface irc;
	public Karma (Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
		mods.interval.callBack("clean-enums", 60000, 1);
	}

	// Interval
	public void interval(Object param)
	{
		if ("clean-enums".equals(param)) {
			// Clean up dead enumerators.
			long lastUsedCutoff = System.currentTimeMillis() - ENUM_TIMEOUT;
			List<KarmaReasonEnumerator> deadEnums = mods.odb.retrieve(KarmaReasonEnumerator.class, "WHERE lastUsed < " + lastUsedCutoff);
			for (int i = 0; i < deadEnums.size(); i++) {
				mods.odb.delete(deadEnums.get(i));
			}
			mods.interval.callBack(param, 60000, 1);
		}
	}

	private KarmaReasonObject pickRandomKarmaReason(List<KarmaReasonObject> reasons, String enumSource)
	{
		if (enumSource == null) {
			int index = (int)Math.floor(Math.random() * reasons.size());
			return reasons.get(index);
		}
		
		int reasonId = -1;
		enumSource = enumSource.toLowerCase();
		List<KarmaReasonEnumerator> enums = mods.odb.retrieve(KarmaReasonEnumerator.class, "WHERE enumSource = '" + mods.odb.escapeString(enumSource) + "'");
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
			int[] idList = new int[reasons.size()];
			for (int i = 0; i < reasons.size(); i++)
				idList[i] = reasons.get(i).id;
			
			krEnum = new KarmaReasonEnumerator(enumSource, idList);
			reasonId = krEnum.getNext();
			mods.odb.save(krEnum);
		}
		
		KarmaReasonObject rvReason = null;
		for (int i = 0; i < reasons.size(); i++) {
			KarmaReasonObject reason = reasons.get(i);
			if (reason.id == reasonId) {
				rvReason = reason;
				break;
			}
		}
		return rvReason;
	}

	private String[] getReasonResult(List<KarmaReasonObject> reasons, String enumSource)
	{
		if (reasons.size() == 0) {
			return null;
		}
		KarmaReasonObject reason = pickRandomKarmaReason(reasons, enumSource);
		return new String[] {
			reason.string,
			reason.reason,
			reason.direction == 1 ? "gained" : "lost"
		};
	}

	public String[] apiReason()
	{
		System.err.println("WARNING: Karma.apiReason called with no enumSource. No enumeration supported with this call.");
		return apiReasonEnum(null);
	}

	public String[] apiReason(boolean up)
	{
		System.err.println("WARNING: Karma.apiReason called with no enumSource. No enumeration supported with this call.");
		return apiReasonEnum(null, up);
	}

	public String[] apiReason(String name)
	{
		System.err.println("WARNING: Karma.apiReason called with no enumSource. No enumeration supported with this call.");
		return apiReasonEnum(null, name);
	}

	public String[] apiReason(String name, boolean up)
	{
		System.err.println("WARNING: Karma.apiReason called with no enumSource. No enumeration supported with this call.");
		return apiReasonEnum(null, name, up);
	}

	public String[] apiReasonEnum(String enumSource)
	{
		List<KarmaReasonObject> results = mods.odb.retrieve(KarmaReasonObject.class, "");
		return getReasonResult(results, enumSource);
	}

	public String[] apiReasonEnum(String enumSource, boolean up)
	{
		List<KarmaReasonObject> results = mods.odb.retrieve(KarmaReasonObject.class, "WHERE direction = '" + (up ? 1 : -1) + "'");
		return getReasonResult(results, enumSource + ":" + (up ? "up" : "down"));
	}

	public String[] apiReasonEnum(String enumSource, String name)
	{
		List<KarmaReasonObject> results = mods.odb.retrieve(KarmaReasonObject.class, "WHERE string = \"" + mods.odb.escapeString(name) + "\"");
		return getReasonResult(results, enumSource + "::" + name);
	}

	public String[] apiReasonEnum(String enumSource, String name, boolean up)
	{
		List<KarmaReasonObject> results = mods.odb.retrieve(KarmaReasonObject.class, "WHERE string = \"" + mods.odb.escapeString(name) + "\" AND direction = '" + (up ? 1 : -1) + "'");
		return getReasonResult(results, enumSource + ":" + (up ? "up" : "down") + ":" + name);
	}

	public String[] helpCommandReason = {
		"Find out why something rocks or sucks.",
		"[<Object>]",
		"<Object> is the optional thing to ask about."
	};
	public void commandReason( Message mes )
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
			irc.sendContextReply(mes, name + " has " + reason[2] + " karma " + reason[1]);
		else
			irc.sendContextReply(mes, "Nobody has ever told me why " + name + " has changed karma. :(");
	}

	private void nullReason(Message mes, boolean direction)
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

		irc.sendContextReply(mes, name + " has " + (direction ? "gained" : "lost") + " karma " + reason[1]);
	}

	public String[] helpCommandReasonUp = {
		"Find out why something rocks.",
		"[<Object>]",
		"<Object> is the optional thing to ask about."
	};
	public void commandReasonUp( Message mes )
	{
		String name = mods.util.getParamString(mes);

		String[] reason;
		if (name.equals(""))
		{
			nullReason(mes, true);
			return;
		}

		Matcher ma=karmaItemPattern.matcher(name);
		if (ma.find())
		{
			reason = apiReasonEnum(mes.getContext(), getName(ma), true);
			if (reason !=null)
				name = reason[0];
		}
		else
		{
			nullReason(mes, true);
			return;
		}

		if (reason != null)
			irc.sendContextReply(mes, name + " has gained karma " + reason[1]);
		else
			irc.sendContextReply(mes, "Nobody has ever told me why " + name + " has gained karma. :(");
	}

	public String[] helpCommandReasonDown = {
		"Find out why something sucks.",
		"[<Object>]",
		"<Object> is the optional thing to ask about."
	};
	public void commandReasonDown( Message mes )
	{
		String name = mods.util.getParamString(mes);

		String[] reason;
		if (name.equals(""))
		{
			reason = apiReasonEnum(mes.getContext(), false);
			if (reason != null)
				name = reason[0];
		}
		else
		{
			Matcher ma=karmaItemPattern.matcher(name);
			if (ma.find())
			{
				reason = apiReasonEnum(mes.getContext(), getName(ma), false);
				if (reason != null)
					name = reason[0];
			}
			else
			{
				nullReason(mes, false);
				return;
			}
		}


		if (reason != null)
			irc.sendContextReply(mes, name + " has lost karma " + reason[1]);
		else
			irc.sendContextReply(mes, "Nobody has ever told me why " + name + " has lost karma. :(");
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

	// ++ or --:
	final private static String plusplus_or_minusminus = "(\\+\\+|\\-\\-)";

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
	final private static Pattern reasonPattern = Pattern.compile(
		"(?x:"
		+ "^" // Anchor at start of string...
		+ karma_item
		+ plusplus_or_minusminus
		+ "\\s+"
		+ "("
			// A "natural English" reason
			+ "(?i: because | for)\\s" // Must start sensibly
			+ ".+?"                    // Rest of reason
		+ "|"
			// A bracketted reason
			+ "\\("
			+ ".+?" // Contains any text.
			+ "\\)"
		+ ")"
		+ "\\s*$" // Chew up all trailing whitespace.
		+ ")"
	);

	public final String filterKarmaRegex = plusplus_or_minusminus + "\\B";

	public synchronized void filterKarma(Message mes)
	{
		// Ignore lines that look like commands.
		if (mes.getFlags().containsKey("command"))
			return;
		
		List<List<String>> matches = new ArrayList<List<String>>();
		String nick = mods.nick.getBestPrimaryNick(mes.getNick());
		
		// Find out if we've got a reason going on.
		Matcher reasonMatch = reasonPattern.matcher(mes.getMessage());
		if (reasonMatch.matches())
		{
			List<String> groups = new ArrayList<String>();
			matches.add(groups);
			for (int i = 0; i <= 4; i++)
				groups.add(reasonMatch.group(i));
		}
		
		// Find all karma changes now we've covered the reason.
		Matcher karmaMatch = karmaPattern.matcher(mes.getMessage());
		while (karmaMatch.find())
		{
			List<String> groups = new ArrayList<String>();
			matches.add(groups);
			for (int i = 0; i <= 3; i++)
				groups.add(karmaMatch.group(i));
		}
		
		// If we got multiple matches, and the first one is the reason match,
		// we need to discard the first non-reason match (always index 1 while
		// we only support one reasoned item/line) as it's the same as the
		// reason match.
		if ((matches.size() > 1) && (matches.get(0).size() == 5))
			matches.remove(1);
		
		// List of all karma changes that will be applied and hash of them
		// so we can handle duplicates sanely.
		List<KarmaChangeHolder> karmas = new ArrayList<KarmaChangeHolder>();
		Map<String,KarmaChangeHolder> karmaMap = new HashMap<String,KarmaChangeHolder>();
		for (List<String> match : matches)
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
			if (karma.instanceName.equals/*NOT IgnoreCase*/("me") || karma.instanceName.equalsIgnoreCase(nick))
			{
				karma.instanceName = nick;
				karma.change--;
			}
			// Up or down?
			else if (match.get(3).equals("++"))
			{
				karma.change++;
			}
			else
			{
				karma.change--;
			}
			
			// Get the reason, if it's not excluded!
			if ((match.size() > 4) && !reasonExceptions.contains(match.get(4)))
			{
				karma.reason = new KarmaReasonObject();
				karma.reason.reason = match.get(4);
				karma.reason.direction = match.get(3).equals("++") ? 1 : -1;
			}
			
			if (!karmaMap.containsKey(name.toLowerCase()))
			{
				karmas.add(karma);
				karmaMap.put(karma.instanceName.toLowerCase(), karma);
				if (karmas.size() >= 5)
					break;
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
		for (KarmaChangeHolder karma : karmas)
		{
			try
			{
				// 15 minute block for each karma item, irespective of who or direction.
				int ret = (Integer)mods.plugin.callAPI("Flood", "IsFlooding", "Karma:" + normalise(karma.instanceName), FLOOD_RATE, 2);
				if (ret != 0)
				{
					if (ret == 1)
						karma.flood = true;
					continue;
				}
			}
			catch (ChoobNoSuchCallException e)
			{ } // ignore
			catch (Throwable e)
			{
				System.err.println("Couldn't do antiflood call: " + e);
			}
		}
		
		// Apply all the karma changes!
		for (KarmaChangeHolder karma : karmas)
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
			
			// Now add the reason, if there is one. Note that there's nothing
			// to retrieve here so we can save the local object directly.
			if (karma.reason != null)
			{
				karma.reason.string = karma.karma.string;
				mods.odb.save(karma.reason);
			}
		}
		
		// Generate a pretty reply, all actual processing is done now:
		if (karmas.size() == 1)
		{
			KarmaChangeHolder karma = karmas.get(0);
			
			if (karma.flood)
				irc.sendContextReply(mes, "Denied change to '" + karma.instanceName + "'! Karma changes limited to one change per item per " + FLOOD_RATE_STR + ".");
			else if (karma.karma.string.equals(nick))
				// This doesn't mention if there was a reason.
				irc.sendContextReply(mes, "Fool, that's less karma to you! That leaves you with " + karma.karma.value + ".");
			else
				irc.sendContextReply(mes, (karma.change > 0 ? "Given more karma" : (karma.change < 0 ? "Given less karma" : "No change")) + " to " + karma.instanceName + (karma.reason != null ? ", and understood your reasons" : "") + ". New karma is " + karma.karma.value + ".");
		}
		else
		{
			StringBuffer output = new StringBuffer("Karma adjustments: ");
			for (int i = 0; i < karmas.size(); i++)
			{
				KarmaChangeHolder karma = karmas.get(i);
				output.append(karma.instanceName);
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
					if (karma.reason != null)
						output.append(" with reason");
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

	private String postfix(int n)
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

	private void commandScores(Message mes, boolean asc)
	{
		List<KarmaObject> karmaObjs = retrieveKarmaObjects("SORT " + (asc ? "ASC" : "DESC") + " INTEGER value LIMIT (5)");

		StringBuffer output = new StringBuffer((asc ? "Low" : "High" ) + " Scores: ");

		for (int i=0; i<karmaObjs.size(); i++)
		{
			output.append(String.valueOf(i+1) + postfix(i+1));
			output.append(": ");
			output.append(karmaObjs.get(i).string);
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
	public void commandHighScores(Message mes)
	{
		commandScores(mes, false);
	}

	public String[] helpCommandLowScores = {
		"Find out what has the lowest karma"
	};
	public void commandLowScores(Message mes)
	{
		commandScores(mes, true);
	}

	private void saveKarmaObjects(List<KarmaObject> karmaObjs)
	{
		for (KarmaObject karmaObj: karmaObjs)
			mods.odb.update(karmaObj);
	}

	private KarmaObject retrieveKarmaObject(String name)
	{
		name = normalise(name);
		List<KarmaObject> results = mods.odb.retrieve(KarmaObject.class, "WHERE string = \"" + mods.odb.escapeString(name) + "\"");
		if (results.size() == 0)
		{
			KarmaObject newObj = new KarmaObject();
			newObj.string = name;
			mods.odb.save(newObj);
			return newObj;
		}
		return results.get(0);
	}

	private List<KarmaObject> retrieveKarmaObjects(String clause)
	{
		return mods.odb.retrieve(KarmaObject.class, clause);
	}

	public String[] helpCommandFight = {
		"Pit the karma of two objects against each other to find the leetest (or least lame).",
		"<Object 1> <Object 2>",
	};
	
	public void commandFight (Message mes)
	{
		List<String> params = new ArrayList<String>();
		
		Matcher ma=karmaItemPattern.matcher(mods.util.getParamString(mes));
		while (ma.find())
			params.add(getName(ma));
		
		List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();
		//Nab the params
		if (params.size() == 2) 
		{
			for (int i=0; i<2; i++)
			{
				String name = params.get(i);
				if (name!=null)
				{
					KarmaObject karmaObj = retrieveKarmaObject(name);
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
				int result = new KarmaSortByValue().compare(karmaObjs.get(0),karmaObjs.get(1));
				if (result == -1)
				{
					//Winner is Object 0
					irc.sendContextReply(mes, Colors.BOLD + karmaObjs.get(0).instName + Colors.NORMAL + " was victorious over " + Colors.BOLD + karmaObjs.get(1).instName + Colors.NORMAL + "! (" + karmaObjs.get(0).value + " vs " + karmaObjs.get(1).value + ")");
				}
				else if (result == 1)
				{
					//Winner is Object 1
					irc.sendContextReply(mes, Colors.BOLD + karmaObjs.get(1).instName + Colors.NORMAL + " was victorious over " + Colors.BOLD + karmaObjs.get(0).instName + Colors.NORMAL + "! (" + karmaObjs.get(1).value + " vs " + karmaObjs.get(0).value + ")");
				}
				else
				{
					//Should only be a draw
					irc.sendContextReply(mes, "The battle between " + Colors.BOLD + karmaObjs.get(0).instName  + Colors.NORMAL + " and "  + Colors.BOLD + karmaObjs.get(1).instName  + Colors.NORMAL + " was a draw! (" + karmaObjs.get(0).value + " vs " + karmaObjs.get(1).value + ")");
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
	public void commandReal (Message mes)
	{
		List<String> params = new ArrayList<String>();
		Matcher ma=karmaItemPattern.matcher(mods.util.getParamString(mes));
		
		while (ma.find())
			params.add(getName(ma));
		
		List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();
		
		if (params.size() > 0)
			for (int i=0;i<params.size();i++)
			{
				String name = params.get(i);
				if (name != null) {
					KarmaObject karmaObj = retrieveKarmaObject(name);
					karmaObj.instName = name;
					karmaObjs.add(karmaObj);
				}
			}
		
		if (karmaObjs.size() == 1)
		{
			int realkarma = karmaObjs.get(0).up - karmaObjs.get(0).down;
			irc.sendContextReply(mes, karmaObjs.get(0).instName + " has a \"real\" karma of " + realkarma + " (" + karmaObjs.get(0).up + " up, " + karmaObjs.get(0).down + " down).");
			return;
		}
		
		StringBuffer output = new StringBuffer("\"Real\" Karmas: ");
		
		if (karmaObjs.size()!=0)
		{
			for (int i=0;i<karmaObjs.size();i++)
			{
				int realkarma = karmaObjs.get(i).up - karmaObjs.get(i).down;
				output.append(karmaObjs.get(i).instName + ": " + realkarma);
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
	
	public void commandGet (Message mes)
	{
		List<String> params = new ArrayList<String>();

		Matcher ma=karmaItemPattern.matcher(mods.util.getParamString(mes));

		while (ma.find())
			params.add(getName(ma));

		List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();
		if (params.size() > 0)
			for (int i=0; i<params.size(); i++)
			{
				String name = params.get(i);
				if (name!=null)
				{
					KarmaObject karmaObj = retrieveKarmaObject(name);
					karmaObj.instName = name;
					karmaObjs.add(karmaObj);
				}
			}

		if (karmaObjs.size() == 1)
		{
			irc.sendContextReply(mes, karmaObjs.get(0).instName + " has a karma of " + karmaObjs.get(0).value + " (" + karmaObjs.get(0).up + " up, " + karmaObjs.get(0).down + " down).");
			return;
		}

		StringBuffer output = new StringBuffer("Karmas: ");

		if (karmaObjs.size()!=0)
		{
			for (int i=0; i<karmaObjs.size(); i++)
			{
				output.append(karmaObjs.get(i).instName);
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
			irc.sendContextReply( mes, output.toString());
		}
		else
			irc.sendContextReply( mes, "Check the karma of what?");
	}

	public String[] helpCommandSet = {
		"Set out the karma of some object or other.",
		"<Object>=<Value> [<Object>=<Value> ...]",
		"<Object> is the name of something to set the karma of",
		"<Value> is the value to set the karma to"
	};
	public synchronized void commandSet( Message mes )
	{
		mods.security.checkNickPerm(new ChoobPermission("plugins.karma.set"), mes);

		List<String> params = mods.util.getParams( mes );
		List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();
		for (int i=1; i<params.size(); i++)
		{
			String param = params.get(i);
			String[] items = param.split("=");
			if (items.length != 2)
			{
				irc.sendContextReply(mes, "Bad syntax: Use <Object>=<Value> [<Object>=<Value> ...]");
				return;
			}

			String name = items[0].trim();
			int val;
			try
			{
				val = Integer.parseInt(items[1]);
			}
			catch (NumberFormatException e)
			{
				irc.sendContextReply(mes, "Bad syntax: Karma value " + items[1] + " is not a valid integer.");
				return;
			}

			KarmaObject karmaObj = retrieveKarmaObject(name);

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

		StringBuffer output = new StringBuffer("Karma adjustment");
		output.append(karmaObjs.size() == 1 ? "" : "s");
		output.append(": ");
		for (int i=0; i<karmaObjs.size(); i++)
		{
			output.append(karmaObjs.get(i).instName);
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
		KarmaSearchItem(String name, boolean regex)
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
	public void commandSearch(Message mes)
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

		for (KarmaSearchItem item : params)
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
				irc.sendContextReply(mes, "No karma items matched " + (item.regex ? "/" : "'") + item.name + (item.regex ? "/" : "'") + ".");
			} else {
				Collections.sort(odbItems, new KarmaSortByAbsValue());

				String rpl = "Karma items matching " + (item.regex ? "/" : "'") + item.name + (item.regex ? "/" : "'") + ": ";
				boolean cutOff = false;
				for (int j = 0; j < odbItems.size(); j++) {
					KarmaObject ko = odbItems.get(j);
					if (rpl.length() + ko.string.length() > 350) {
						cutOff = true;
						break;
					}
					if (j > 0) {
						rpl += ", ";
					}
					rpl += ko.string + " (" + ko.value + ")";
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
	public void commandList (Message mes)
	{
		irc.sendContextReply(mes, "No chance, matey.");
	}

	public void webList(PrintWriter out, String params, String[] user)
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
		for (String param : params.split("&")) {
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
		for (KarmaObject karmaObject: karmaObjects) {
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
	
	public void webView(PrintWriter out, String params, String[] user)
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
		for (String param : params.split("&")) {
			if (param.startsWith("id=")) {
				karmaId = Integer.parseInt(param.substring(3));
			} else if (param.indexOf("=") == -1) {
				List<KarmaObject> temp = mods.odb.retrieve(KarmaObject.class, "WHERE string = \"" + mods.odb.escapeString(param) + "\"");
				if (temp.size() == 1) {
					karmaId = temp.get(0).id;
				}
			}
		}
		
		// Fetch karma item.
		List<KarmaObject> karmaObjects = mods.odb.retrieve(KarmaObject.class, "WHERE id = " + karmaId);
		if (karmaObjects.size() >= 1) {
			KarmaObject karmaObject = karmaObjects.get(0);
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
			boolean inDown = false;
			List<KarmaReasonObject> reasons = mods.odb.retrieve(KarmaReasonObject.class, "WHERE string = \"" + mods.odb.escapeString(karmaObject.string) + "\" SORT INTEGER direction");
			for (KarmaReasonObject reason : reasons) {
				if (!inUp && (reason.direction == 1)) {
					//out.println("  <TR><TH>Reasons for gaining karma</TH></TR>");
					inUp = true;
				} else if (!inDown && (reason.direction == -1)) {
					//out.println("  <TR><TH>Reasons for losing karma</TH></TR>");
					inDown = true;
				}
				out.println("  <TR><TD>" + mods.scrape.escapeForHTML(karmaObject.string + " has " + (reason.direction > 0 ? "gained" : "lost") + " karma " + reason.reason) + "</TD></TR>");
			}
			out.println("  </TABLE>");
		} else {
			out.println("  <P>The karma ID " + karmaId + " is not valid.</P>");
		}
		
		out.println("</BODY>");
		out.println("</HTML>");
	}

	private String normalise(String name)
	{
		return name.replaceAll(" ", "_");
	}

	private String getName (Matcher ma)
	{
		if (ma.group(1) != null)
			return ma.group(1).replaceAll("\\\\(.)", "$1");
		return ma.group(2);
	}
}
