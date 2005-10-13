import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

public class KarmaObject
{
	public int id;
	public String string;
	public int up;
	public int down;
	public int value;
	boolean dup;
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
	// Java-- A better regex would be: (")?((?(1)(?:[ \\./a-zA-Z0-9_-]{2,})|(?:[\\./a-zA-Z0-9_-]{2,})))(?(1)")((?:\\+\\+)|(?:\\-\\-))
	public String filterKarmaRegex = "\\b(?:((?:[\\./a-zA-Z0-9_-]{3,})|(?:^[\\./a-zA-Z0-9_-]+))((?:\\+\\+)|(?:\\-\\-)))\\B";

	public void commandReason( Message con, Modules mods, IRCInterface irc )
	{
		Pattern pa;
		Matcher ma;

		pa=Pattern.compile(".*(?:(?:do)|(?:does))? ([\\./a-zA-Z0-9_-]+) ((?:suck)|(?:rulz0r))\\?$");
		ma=pa.matcher(con.getMessage());
		if (ma.matches())
		{
			List<KarmaReasonObject> results;
			try
			{
				results=mods.odb.retrieve(KarmaReasonObject.class, "WHERE string = '" + ma.group(1) + "' AND direction = '" + (ma.group(2).equals("suck") ? -1 : 1) + "'");
			}
			catch (ChoobException e) { return; }
			if (results.size()==0)
				return;

			KarmaReasonObject kr=results.get((new Random()).nextInt(results.size()));

			irc.sendContextReply(con, kr.string + " " + ma.group(2) + (kr.string.endsWith("s") ? "" : "s") + " " + kr.reason);
			return;
		}
	}

	public void filterKarma( Message con, Modules mods, IRCInterface irc )
	{
		String special="";

		Pattern pa;
		Matcher ma;

		ArrayList<KarmaObject> kos = new ArrayList();
		HashSet used = new HashSet();

		pa=Pattern.compile("^([\\./a-zA-Z0-9_-]+)((?:\\+\\+)|(?:\\-\\-)) (because .+)$");
		ma = pa.matcher(con.getMessage());

		if (ma.matches())
		{
			KarmaReasonObject kr=new KarmaReasonObject();
			kr.string=ma.group(1);
			kr.direction=(ma.group(2).equals("++") ? 1 : -1);
			kr.reason=ma.group(3);
			try { mods.odb.save(kr); } catch (ChoobException e) {}
			special="*";
		}

		pa = Pattern.compile(filterKarmaRegex);
		ma = pa.matcher(con.getMessage());



		while (ma.find())
		{
			KarmaObject ko;
			String st=ma.group(1);
			if (used.contains(st.toLowerCase()))
				continue;
			used.add(st.toLowerCase());

			boolean dup=ma.group(2).equals("++");

			List<KarmaObject> results = retrieveKarmaObjects("WHERE string = '" + st + "'", mods);
			if (results.size()==0)
			{
				ko=new KarmaObject();
				ko.string=st;
				if (dup)
				{
					ko.up=1;
					ko.value=1;
				}
				else
				{
					ko.down=1;
					ko.value=-1;
				}
			}
			else
			{
				ko=results.get(0);
				if (dup)
				{
					ko.up++;
					ko.value++;
				}
				else
				{
					ko.down++;
					ko.value--;
				}
			}
			ko.dup=dup;
			kos.add(ko);
		}

		saveKarmaObjects(kos, mods);


		// Generate a pretty reply, all actual processing is done now:

		if (kos.size()==1)
		{
			irc.sendContextReply(con, (kos.get(0).dup ? "Karma" : "Less karma") + " to " + kos.get(0).string + special + ", " + (kos.get(0).dup ? "giving" : "leaving") + " a karma of " + kos.get(0).value + ".");
			return;
		}

		String t="Karma adjustments: ";

		for (int i=0; i<kos.size(); i++)
		{

			t+=kos.get(i).string + ( kos.get(i).dup ? " up" : " down") + " (now " + kos.get(i).value + ")";
			if (i!=kos.size()-1)
			{
				if (i==kos.size()-2)
					t+=" and ";
				else
					t+=", ";
			}
		}
		irc.sendContextReply( con, t + ".");

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


	private void commandScores(Message con, Modules mods, IRCInterface irc, boolean asc)
	{
		List<KarmaObject> kos=retrieveKarmaObjects("SORT " + (asc ? "ASC" : "DESC") + " INTEGER value LIMIT (5)", mods);

		String t="High Scores: ";

		for (int i=0; i<kos.size(); i++)
		{

			t+=(i+1) + postfix(i+1) + ": " + kos.get(i).string + " (with " + kos.get(i).value + ")";
			if (i!=kos.size()-1)
			{
				if (i==kos.size()-2)
					t+=" and ";
				else
					t+=", ";
			}
		}
		irc.sendContextReply( con, t + ".");
	}

	public void commandHighScores(Message con, Modules mods, IRCInterface irc)
	{
		commandScores(con,mods,irc,false);
	}

	public void commandLowScores(Message con, Modules mods, IRCInterface irc)
	{
		commandScores(con,mods,irc,true);
	}


	private void saveKarmaObjects (ArrayList<KarmaObject> kos, Modules mods)
	{
		try
		{
			for (int i=0; i<kos.size(); i++)
				if (kos.get(i).id==0)
					mods.odb.save(kos.get(i));
				else
					mods.odb.update(kos.get(i));
		}
		catch (ChoobException e)
		{
			e.printStackTrace();
		}
	}

	private List<KarmaObject> retrieveKarmaObjects(String clause, Modules mods)
	{
		try
		{
			return mods.odb.retrieve(KarmaObject.class, clause);
		}
		catch (ChoobException e) { return new ArrayList<KarmaObject>(); }
	}

	public void commandGet (Message con, Modules mods, IRCInterface irc)
	{
		List<String> params = mods.util.getParams( con );
		ArrayList<KarmaObject> kos=new ArrayList();
		if (params.size()>1)
			for (int i=1; i<params.size(); i++)
			{
				String st=(String)params.get(i);
				List<KarmaObject> results=retrieveKarmaObjects("WHERE string = '" + st + "'", mods);
				if (results.size()!=0)
					kos.add((KarmaObject)results.get(0));
				else
				{
					KarmaObject ko=new KarmaObject();
					ko.string=st;
					ko.value=0;
					kos.add(ko);
				}
			}

		if (kos.size()==1)
		{
			irc.sendContextReply(con, kos.get(0).string + " has a karma of " + kos.get(0).value + ".");
			return;
		}

		String t="Karmas: ";

		for (int i=0; i<kos.size(); i++)
		{

			t+=kos.get(i).string + ": " + kos.get(i).value;
			if (i!=kos.size()-1)
			{
				if (i==kos.size()-2)
					t+=" and ";
				else
					t+=", ";
			}
		}
		irc.sendContextReply( con, t + ".");

	}

	public void commandSet( Message con, Modules mods, IRCInterface irc )
	{
		if (!mods.security.hasPerm(new ChoobPermission("plugins.karma.set"), con.getNick()))
		{
			irc.sendContextReply(con, "You lack authority!");
			return;
		}

		List params = mods.util.getParams( con );
		ArrayList<KarmaObject> kos=new ArrayList();

		for (int i=1; i<params.size(); i++)
		{
			KarmaObject ko;
			String p=(String)params.get(i);
			System.out.println(i);
			String[] items=p.split("=");
			String st=items[0].trim();
			int val=Integer.parseInt((String)items[1]);

			List<KarmaObject> results = retrieveKarmaObjects("WHERE string = '" + st + "'", mods);
			if (results.size()==0)
			{
				ko=new KarmaObject();
				ko.string=st;
			}
			else
				ko=results.get(0);

			ko.value=val;
			kos.add(ko);
		}

		if (kos.size()==0)
			return;

		saveKarmaObjects(kos, mods);

		String t="Karma adjustment" + (kos.size() == 1 ? "" : "s") + ": ";
		for (int i=0; i<kos.size(); i++)
		{

			t+=kos.get(i).string + ": now " + kos.get(i).value;
			if (i!=kos.size()-1)
			{
				if (i==kos.size()-2)
					t+=" and ";
				else
					t+=", ";
			}
		}
		irc.sendContextReply( con, t + ".");

	}

	public void commandList (Message con, Modules mods, IRCInterface irc)
	{
		irc.sendContextReply(con, "No chance, matey.");
	}
}

