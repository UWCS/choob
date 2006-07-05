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
	
	public Factoid(String subject, boolean fact, String info, String nick)
	{
		this.subject = subject;
		this.fact = fact;
		this.info = info;
		this.nick = nick;
		this.reads = 0;
		this.date = System.currentTimeMillis();
	}
	
	public int id;
	public String subject;
	public boolean fact;
	public String info;
	public String nick;
	public int reads;
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

class FactoidParsed
{
	public FactoidParsed(String subject, String definition)
	{
		this.subject = subject;
		this.definition = definition;
	}
	
	public String subject;
	public String definition;
}

class FactoidSearch
{
	public FactoidSearch(String subject)
	{
		this.subject = subject;
		this.search = "";
	}
	
	public FactoidSearch(String subject, String search)
	{
		this.subject = subject;
		this.search = search;
		if (this.search == null)
			this.search = "";
	}
	
	public String subject;
	public String search;
}

class FactoidSearchData
{
	public FactoidSearch search;
	public List<Factoid> definitions;
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
	
	// Subjects to not auto-learn from.
	private final static Set<String> subjectExclusions = new HashSet<String>();
	static
	{
		subjectExclusions.add("also");
		subjectExclusions.add("cant");
		subjectExclusions.add("could");
		subjectExclusions.add("dont");
		subjectExclusions.add("just");
		subjectExclusions.add("might");
		subjectExclusions.add("must");
		subjectExclusions.add("should");
		subjectExclusions.add("something");
		subjectExclusions.add("someone");
		subjectExclusions.add("that");
		subjectExclusions.add("there");
		subjectExclusions.add("they");
		subjectExclusions.add("this");
		subjectExclusions.add("thing");
		subjectExclusions.add("what");
		subjectExclusions.add("where");
		subjectExclusions.add("when");
		subjectExclusions.add("which");
		subjectExclusions.add("will");
		subjectExclusions.add("would");
	}
	
	private final static String splitWords = "is|was|am|be|can|can't|cant|cannot";
	
	private Modules mods;
	private IRCInterface irc;
	private Pattern filterFactoidsPattern;
	private Pattern addDefinitionPattern;
	private Pattern quotedSearchPattern;
	private Pattern regexpSearchPattern;
	
	public Factoids2(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
		filterFactoidsPattern = Pattern.compile(filterFactoidsRegex);
		addDefinitionPattern = Pattern.compile("\\s*(?i:that\\s+)?(.+?)\\s+((?i:" + splitWords + ")\\s+.+)\\s*");
		quotedSearchPattern = Pattern.compile("\\s*\"([^\"]+)\"(?:\\s+(.*))?\\s*");
		regexpSearchPattern = Pattern.compile("\\s*(.+?)\\s+(/([^/]+|\\/)+/)\\s*");
		
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
	
	private List<Factoid> getDefinitions(FactoidSearch factoidSearch)
	{
		String subject = factoidSearch.subject.toLowerCase();
		String search = factoidSearch.search;
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
	
	// Get a subject + definition from the message params.
	private FactoidParsed getParsedFactoid(Message mes)
	{
		String[] params1 = mods.util.getParamArray(mes, 2);
		
		if (params1.length <= 2) {
			return null;
		}
		
		String[] params2 = mods.util.getParamArray(mes, 1);
		
		// If they have used is/are, split on that.
		Matcher mIsAre = addDefinitionPattern.matcher(params2[1]);
		if (mIsAre.matches()) {
			return new FactoidParsed(mIsAre.group(1), mIsAre.group(2));
		}
		
		// Default to first word + rest split.
		return new FactoidParsed(params1[1], params1[2]);
	}
	
	// Get a subject + optional search from the message params.
	private FactoidSearch getFactoidSearch(Message mes)
	{
		String[] params1 = mods.util.getParamArray(mes, 2);
		
		if (params1.length == 2) {
			return new FactoidSearch(params1[1]);
		}
		if (params1.length <= 1) {
			return null;
		}
		
		String[] params2 = mods.util.getParamArray(mes, 1);
		
		// If they have used quotes, split on that.
		Matcher mQuoted = quotedSearchPattern.matcher(params2[1]);
		if (mQuoted.matches()) {
			return new FactoidSearch(mQuoted.group(1), mQuoted.group(2));
		}
		
		// If there's a regexp search, split on that.
		Matcher mRegExp = regexpSearchPattern.matcher(params2[1]);
		if (mRegExp.matches()) {
			return new FactoidSearch(mRegExp.group(1), mRegExp.group(2));
		}
		
		// If they have used is/are, split on that.
		Matcher mIsAre = addDefinitionPattern.matcher(params2[1]);
		if (mIsAre.matches()) {
			return new FactoidSearch(mIsAre.group(1), mIsAre.group(2));
		}
		
		// Default to first word + rest split.
		return new FactoidSearch(params1[1], params1[2]);
	}
	
	private FactoidSearchData getFactoidSearchDefinitions(Message mes)
	{
		FactoidSearchData data = new FactoidSearchData();
		
		// Try to parse the params into search data.
		data.search = getFactoidSearch(mes);
		if (data.search == null) {
			// Gah, didn't parse at all!
			return null;
		}
		// Get matching definitions while we're here.
		data.definitions = getDefinitions(data.search);
		
		if (data.definitions.size() == 0) {
			// Nothing with the normal parser, try the entire thing quoted just in case.
			String[] params = mods.util.getParamArray(mes, 1);
			FactoidSearch search = new FactoidSearch(params[1]);
			List<Factoid> definitions = getDefinitions(search);
			
			// Something did match when quoted - pretend that's what the user asked for originally.
			if (definitions.size() > 0) {
				data.search = search;
				data.definitions = definitions;
			}
		}
		return data;
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
	public String filterFactoidsRegex = "(\\w{4,})\\s+((?i:" + splitWords + ")\\s+.{4,})";
	
	public void filterFactoids(Message mes) throws ChoobException
	{
		Matcher rMatcher = filterFactoidsPattern.matcher(mes.getMessage());
		if (rMatcher.matches()) {
			String subject = rMatcher.group(1).toLowerCase();
			String defn = rMatcher.group(2);
			
			// We don't auto-learn about some things.
			if (subjectExclusions.contains(subject))
				return;
			
			Factoid rumour = new Factoid(subject, false, defn, mes.getNick());
			mods.odb.save(rumour);
			
			// Remove oldest so we only have the 5 most recent.
			List<Factoid> results = mods.odb.retrieve(Factoid.class, "WHERE fact = 0 AND subject = '" + mods.odb.escapeString(subject) + "' SORT DESC date");
			
			for (int i = 5; i < results.size(); i++) {
				Factoid oldRumour = (Factoid)results.get(i);
				mods.odb.delete(oldRumour);
			}
		}
	}
	
	// Get some simple stats.
	public String[] helpCommandStats = {
			"Returns some (maybe) interesting statistics from the factoids system.",
			"[<term> [" + splitWords + "] [<search>]]",
			"<term> is the term to get stats for",
			"<search> limits the removal to only matching defintions, if multiple ones exist (substring or regexp allowed)"
		};
	
	public void commandStats(Message mes)
	{
		FactoidSearchData data = getFactoidSearchDefinitions(mes);
		
		if (data == null) {
			data = new FactoidSearchData();
			data.definitions = mods.odb.retrieve(Factoid.class, "");
		}
		
		if ((data.search != null) && (data.definitions.size() == 1)) {
			Factoid defn = data.definitions.get(0);
			irc.sendContextReply(mes, "Factoid for '" + data.search.subject +
					"' is a " + (defn.fact ? "fact" : "rumour") +
					(defn.fact ? " added by " : " collected from ") + defn.nick +
					" and displayed " + defn.reads + " time" + (defn.reads != 1 ? "s":"") + ".");
			
		} else {
			int factCount   = countFacts(data.definitions);
			int rumourCount = countRumours(data.definitions);
			
			if (data.search != null) {
				irc.sendContextReply(mes, data.definitions.size() +
						" factoid" + (data.definitions.size() != 1 ? "s":"") +
						" matched, containing " + factCount + " fact" + (factCount != 1 ? "s":"") +
						" and " + rumourCount + " rumour" + (rumourCount != 1 ? "s":"") + ".");
			} else {
				irc.sendContextReply(mes, "Factoids database contains " + factCount + " fact" + (factCount != 1 ? "s":"") + " and " + rumourCount + " rumour" + (rumourCount != 1 ? "s":"") + ".");
			}
		}
	}
	
	// Manually add facts to the system.
	public String[] helpCommandAdd = {
			"Add a new factual definition for a term.",
			"<term> [" + splitWords + "] <defn>",
			"<term> is the term to which the definition applies",
			"<defn> is the definition itself"
		};
	
	public void commandAdd(Message mes)
	{
		FactoidParsed factoid = getParsedFactoid(mes);
		
		if (factoid == null) {
			irc.sendContextReply(mes, "Syntax: 'Factoids2.Add " + helpCommandAdd[1] + "'");
			return;
		}
		
		Factoid fact = new Factoid(factoid.subject, true, factoid.definition, mes.getNick());
		mods.odb.save(fact);
		irc.sendContextReply(mes, "Added definition for '" + factoid.subject + "'.");
	}
	
	// Remove facts from the system.
	public String[] helpCommandRemove = {
			"Remove a definition (both facts and rumours).",
			"<term> [" + splitWords + "] [<search>]",
			"<term> is the term to remove",
			"<search> limits the removal to only matching defintions, if multiple ones exist (substring or regexp allowed)"
		};
	
	public void commandRemove(Message mes)
	{
		FactoidSearchData data = getFactoidSearchDefinitions(mes);
		
		if (data == null) {
			irc.sendContextReply(mes, "Syntax: 'Factoids2.Remove " + helpCommandRemove[1] + "'");
			return;
		}
		
		for (int i = 0; i < data.definitions.size(); i++) {
			Factoid defn = data.definitions.get(i);
			mods.odb.delete(defn);
		}
		if (data.definitions.size() > 1) {
			irc.sendContextReply(mes, data.definitions.size() + " definitions for '" + data.search.subject + "' removed.");
		} else if (data.definitions.size() == 1) {
			irc.sendContextReply(mes, "1 definition for '" + data.search.subject + "' removed.");
		} else {
			irc.sendContextReply(mes, "No definitions for '" + data.search.subject + "' found.");
		}
	}
	
	// Retrieve definitions from the system.
	public String[] helpCommandGet = {
			"Returns a definition for a term.",
			"<term> [" + splitWords + "] [<search>]",
			"<term> is the term to define",
			"<search> limits the definition(s) given, if multiple ones exist (substring or regexp allowed)"
		};
	
	public void commandGet(Message mes) throws ChoobException
	{
		FactoidSearchData data = getFactoidSearchDefinitions(mes);
		
		if (data == null) {
			irc.sendContextReply(mes, "Syntax: 'Factoids2.Get " + helpCommandGet[1] + "'");
			return;
		}
		
		if (data.definitions.size() == 0) {
			irc.sendContextReply(mes, "Sorry, I don't know anything about '" + data.search.subject + "'!");
			
		} else {
			int factCount = countFacts(data.definitions);
			int rumourCount = countRumours(data.definitions);
			
			if (factCount > 0) {
				Factoid fact = pickFact(data.definitions, mes.getContext() + ":" + data.search.subject);
				fact.reads++;
				mods.odb.update(fact);
				
				if (factCount > 2) {
					irc.sendContextReply(mes, fact.subject + " " + fact.info + " (" + (factCount - 1) + " other defns)");
				} else if (factCount == 2) {
					irc.sendContextReply(mes, fact.subject + " " + fact.info + " (1 other defn)");
				} else {
					irc.sendContextReply(mes, fact.subject + " " + fact.info);
				}
				
			} else {
				Factoid rumour = pickRumour(data.definitions, mes.getContext() + ":" + data.search.subject);
				rumour.reads++;
				mods.odb.update(rumour);
				
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
			"Returns a factual definition for a term.",
			"<term> [" + splitWords + "] [<search>]",
			"<term> is the term to define",
			"<search> limits the definition(s) given, if multiple ones exist (substring or regexp allowed)"
		};
	
	public void commandGetFact(Message mes) throws ChoobException
	{
		FactoidSearchData data = getFactoidSearchDefinitions(mes);
		
		if (data == null) {
			irc.sendContextReply(mes, "Syntax: 'Factoids2.GetFact " + helpCommandGetFact[1] + "'");
			return;
		}
		
		if (data.definitions.size() == 0) {
			irc.sendContextReply(mes, "Sorry, I don't know anything about '" + data.search.subject + "'!");
			
		} else {
			int factCount = countFacts(data.definitions);
			
			if (factCount > 0) {
				Factoid fact = pickFact(data.definitions, mes.getContext() + ":" + data.search.subject);
				fact.reads++;
				mods.odb.update(fact);
				
				if (factCount > 2) {
					irc.sendContextReply(mes, fact.subject + " " + fact.info + " (" + (factCount - 1) + " other defns)");
				} else if (factCount == 2) {
					irc.sendContextReply(mes, fact.subject + " " + fact.info + " (1 other defn)");
				} else {
					irc.sendContextReply(mes, fact.subject + " " + fact.info);
				}
				
			} else {
				irc.sendContextReply(mes, "Sorry, I don't have any facts about '" + data.search.subject + "'.");
			}
		}
	}
	
	public String[] helpCommandGetRumour = {
			"Returns a rumour for a term.",
			"<term> [" + splitWords + "] [<search>]",
			"<term> is the term to define",
			"<search> limits the definition(s) given, if multiple ones exist (substring or regexp allowed)"
		};
	
	public void commandGetRumour(Message mes) throws ChoobException
	{
		FactoidSearchData data = getFactoidSearchDefinitions(mes);
		
		if (data == null) {
			irc.sendContextReply(mes, "Syntax: 'Factoids2.GetRumour " + helpCommandGetRumour[1] + "'");
			return;
		}
		
		if (data.definitions.size() == 0) {
			irc.sendContextReply(mes, "Sorry, I don't know anything about '" + data.search.subject + "'!");
			
		} else {
			int rumourCount = countRumours(data.definitions);
			
			if (rumourCount > 0) {
				Factoid rumour = pickRumour(data.definitions, mes.getContext() + ":" + data.search.subject);
				rumour.reads++;
				mods.odb.update(rumour);
				
				if (rumourCount > 2) {
					irc.sendContextReply(mes, "Rumour has it " + rumour.subject + " " + rumour.info + " (" + (rumourCount - 1) + " other rumours)");
				} else if (rumourCount == 2) {
					irc.sendContextReply(mes, "Rumour has it " + rumour.subject + " " + rumour.info + " (1 other rumour)");
				} else {
					irc.sendContextReply(mes, "Rumour has it " + rumour.subject + " " + rumour.info);
				}
				
			} else {
				irc.sendContextReply(mes, "Sorry, I don't have any rumours about '" + data.search.subject + "'.");
			}
		}
	}
}
