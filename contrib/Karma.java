import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

private class KarmaObject
{
	public int id;
	public String string;
	public int up;
	public int down;
	public int value;
	public boolean dup;
}

private class KarmaReasonObject
{
	public int id;
	public String string;
	public int up;
	public String reason;
}

class Karma
{
	// Java-- A better regex would be: (")?((?(1)(?:[ \\./a-zA-Z0-9_-]{2,})|(?:[\\./a-zA-Z0-9_-]{2,})))(?(1)")((?:\\+\\+)|(?:\\-\\-))
	public String filterKarmaRegex = ".*?(?:((?:[\\./a-zA-Z0-9_-]{3,})|(?:^[\\./a-zA-Z0-9_-]+))((?:\\+\\+)|(?:\\-\\-))).*?";

	public void filterKarma( Message con, Modules mods, IRCInterface irc )
	{
		Pattern pa;
		Matcher ma;

		Vector /*bwhaha*/ kos = new Vector();
		HashSet used = new HashSet();

		pa = Pattern.compile(filterKarmaRegex);
		ma = pa.matcher(con.getMessage());

		while (ma.find())
		{

			KarmaObject ko;
			String st=ma.group(1);
			if (used.contains(st))
				continue;
			used.add(st);
			boolean dup=ma.group(2).equals("++");

			List results = mods.odb.retrieve (KarmaObject.class, "string = '" + st + "'");
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
				mods.odb.delete(results.get(0)); // <-- ohnoes, errors cause badness.
			}
			ko.dup=dup;
			kos.add(ko);
		}
		//t+=ma.group(1) + " (" + ( ma.group(2).equals("++") ? "up" : "down") + "), ";

		if (kos.size()==1)
		{
			irc.sendContextReply(con, (kos.get(0).dup ? "Karma" : "Less karma") + " to " + kos.get(0).string + ", " + (kos.get(0).dup ? "giving" : "leaving") + " a karma of " + kos.get(0).value + ".");
			mods.odb.save(kos.get(0));
			return;
		}

		String t="Karma adjustments: ";

		for (int i=0; i<kos.size(); i++)
		{
			mods.odb.save(kos.get(i));
			t+=kos.get(i).string + ( kos.get(i).dup ? "++" : "--") + " (now " + kos.get(i).value + ")";
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
}

