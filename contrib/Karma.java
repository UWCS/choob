import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;


public class KarmaObject
{
	public int id;
	public String string;
	public int up;
	public int down;
	public int value;
	boolean increase;
}

public class KarmaReasonObject
{
	public int id;
	public String string;
	public int direction;
	public String reason;
}

public class Karma
{
	Modules mods;
	public Karma (Modules mods)
	{
		this.mods=mods;
	}

	// Java-- A better regex would be: (")?((?(1)(?:[ \\./a-zA-Z0-9_-]{2,})|(?:[\\./a-zA-Z0-9_-]{2,})))(?(1)")((?:\\+\\+)|(?:\\-\\-))
	public String filterKarmaRegex = "(?x:\\b(?:"
		+ "("
			+ "[\\./a-zA-Z0-9_-]{3,}" // 3 chars anywhere
		+ "|"
			+ "^[\\./a-zA-Z0-9_-]+" // Or anything at the start
		+ ")"

		+ "( \\+\\+ | \\-\\- )" // The actual karma change
		+ ")\\B)";

	public String apiReason(String wot, boolean up) throws ChoobException
	{
		List<KarmaReasonObject> results;
		results = mods.odb.retrieve(KarmaReasonObject.class, "WHERE string = '" + wot + "' AND direction = '" + (up ? 1 : -1) + "'");

		if (results.size() == 0)
			return null;
		else
			return (results.get((new Random()).nextInt(results.size()))).reason;
	}

	public String[] helpCommandReasonUp = {
		"Find out why something rocks.",
		"<what>"
	};

	public void commandReasonUp( Message mes, Modules mods, IRCInterface irc ) throws ChoobException
	{
		String wot=mods.util.getParamString(mes);
		String re=apiReason(wot, true);
		if (re!=null)
			irc.sendContextReply(mes, wot + " has been given karma " + re);
		else
			irc.sendContextReply(mes, "Nobody has ever told me why " + wot + " has gained karma. :(");
	}

	public String[] helpCommandReasonDown = {
		"Find out why something sucks.",
		"<what>"
	};

	public void commandReasonDown( Message mes, Modules mods, IRCInterface irc ) throws ChoobException
	{
		String wot=mods.util.getParamString(mes);
		String re=apiReason(wot, false);
		if (re!=null)
			irc.sendContextReply(mes, wot + " has lost karma " + re);
		else
			irc.sendContextReply(mes, "Nobody has ever told me why " + wot + " has lost karma. :(");
	}

	public String[] helpTopics = { "Using" };
	public String[] helpUsing = {
		  "To change the karma of something, simply send the message"
		+ " 'something--' or 'something++'. You can also specify a reason, by"
		+ " sending a line starting with a karma change then giving a reason,"
		+ " like: 'ntl-- because they suck' or 'ntl-- for sucking teh c0k'."
	};

	public void filterKarma( Message mes, Modules mods, IRCInterface irc ) throws ChoobException
	{
		if (mes instanceof PrivateEvent)
		{
			irc.sendContextReply(mes, "I'm sure other people want to hear what you have to think!");
			return;
		}
		String special="";

		ArrayList<KarmaObject> karmaObjs = new ArrayList();
		HashSet used = new HashSet();

		Matcher reasonMatch = Pattern.compile("(?x:^"
				+ "([\\./a-zA-Z0-9_+-]+)" // The karma string.
				+ "(\\+\\+|\\-\\-)" // Up or down.
				+ "\\s+((?:because)|(?:for)" // Ensure proper english.
				+ ".+?)" // The reason.
				+ "$)")
			.matcher(mes.getMessage());

		if (reasonMatch.matches())
		{
			KarmaReasonObject reason = new KarmaReasonObject();
			reason.string = reasonMatch.group(1);
			reason.direction = (reasonMatch.group(2).equals("++") ? 1 : -1);
			reason.reason = reasonMatch.group(3);
			mods.odb.save(reason);
			special=" (with reason)";
		}

		Matcher karmaMatch = Pattern.compile(filterKarmaRegex).matcher(mes.getMessage());

		String nick=mods.nick.getBestPrimaryNick(mes.getNick());

		List<String> names = new ArrayList<String>();
		while (karmaMatch.find() && karmaObjs.size()<5)
		{
			String name = karmaMatch.group(1);

			name=name.replaceAll("-+","-");

			// Have we already done this?
			if (used.contains(name.toLowerCase()))
				continue;
			used.add(name.toLowerCase());

			boolean increase = karmaMatch.group(2).equals("++");

			if (name.equalsIgnoreCase(nick))
				increase=false;

			List<KarmaObject> results = retrieveKarmaObjects("WHERE string = '" + name + "'", mods);
			KarmaObject karmaObj;
			if (results.size() == 0)
			{
				karmaObj = new KarmaObject();
				karmaObj.string = name;
				if (increase)
				{
					karmaObj.up=1;
					karmaObj.value=1;
				}
				else
				{
					karmaObj.down=1;
					karmaObj.value=-1;
				}
			}
			else
			{
				karmaObj = results.get(0);
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
			}
			karmaObj.increase = increase;
			karmaObjs.add(karmaObj);
			names.add(name);

			if (karmaMatch.start()==0) // If something is karma'd at the start of a line, don't allow anything else to be karma'd on that line.
				break;
		}

		saveKarmaObjects(karmaObjs, mods);


		// Generate a pretty reply, all actual processing is done now:

		if (karmaObjs.size() == 1)
		{
			KarmaObject info = karmaObjs.get(0);
			irc.sendContextReply(mes, (info.increase ? "Karma" : "Less karma") + " to " + names.get(0) + special + ", " + (info.increase ? "giving" : "leaving") + " a karma of " + info.value + ".");
			return;
		}

		StringBuffer output = new StringBuffer("Karma adjustments: ");

		for (int i=0; i<karmaObjs.size(); i++)
		{
			KarmaObject info = karmaObjs.get(i);
			output.append(names.get(i));
			output.append(info.increase ? " up" : " down");
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
		switch (n%10)
		{
			case 1: return "st";
			case 2: return "nd";
			case 3: return "rd";
		}
		return "th";
	}

	private void commandScores(Message mes, Modules mods, IRCInterface irc, boolean asc) throws ChoobException
	{
		List<KarmaObject> karmaObjs = retrieveKarmaObjects("SORT " + (asc ? "ASC" : "DESC") + " INTEGER value LIMIT (5)", mods);

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
	public void commandHighScores(Message mes, Modules mods, IRCInterface irc) throws ChoobException
	{
		commandScores(mes, mods, irc, false);
	}

	public String[] helpCommandLowScores = {
		"Find out what has the lowest karma"
	};
	public void commandLowScores(Message mes, Modules mods, IRCInterface irc) throws ChoobException
	{
		commandScores(mes, mods, irc, true);
	}

	private void saveKarmaObjects (List<KarmaObject> karmaObjs, Modules mods) throws ChoobException
	{
		for (KarmaObject karmaObj: karmaObjs)
			if (karmaObj.id==0)
				mods.odb.save(karmaObj);
			else
				mods.odb.update(karmaObj);
	}

	private List<KarmaObject> retrieveKarmaObjects(String clause, Modules mods) throws ChoobException
	{
		return mods.odb.retrieve(KarmaObject.class, clause);
	}

	public String[] helpCommandGet = {
		"Find out the karma of some object or other.",
		"<Object> [<Object> ...]",
		"<Object> is the name of something to get the karma of"
	};
	public void commandGet (Message mes, Modules mods, IRCInterface irc) throws ChoobException
	{
		List<String> params = mods.util.getParams( mes );
		List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();
		List<String> names = new ArrayList<String>();
		if (params.size()>1)
			for (int i=1; i<params.size(); i++)
			{
				String name = params.get(i);
				names.add(name);
				List<KarmaObject> results = retrieveKarmaObjects("WHERE string = '" + name + "'", mods);
				if (results.size()!=0)
					karmaObjs.add((KarmaObject)results.get(0));
				else
				{
					KarmaObject karmaObj = new KarmaObject();
					karmaObj.string = name;
					karmaObj.value = 0;
					karmaObjs.add(karmaObj);
				}
			}

		if (karmaObjs.size()==1)
		{
			irc.sendContextReply(mes, karmaObjs.get(0).string + " has a karma of " + karmaObjs.get(0).value + ".");
			return;
		}

		StringBuffer output = new StringBuffer("Karmas: ");

		for (int i=0; i<karmaObjs.size(); i++)
		{
			output.append(names.get(i));
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

	public String[] helpCommandSet = {
		"Set out the karma of some object or other.",
		"<Object>=<Value> [<Object>=<Value> ...]",
		"<Object> is the name of something to set the karma of",
		"<Value> is the value to set the karma to"
	};
	public void commandSet( Message mes, Modules mods, IRCInterface irc ) throws ChoobException
	{
		mods.security.checkNickPerm(new ChoobPermission("plugins.karma.set"), mes.getNick());

		List<String> params = mods.util.getParams( mes );
		List<KarmaObject> karmaObjs = new ArrayList<KarmaObject>();
		List<String> names = new ArrayList<String>();

		for (int i=1; i<params.size(); i++)
		{
			KarmaObject karmaObj;
			String param = params.get(i);
			String[] items = param.split("=");
			if (items.length != 2)
			{
				irc.sendContextReply(mes, "Bad syntax: Use OBJECT=VALUE OBJECT=VALUE ...");
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

			names.add(name);

			List<KarmaObject> results = retrieveKarmaObjects("WHERE string = '" + name + "'", mods);
			if (results.size()==0)
			{
				karmaObj = new KarmaObject();
				karmaObj.string = name;
			}
			else
				karmaObj = results.get(0);

			karmaObj.value = val;
			karmaObjs.add(karmaObj);
		}

		if (karmaObjs.size()==0)
		{
			irc.sendContextReply(mes, "You need to specify some bobdamn karma to set!");
			return;
		}

		saveKarmaObjects(karmaObjs, mods);

		StringBuffer output = new StringBuffer("Karma adjustment");
		output.append(karmaObjs.size() == 1 ? "" : "s");
		output.append(": ");
		for (int i=0; i<karmaObjs.size(); i++)
		{
			output.append(names.get(i));
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

	public String[] helpCommandList = {
		"Get a list of all karma objects.",
	};
	public void commandList (Message mes, Modules mods, IRCInterface irc)
	{
		irc.sendContextReply(mes, "No chance, matey.");
	}

	public void webList(PrintWriter out, String params, String[] user) throws ChoobException
	{
		out.println("HTTP/1.0 200 OK");
		out.println("Content-Type: text/html");
		out.println();

		List<KarmaObject> res = retrieveKarmaObjects("WHERE 1 SORT INTEGER value", mods);


		out.println(res.size());
		for(KarmaObject karmaObject: res)
		{
			if (karmaObject == null)
				out.println("NULL.<br/>");
			else
				out.println("" + karmaObject.id + ": " + karmaObject.string + " => " + karmaObject.value + ".<br/>");
		}
	}


}

