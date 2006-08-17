import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;
import org.jibble.pircbot.Colors;


public class KarmaObject
{
	public int id;
	public String string;
	public int up;
	public int down;
	public int value;
	boolean increase;
	String instName;
	
	public boolean equals(KarmaObject obj) 
	{
		return this.string.equalsIgnoreCase(obj.string);
	}
}

public class KarmaReasonObject
{
	public int id;
	public String string;
	public int direction;
	public String reason;
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
	}

	public String[] apiReason()
	{
		List<KarmaReasonObject> results;
		results = mods.odb.retrieve(KarmaReasonObject.class, "ORDER BY RAND() LIMIT 1");

		if (results.size() == 0)
			return null;
		else
			return new String[] {
				results.get(0).string,
				results.get(0).reason,
				results.get(0).direction == 1 ? "gained" : "lost"
			};
	}

	public String[] apiReason(boolean up)
	{
		List<KarmaReasonObject> results;
		results = mods.odb.retrieve(KarmaReasonObject.class, "WHERE direction = '" + (up ? 1 : -1) + "' ORDER BY RAND() LIMIT 1");

		if (results.size() == 0)
			return null;
		else
			return new String[] {
				results.get(0).string,
				results.get(0).reason,
				up ? "gained" : "lost"
			};
	}

	public String[] apiReason(String name)
	{
		List<KarmaReasonObject> results;
		results = mods.odb.retrieve(KarmaReasonObject.class, "WHERE string = \"" + mods.odb.escapeString(name) + "\" ORDER BY RAND() LIMIT 1");

		if (results.size() == 0)
			return null;
		else
			return new String[] {
				name,
				results.get(0).reason,
				results.get(0).direction == 1 ? "gained" : "lost"
			};
	}

	public String[] apiReason(String name, boolean up)
	{
		List<KarmaReasonObject> results;
		results = mods.odb.retrieve(KarmaReasonObject.class, "WHERE string = \"" + mods.odb.escapeString(name) + "\" AND direction = '" + (up ? 1 : -1) + "' ORDER BY RAND() LIMIT 1");

		if (results.size() == 0)
			return null;
		else
			return new String[] {
				name,
				results.get(0).reason,
				up ? "gained" : "lost"
			};
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
			reason = apiReason();
			if (reason != null)
				name = reason[0];
		}
		else
		{
			Matcher ma = karmaItemPattern.matcher(name);
			if (ma.find())
			{
				reason = apiReason(getName(ma));
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
				reason = apiReason(getName(ma));
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
		reason = apiReason(direction);
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
		else
		{
			Matcher ma=karmaItemPattern.matcher(name);
			if (ma.find())
			{
				reason = apiReason(getName(ma), true);
				if (reason !=null)
					name = reason[0];
			}
			else
			{
				nullReason(mes, true);
				return;
			}
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
			reason = apiReason(false);
			if (reason != null)
				name = reason[0];
		}
		else
		{
			Matcher ma=karmaItemPattern.matcher(name);
			if (ma.find())
			{
				reason = apiReason(getName(ma), false);
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

	public synchronized void filterKarma( Message mes, Modules mods, IRCInterface irc )
	{
		// Ignore lines that look like commands.
		if (Pattern.compile(irc.getTriggerRegex()).matcher(mes.getMessage()).find())
			return;

		Matcher reasonMatch = reasonPattern.matcher(mes.getMessage());

		boolean hasReason = false;
		String karmaReasonFloodCheck = "";
		if (reasonMatch.matches())
		{
			// Find out which holds our name.
			String name;
			boolean skip = false;
			// Group 1: quoted karma item
			// Group 2: raw karma item
			// Group 3: "++" or "--"
			// Group 4: reason
			if (reasonMatch.group(1) != null)
				name = reasonMatch.group(1).replaceAll("\\\\(.)", "$1");
			else
			{
				// need verification on this one.
				name = reasonMatch.group(2);
				if (exceptions.contains(name.toLowerCase()))
					skip = true;
			}
			if (!skip)
			{
				// Wait until we know there's a real karma change going on.
				if (mes instanceof PrivateEvent)
				{
					irc.sendContextReply(mes, "I'm sure other people want to hear what you have to think!");
					return;
				}

				// Karma flood check.
				karmaReasonFloodCheck = name;
				try
				{
					// 15 minute block for each karma item, irespective of who or direction.
					int ret = (Integer)mods.plugin.callAPI("Flood", "IsFlooding", "Karma:" + name.replaceAll(" ", "_"), FLOOD_RATE, 2);
					if (ret != 0)
					{
						if (ret == 1)
							irc.sendContextReply(mes, "Denied change to '" + name + "'! Karma changes limited to one change per item per " + FLOOD_RATE_STR + ".");
						return;
					}
				}
				catch (ChoobNoSuchCallException e)
				{ } // ignore
				catch (Throwable e)
				{
					System.err.println("Couldn't do antiflood call: " + e);
				}

				boolean increase = reasonMatch.group(3).equals("++");

				final String sreason = reasonMatch.group(4);

				if (!reasonExceptions.contains(sreason))
				{
					// Store the reason too.
					KarmaReasonObject reason = new KarmaReasonObject();
					reason.string = name;
					reason.reason = sreason;
					reason.direction = increase ? 1 : -1;
					mods.odb.save(reason);

					hasReason = true;
				}
			}
		}

		// Not a reasoned match, then.
		Matcher karmaMatch = karmaPattern.matcher(mes.getMessage());

		String nick = mods.nick.getBestPrimaryNick(mes.getNick());

		HashSet used = new HashSet();
		List<String> names = new ArrayList<String>();
		List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();

		while (karmaMatch.find() && karmaObjs.size() < 5)
		{
			String name;
			boolean skip = false;
			// Group 1: quoted karma item
			// Group 2: raw karma item
			// Group 3: "++" or "--"
			if (karmaMatch.group(1) != null)
				name = karmaMatch.group(1).replaceAll("\\\\(.)", "$1");
			else
			{
				// need verification on this one.
				name = karmaMatch.group(2);
				if (exceptions.contains(name.toLowerCase()))
					skip = true;
			}
			if (skip)
				continue;

			// Wait until we know there's a real karma change going on.
			if (mes instanceof PrivateEvent)
			{
				irc.sendContextReply(mes, "I'm sure other people want to hear what you have to think!");
				return;
			}

			if (name.equals/*NOT IgnoreCase*/("me"))
				name=nick;

			// Have we already done this?
			if (used.contains(name.toLowerCase()))
				continue;
			used.add(name.toLowerCase());

			boolean increase = karmaMatch.group(3).equals("++");

			if (name.equalsIgnoreCase(nick))
				increase = false;

			// Karma flood check (skip if we checked this item with the reason earlier).
			if (!name.equalsIgnoreCase(karmaReasonFloodCheck))
			{
				try
				{
					// 15 minute block for each karma item, irespective of who or direction.
					int ret = (Integer)mods.plugin.callAPI("Flood", "IsFlooding", "Karma:" + name.replaceAll(" ", "_"), FLOOD_RATE, 2);
					if (ret != 0)
					{
						if (ret == 1)
							irc.sendContextReply(mes, "Denied change to '" + name + "'! Karma changes limited to one change per item per " + FLOOD_RATE_STR + ".");
						return;
					}
				}
				catch (ChoobNoSuchCallException e)
				{ } // ignore
				catch (Throwable e)
				{
					System.err.println("Couldn't do antiflood call: " + e);
				}
			}

			KarmaObject karmaObj = retrieveKarmaObject(name);
			if (increase)
			{
				karmaObj.up++;
				karmaObj.value++;
			}
			else
			{
				karmaObj.down++;
				karmaObj.value--;
			}

			karmaObj.instName = name;
			karmaObj.increase = increase;

			karmaObjs.add(karmaObj);
		}

		saveKarmaObjects(karmaObjs);

		// No actual karma changes. Maybe someone said "c++" or something.
		if (karmaObjs.size() == 0)
			return;

		// Generate a pretty reply, all actual processing is done now:

		if (karmaObjs.size() == 1)
		{
			KarmaObject info = karmaObjs.get(0);

			if (info.string.equalsIgnoreCase(nick))
				// This doesn't mention if there was a reason..
				irc.sendContextReply(mes, "Fool, that's less karma to you! That leaves you with " + info.value + ".");
			else
			{
				if (hasReason)
					irc.sendContextReply(mes, "Given " + (info.increase ? "karma" : "less karma") + " to " + info.instName + ", and understood your reasons. New karma is " + info.value + ".");
				else
					irc.sendContextReply(mes, "Given " + (info.increase ? "karma" : "less karma") + " to " + info.instName + ". New karma is " + info.value + ".");
			}
			return;
		}

		StringBuffer output = new StringBuffer("Karma adjustments: ");

		for (int i=0; i<karmaObjs.size(); i++)
		{
			KarmaObject info = karmaObjs.get(i);
			output.append(info.instName);
			output.append(info.increase ? " up" : " down");
			if (i == 0 && hasReason)
				output.append(", with reason");
			output.append(" (now " + info.value + ")");
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

	private void commandScores(Message mes, Modules mods, IRCInterface irc, boolean asc)
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
		commandScores(mes, mods, irc, false);
	}

	public String[] helpCommandLowScores = {
		"Find out what has the lowest karma"
	};
	public void commandLowScores(Message mes)
	{
		commandScores(mes, mods, irc, true);
	}

	private void saveKarmaObjects(List<KarmaObject> karmaObjs)
	{
		for (KarmaObject karmaObj: karmaObjs)
			mods.odb.update(karmaObj);
	}

	private KarmaObject retrieveKarmaObject(String name)
	{
		name = name.replaceAll(" ", "_");
		List<KarmaObject> results = mods.odb.retrieve(KarmaObject.class, "WHERE string = \"" + mods.odb.escapeString(name) + "\"");
		if (results.size() == 0)
		{
			KarmaObject newObj = new KarmaObject();
			newObj.string = name;
			mods.odb.save(newObj);
			return newObj;
		}
		else
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
	
	public void commandFight (Message mes, Modules mods, IRCInterface irc)
	{
		List<String> params = new ArrayList<String>();
		
		Matcher ma=karmaItemPattern.matcher(mods.util.getParamString(mes));
		while (ma.find())
			params.add(getName(ma));
		
		List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();
		List<String> names = new ArrayList<String>();
		
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
	public void commandReal (Message mes, Modules mods, IRCInterface irc)
	{
		List<String> params = new ArrayList<String>();
		Matcher ma=karmaItemPattern.matcher(mods.util.getParamString(mes));
		
		while (ma.find())
			params.add(getName(ma));
		
		List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();
		List<String> names = new ArrayList<String>();
		
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
	
	public void commandGet (Message mes, Modules mods, IRCInterface irc)
	{
		List<String> params = new ArrayList<String>();

		Matcher ma=karmaItemPattern.matcher(mods.util.getParamString(mes));

		while (ma.find())
			params.add(getName(ma));

		List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();
		List<String> names = new ArrayList<String>();

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
	public synchronized void commandSet( Message mes, Modules mods, IRCInterface irc )
	{
		mods.security.checkNickPerm(new ChoobPermission("plugins.karma.set"), mes);

		List<String> params = mods.util.getParams( mes );
		List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();
		List<String> names = new ArrayList<String>();

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
	public void commandSearch(Message mes, Modules mods, IRCInterface irc)
	{
		final List<KarmaSearchItem> params = new ArrayList<KarmaSearchItem>();

		final Matcher ma = Pattern.compile(karma_item + "|" + slashed_regex_string).matcher(mods.util.getParamString(mes));

		while (ma.find())
		{
			final String name = getName(ma);
			params.add(
				name == null ?
				new KarmaSearchItem(ma.group(3).replaceAll(" ", "_"), true) :
				new KarmaSearchItem(name.replaceAll(" ", "_"), false)
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
				odbQuery = "WHERE string RLIKE \"" + mods.odb.escapeString(item.name) + "\"" + andNotZero;
			} else {
				// Substring
				odbQuery = "WHERE string LIKE \"%" + mods.odb.escapeString(item.name) + "%\"" + andNotZero;
			}

			final List<KarmaObject> odbItems = (List<KarmaObject>)mods.odb.retrieve(KarmaObject.class, odbQuery);

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
	public void commandList (Message mes, Modules mods, IRCInterface irc)
	{
		irc.sendContextReply(mes, "No chance, matey.");
	}

	public void webList(PrintWriter out, String params, String[] user)
	{
		out.println("HTTP/1.0 200 OK");
		out.println("Content-Type: text/html");
		out.println();

		List<KarmaObject> res = retrieveKarmaObjects("WHERE 1 SORT INTEGER value");


		out.println("Item count: " + res.size() + "<br/>");
		for(KarmaObject karmaObject: res)
		{
			if (karmaObject == null)
				out.println("NULL.<br/>");
			else
				out.println("" + karmaObject.id + ": " + karmaObject.string + " => " + karmaObject.value + ".<br/>");
		}
	}


	private String getName (Matcher ma)
	{
		if (ma.group(1) != null)
			return ma.group(1).replaceAll("\\\\(.)", "$1");
		else
			return ma.group(2);
	}
}
