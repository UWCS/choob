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
	
	public Factoid(String subject, boolean fact, String info)
	{
		this.subject = subject;
		this.fact = fact;
		this.info = info;
		this.date = System.currentTimeMillis();
	}
	
	public int id;
	public String subject;
	public boolean fact;
	public String info;
	public long date;
}

public class FactoidEnumerator
{
	public FactoidEnumerator()
	{
	}
	
	public FactoidEnumerator(String enumSource, int index, int count)
	{
		this.enumSource = enumSource;
		this.index = index;
		this.count = count;
		this.lastUsed = System.currentTimeMillis();
	}
	
	public int getNext()
	{
		index++;
		if (index >= count) {
			index = 0;
		}
		lastUsed = System.currentTimeMillis();
		return index;
	}
	
	public int id;
	public String enumSource;
	public int index;
	public int count;
	public long lastUsed;
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
	
	private static long ENUM_TIMEOUT = 5 * 60 * 1000; // 5 minutes
	private final static Set<String> subjectExclusions = new HashSet<String>();
	static
	{
		exceptions.add("that");
		exceptions.add("this");
		exceptions.add("what");
		exceptions.add("which");
	}
	
	private Modules mods;
	private IRCInterface irc;
	private Pattern filterFactoidsPattern;
	
	public Factoids2(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
		filterFactoidsPattern = Pattern.compile(filterFactoidsRegex);
		
		mods.interval.callBack(null, 60000, 1);
	}
	
	private int countFacts(List<Factoid> definitions)
	{
		int factCount = 0;
		for (int i = 0; i < definitions.size(); i++) {
			Factoid defn = definitions.get(i);
			if (defn.fact) {
				factCount++;
			}
		}
		return factCount;
	}
	
	private int countRumours(List<Factoid> definitions)
	{
		int rumourCount = 0;
		for (int i = 0; i < definitions.size(); i++) {
			Factoid defn = definitions.get(i);
			if (!defn.fact) {
				rumourCount++;
			}
		}
		return rumourCount;
	}
	
	private Factoid pickDefinition(List<Factoid> definitions, String enumSource)
	{
		if (countFacts(definitions) > 0)
			return pickFact(definitions, enumSource);
		return pickRumour(definitions, enumSource);
	}
	
	private Factoid pickFact(List<Factoid> definitions, String enumSource)
	{
		return pickDefinition(definitions, true, countFacts(definitions), enumSource);
	}
	
	private Factoid pickRumour(List<Factoid> definitions, String enumSource)
	{
		return pickDefinition(definitions, false, countRumours(definitions), enumSource);
	}
	
	private Factoid pickDefinition(List<Factoid> definitions, boolean fact, int count, String enumSource)
	{
		int index = -1;
		enumSource = enumSource.toLowerCase();
		List<FactoidEnumerator> enums = mods.odb.retrieve(FactoidEnumerator.class, "WHERE enumSource = '" + mods.odb.escapeString(enumSource) + "'");
		FactoidEnumerator fEnum = null;
		if (enums.size() >= 1) {
			fEnum = enums.get(0);
			if (fEnum.count != count) {
				// Count has changed: invalidated!
				mods.odb.delete(fEnum);
				fEnum = null;
			} else {
				// Alright, step to the next one.
				index = fEnum.getNext();
				mods.odb.update(fEnum);
			}
		}
		if (fEnum == null) {
			// No enumerator, pick random start and create one.
			index = (int)Math.floor(Math.random() * count);
			fEnum = new FactoidEnumerator(enumSource, index, count);
			mods.odb.save(fEnum);
		}
		
		Factoid rvDefn = null;
		for (int i = 0; i < definitions.size(); i++) {
			Factoid defn = definitions.get(i);
			if (defn.fact == fact) {
				if (index == 0) {
					rvDefn = defn;
					break;
				}
				index--;
			}
		}
		return rvDefn;
	}
	
	private List<Factoid> getDefinitions(String subject, String search)
	{
		subject = subject.toLowerCase();
		String odbQuery = "WHERE subject = '" + mods.odb.escapeString(subject) + "'";
		
		if (search.length() > 0) {
			if (search.startsWith("/") && search.endsWith("/")) {
				// Regexp
				odbQuery += " AND info RLIKE \"" + mods.odb.escapeString(search.substring(1, search.length() - 1)) + "\"";
			} else {
				// Substring
				odbQuery += " AND info LIKE \"%" + mods.odb.escapeString(search) + "%\"";
			}
		}
		return mods.odb.retrieve(Factoid.class, odbQuery);
	}
	
	// Interval
	public void interval(Object param)
	{
		// Clean up dead enumerators.
		long lastUsedCutoff = System.currentTimeMillis() - ENUM_TIMEOUT;
		List<FactoidEnumerator> deadEnums = mods.odb.retrieve(FactoidEnumerator.class, "WHERE lastUsed < " + lastUsedCutoff);
		for (int i = 0; i < deadEnums.size(); i++) {
			mods.odb.delete(deadEnums.get(i));
		}
		
		mods.interval.callBack(null, 60000, 1);
	}
	
	// Collect and store rumours for things.
	public String filterFactoidsRegex = "(\\w{4,})\\s+((?:is|was)\\s+.{4,})";
	
	public void filterFactoids(Message mes) throws ChoobException
	{
		Matcher rMatcher = filterFactoidsPattern.matcher(mes.getMessage());
		if (rMatcher.matches()) {
			String subject = rMatcher.group(1).toLowerCase();
			String defn = rMatcher.group(2);
			
			// We don't auto-learn about some things.
			if (subjectExclusions.contains(subject))
				return;
			
			Factoid rumour = new Factoid(subject, false, defn);
			mods.odb.save(rumour);
			
			// Remove oldest so we only have the 5 most recent.
			List<Factoid> results = mods.odb.retrieve(Factoid.class, "WHERE fact = 0 AND subject = '" + mods.odb.escapeString(subject) + "' SORT DESC date");
			
			for (int i = 5; i < results.size(); i++) {
				Factoid oldRumour = (Factoid)results.get(i);
				mods.odb.delete(oldRumour);
			}
		}
	}
	
	// Manually add facts to the system.
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
		irc.sendContextReply(mes, "Added definition for '" + subject + "'.");
	}
	
	// Remove facts from the system.
	public String[] helpCommandRemove = {
			"Remove a definition (both facts and rumours).",
			"<term> [<search>]",
			"<term> is the term to remove",
			"<search> limits the removal to only matching defintions, if multiple ones exist (substring or regexp allowed)"
		};
	
	public void commandRemove(Message mes)
	{
		String[] params = mods.util.getParamArray(mes, 2);
		
		if (params.length <= 1) {
			irc.sendContextReply(mes, "Syntax: 'Factoids2.Remove " + helpCommandRemove[1] + "'");
			return;
		}
		
		List<Factoid> removals = getDefinitions(params[1], (params.length > 2 ? params[2] : ""));
		
		if (removals != null) {
		for (int i = 0; i < removals.size(); i++) {
			Factoid defn = (Factoid)removals.get(i);
			mods.odb.delete(defn);
		}
		if (removals.size() > 1) {
			irc.sendContextReply(mes, removals.size() + " definitions for '" + params[1] + "' removed.");
		} else if (removals.size() == 1) {
			irc.sendContextReply(mes, "1 definition for '" + params[1] + "' removed.");
		} else {
			irc.sendContextReply(mes, "No definitions for '" + params[1] + "' found.");
		}
		}
	}
	
	// Retrieve definitions from the system.
	public String[] helpCommandGet = {
			"Returns a/the definition for a term.",
			"<term> [<search>]",
			"<term> is the term to define",
			"<search> limits the definition(s) given, if multiple ones exist (substring or regexp allowed)"
		};
	
	public void commandGet(Message mes) throws ChoobException
	{
		String[] params = mods.util.getParamArray(mes, 2);
		
		if (params.length <= 1) {
			irc.sendContextReply(mes, "Syntax: 'Factoids2.Get " + helpCommandGet[1] + "'");
			return;
		}
		
		List<Factoid> definitions = getDefinitions(params[1], (params.length > 2 ? params[2] : ""));
		
		if (definitions.size() == 0) {
			irc.sendContextReply(mes, "Sorry, I don't know anything about '" + params[1] + "'!");
			
		} else {
			int factCount = countFacts(definitions);
			int rumourCount = countRumours(definitions);
			
			if (factCount > 0) {
				Factoid fact = pickFact(definitions, mes.getContext() + ":" + params[1]);
				
				if (factCount > 2) {
					irc.sendContextReply(mes, fact.subject + " " + fact.info + " (" + (factCount - 1) + " other defns)");
				} else if (factCount == 2) {
					irc.sendContextReply(mes, fact.subject + " " + fact.info + " (1 other defn)");
				} else {
					irc.sendContextReply(mes, fact.subject + " " + fact.info);
				}
				
			} else {
				Factoid rumour = pickRumour(definitions, mes.getContext() + ":" + params[1]);
				
				if (rumourCount > 2) {
					irc.sendContextReply(mes, "Rumour has it " + rumour.subject + " " + rumour.info + " (" + (rumourCount - 1) + " other rumours)");
				} else if (rumourCount == 2) {
					irc.sendContextReply(mes, "Rumour has it " + rumour.subject + " " + rumour.info + " (1 other rumour)");
				} else {
					irc.sendContextReply(mes, "Rumour has it " + rumour.subject + " " + rumour.info);
				}
			}
		}
	}
	
	public String[] helpCommandGetFact = {
			"Returns a/the factual definition for a term.",
			"<term> [<search>]",
			"<term> is the term to define",
			"<search> limits the definition(s) given, if multiple ones exist (substring or regexp allowed)"
		};
	
	public void commandGetFact(Message mes) throws ChoobException
	{
		String[] params = mods.util.getParamArray(mes, 2);
		
		if (params.length <= 1) {
			irc.sendContextReply(mes, "Syntax: 'Factoids2.GetFact " + helpCommandGetFact[1] + "'");
			return;
		}
		
		List<Factoid> definitions = getDefinitions(params[1], (params.length > 2 ? params[2] : ""));
		
		if (definitions.size() == 0) {
			irc.sendContextReply(mes, "Sorry, I don't know anything about '" + params[1] + "'!");
			
		} else {
			int factCount = countFacts(definitions);
			
			if (factCount > 0) {
				Factoid fact = pickFact(definitions, mes.getContext() + ":" + params[1]);
				
				if (factCount > 2) {
					irc.sendContextReply(mes, fact.subject + " " + fact.info + " (" + (factCount - 1) + " other defns)");
				} else if (factCount == 2) {
					irc.sendContextReply(mes, fact.subject + " " + fact.info + " (1 other defn)");
				} else {
					irc.sendContextReply(mes, fact.subject + " " + fact.info);
				}
				
			} else {
				irc.sendContextReply(mes, "Sorry, I don't have any facts about '" + params[1] + "'.");
			}
		}
	}
	
	public String[] helpCommandGetRumour = {
			"Returns a/the definition for a term.",
			"<term> [<search>]",
			"<term> is the term to define",
			"<search> limits the definition(s) given, if multiple ones exist (substring or regexp allowed)"
		};
	
	public void commandGetRumour(Message mes) throws ChoobException
	{
		String[] params = mods.util.getParamArray(mes, 2);
		
		if (params.length <= 1) {
			irc.sendContextReply(mes, "Syntax: 'Factoids2.GetRumour " + helpCommandGetRumour[1] + "'");
			return;
		}
		
		List<Factoid> definitions = getDefinitions(params[1], (params.length > 2 ? params[2] : ""));
		
		if (definitions.size() == 0) {
			irc.sendContextReply(mes, "Sorry, I don't know anything about '" + params[1] + "'!");
			
		} else {
			int rumourCount = countRumours(definitions);
			
			if (rumourCount > 0) {
				Factoid rumour = pickRumour(definitions, mes.getContext() + ":" + params[1]);
				
				if (rumourCount > 2) {
					irc.sendContextReply(mes, "Rumour has it " + rumour.subject + " " + rumour.info + " (" + (rumourCount - 1) + " other rumours)");
				} else if (rumourCount == 2) {
					irc.sendContextReply(mes, "Rumour has it " + rumour.subject + " " + rumour.info + " (1 other rumour)");
				} else {
					irc.sendContextReply(mes, "Rumour has it " + rumour.subject + " " + rumour.info);
				}
				
			} else {
				irc.sendContextReply(mes, "Sorry, I don't have any rumours about '" + params[1] + "'.");
			}
		}
	}
}
