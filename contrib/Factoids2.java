import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;

public class Factoid
{
	public Factoid()
	{
	}
	
	public Factoid(String subject, boolean fact, String info, long date)
	{
		this.subject = subject;
		this.fact = fact;
		this.info = info;
		this.date = date;
	}
	
	public int id;
	public String subject;
	public boolean fact;
	public String info;
	public long date;
}

public class Factoids2
{
	public String[] info()
	{
		return new String[] {
			"Stores, recalls and collects facts and rumours.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}
	
	private Modules mods;
	private IRCInterface irc;
	private Pattern filterFactoidsPattern;
	
	public Factoids2(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
		filterFactoidsPattern = Pattern.compile(filterFactoidsRegex);
	}
	
	// Collect and store rumours for things.
	public String filterFactoidsRegex = "(\\w{4,})\\s+(?:is|was)\\s+(.{4,})";
	
	public void filterFactoids(Message mes) throws ChoobException
	{
		Matcher rMatcher = filterFactoidsPattern.matcher(mes.getMessage());
		if (rMatcher.matches()) {
			String subject = rMatcher.group(1).toLowerCase();
			String defn = rMatcher.group(2);
			Factoid rumour = new Factoid(subject, false, defn, mes.getMillis());
			mods.odb.save(rumour);
			System.out.println("FACTOIDS2: Added new rumour for '" + subject + "' of '" + defn + "'");
			
			// Remove oldest so we only have the 5 most recent.
			List<Factoid> results = mods.odb.retrieve(Factoid.class, "WHERE fact = 0 AND subject = '" + mods.odb.escapeString(subject) + "' SORT DESC date");
			
			for (int i = 5; i < results.size(); i++) {
				Factoid oldRumour = (Factoid)results.get(i);
				mods.odb.delete(oldRumour);
				System.out.println("FACTOIDS2: Removed old rumour for '" + subject + "' of '" + oldRumour.info + "'.");
			}
		}
	}
	
	public String[] helpCommandAdd = {
			"Add a new factual definition for a term.",
			"<term> <defn>",
			"<term> is the term to which the definition applies",
			"<defn> is the definition itself"
		};
	
	public void commandAdd(Message mes)
	{
		String[] params = mods.util.getParamArray(mes, 2);
		
		if (params.length <= 2) {
			irc.sendContextReply(mes, "Syntax: 'Factoids2.Add " + helpCommandAdd[1] + "'");
			return;
		}
		
		String subject = params[1].toLowerCase();
		String defn = params[2];
		Factoid fact = new Factoid(subject, true, defn, mes.getMillis());
		mods.odb.save(fact);
		System.out.println("FACTOIDS2: Added new fact for '" + subject + "' of '" + defn + "'");
		
	}
}