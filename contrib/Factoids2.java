import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.ChannelMessage;
import uk.co.uwcs.choob.support.events.Message;

class Factoid
{
	public Factoid()
	{
		// Unhide
	}

	public Factoid(final String subject, final boolean fact, final String info, final String nick)
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

class FactoidEnumerator
{
	public FactoidEnumerator()
	{
		// Unhide
	}

	public FactoidEnumerator(final String enumSource, final int index, final int count)
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
	public FactoidParsed(final String subject, final String definition)
	{
		this.subject = subject;
		this.definition = definition;
	}

	public String subject;
	public String definition;
}

class FactoidSearch
{
	public FactoidSearch(final String subject)
	{
		this.subject = subject;
		this.search = "";
	}

	public FactoidSearch(final String subject, final String search)
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

	private final Modules mods;
	private final IRCInterface irc;
	private final Pattern filterFactoidsPattern;
	private final Pattern filterQuestionPatter;
	private final Pattern addDefinitionPattern;
	private final Pattern quotedSearchPattern;
	private final Pattern regexpSearchPattern;

	public Factoids2(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
		filterFactoidsPattern = Pattern.compile(filterFactoidsRegex);
		filterQuestionPatter = Pattern.compile(".*\\?\\s*");
		addDefinitionPattern = Pattern.compile("\\s*(?i:that\\s+)?(.+?)\\s+((?i:" + splitWords + ")\\s+.+)\\s*");
		quotedSearchPattern = Pattern.compile("\\s*\"([^\"]+)\"(?:\\s+(.*))?\\s*");
		regexpSearchPattern = Pattern.compile("\\s*(.+?)\\s+(/([^/]+|\\/)+/)\\s*");

		mods.interval.callBack("clean-enums", 60000, 1);
	}

	private int countFacts(final List<Factoid> definitions)
	{
		int factCount = 0;
		for (int i = 0; i < definitions.size(); i++) {
			final Factoid defn = definitions.get(i);
			if (defn.fact) {
				factCount++;
			}
		}
		return factCount;
	}

	private int countRumours(final List<Factoid> definitions)
	{
		int rumourCount = 0;
		for (int i = 0; i < definitions.size(); i++) {
			final Factoid defn = definitions.get(i);
			if (!defn.fact) {
				rumourCount++;
			}
		}
		return rumourCount;
	}

	private Factoid pickFact(final List<Factoid> definitions, final String enumSource)
	{
		return pickDefinition(definitions, true, countFacts(definitions), enumSource);
	}

	private Factoid pickRumour(final List<Factoid> definitions, final String enumSource)
	{
		return pickDefinition(definitions, false, countRumours(definitions), enumSource);
	}

	private Factoid pickDefinition(final List<Factoid> definitions, final boolean fact, final int count, String enumSource)
	{
		int index = -1;
		enumSource = enumSource.toLowerCase();
		final List<FactoidEnumerator> enums = mods.odb.retrieve(FactoidEnumerator.class, "WHERE enumSource = '" + mods.odb.escapeString(enumSource) + "'");
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
			final Factoid defn = definitions.get(i);
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

	private List<Factoid> getDefinitions(final FactoidSearch factoidSearch)
	{
		final String subject = factoidSearch.subject.toLowerCase();
		final String search = factoidSearch.search;
		String odbQuery = "WHERE subject = '" + mods.odb.escapeString(subject) + "'";

		if (search.length() > 0) {
			if (search.startsWith("/") && search.endsWith("/")) {
				// Regexp
				odbQuery += " AND info RLIKE \"" + mods.odb.escapeForRLike(search.substring(1, search.length() - 1)) + "\"";
			} else {
				// Substring
				odbQuery += " AND info LIKE \"%" + mods.odb.escapeForLike(search) + "%\"";
			}
		}
		return mods.odb.retrieve(Factoid.class, odbQuery);
	}

	// Get a subject + definition from the message params.
	private FactoidParsed getParsedFactoid(final Message mes)
	{
		final String[] params1 = mods.util.getParamArray(mes, 2);

		if (params1.length <= 2) {
			return null;
		}

		final String[] params2 = mods.util.getParamArray(mes, 1);

		// If they have used is/are, split on that.
		final Matcher mIsAre = addDefinitionPattern.matcher(params2[1]);
		if (mIsAre.matches()) {
			return new FactoidParsed(mIsAre.group(1), mIsAre.group(2));
		}

		// Default to first word + rest split.
		return new FactoidParsed(params1[1], params1[2]);
	}

	// Get a subject + optional search from the message params.
	private FactoidSearch getFactoidSearch(final Message mes)
	{
		final String[] params1 = mods.util.getParamArray(mes, 2);

		if (params1.length == 2) {
			return new FactoidSearch(params1[1]);
		}
		if (params1.length <= 1) {
			return null;
		}

		final String[] params2 = mods.util.getParamArray(mes, 1);

		// If they have used quotes, split on that.
		final Matcher mQuoted = quotedSearchPattern.matcher(params2[1]);
		if (mQuoted.matches()) {
			return new FactoidSearch(mQuoted.group(1), mQuoted.group(2));
		}

		// If there's a regexp search, split on that.
		final Matcher mRegExp = regexpSearchPattern.matcher(params2[1]);
		if (mRegExp.matches()) {
			return new FactoidSearch(mRegExp.group(1), mRegExp.group(2));
		}

		// If they have used is/are, split on that.
		final Matcher mIsAre = addDefinitionPattern.matcher(params2[1]);
		if (mIsAre.matches()) {
			return new FactoidSearch(mIsAre.group(1), mIsAre.group(2));
		}

		// Default to first word + rest split.
		return new FactoidSearch(params1[1], params1[2]);
	}

	private FactoidSearchData getFactoidSearchDefinitions(final Message mes)
	{
		final FactoidSearchData data = new FactoidSearchData();

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
			final String[] params = mods.util.getParamArray(mes, 1);
			final FactoidSearch search = new FactoidSearch(params[1]);
			final List<Factoid> definitions = getDefinitions(search);

			// Something did match when quoted - pretend that's what the user asked for originally.
			if (definitions.size() > 0) {
				data.search = search;
				data.definitions = definitions;
			}
		}
		return data;
	}

	// Interval
	public void interval(final Object param)
	{
		if ("clean-enums".equals(param)) {
			// Clean up dead enumerators.
			final long lastUsedCutoff = System.currentTimeMillis() - ENUM_TIMEOUT;
			final List<FactoidEnumerator> deadEnums = mods.odb.retrieve(FactoidEnumerator.class, "WHERE lastUsed < " + lastUsedCutoff);
			for (int i = 0; i < deadEnums.size(); i++) {
				mods.odb.delete(deadEnums.get(i));
			}
			mods.interval.callBack(param, 60000, 1);
		}
	}

	// Collect and store rumours for things.
	public String filterFactoidsRegex = "(\\w{4,})\\s+((?i:" + splitWords + ")\\s+.{4,})";

	public void filterFactoids(final Message mes)
	{
		// Only capture channel messages.
		if (!(mes instanceof ChannelMessage)) {
			return;
		}

		final Matcher rMatcher = filterFactoidsPattern.matcher(mes.getMessage());
		if (rMatcher.matches()) {
			final String subject = rMatcher.group(1).toLowerCase();
			final String defn = rMatcher.group(2);

			// Exclude questions from the data.
			final Matcher questionMatcher = filterQuestionPatter.matcher(defn);
			if (questionMatcher.matches()) {
				return;
			}

			// We don't auto-learn about some things.
			if (subjectExclusions.contains(subject))
				return;

			final Factoid rumour = new Factoid(subject, false, defn, mes.getNick());
			mods.odb.save(rumour);

			// Remove oldest so we only have the 5 most recent.
			final List<Factoid> results = mods.odb.retrieve(Factoid.class, "WHERE fact = 0 AND subject = '" + mods.odb.escapeString(subject) + "' SORT DESC date");

			for (int i = 5; i < results.size(); i++) {
				final Factoid oldRumour = results.get(i);
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

	public void commandStats(final Message mes)
	{
		FactoidSearchData data = getFactoidSearchDefinitions(mes);

		if (data == null) {
			data = new FactoidSearchData();
			data.definitions = mods.odb.retrieve(Factoid.class, "");
		}

		if (data.search != null && data.definitions.size() == 1) {
			final Factoid defn = data.definitions.get(0);
			irc.sendContextReply(mes, "Factoid for '" + data.search.subject +
					"' is a " + (defn.fact ? "fact" : "rumour") +
					(defn.fact ? " added by " : " collected from ") + defn.nick +
					" and displayed " + defn.reads + " time" + (defn.reads != 1 ? "s":"") + ".");

		} else {
			final int factCount   = countFacts(data.definitions);
			final int rumourCount = countRumours(data.definitions);

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

	public void commandAdd(final Message mes)
	{
		final FactoidParsed factoid = getParsedFactoid(mes);

		if (factoid == null) {
			irc.sendContextReply(mes, "Syntax: 'Factoids2.Add " + helpCommandAdd[1] + "'");
			return;
		}

		final Factoid fact = new Factoid(factoid.subject, true, factoid.definition, mes.getNick());
		mods.odb.save(fact);
		irc.sendContextReply(mes, "Added definition for '" + factoid.subject + "'.");
	}

	// Remove facts from the system.
	public String[] helpCommandRemove = {
			"Remove factual definitions for a term.",
			"<term> [" + splitWords + "] [<search>]",
			"<term> is the term to remove",
			"<search> limits the removal to only matching defintions, if multiple ones exist (substring or regexp allowed)"
		};

	public void commandRemove(final Message mes)
	{
		final FactoidSearchData data = getFactoidSearchDefinitions(mes);

		if (data == null) {
			irc.sendContextReply(mes, "Syntax: 'Factoids2.Remove " + helpCommandRemove[1] + "'");
			return;
		}

		int count = 0;
		for (int i = 0; i < data.definitions.size(); i++) {
			final Factoid defn = data.definitions.get(i);
			if (defn.fact) {
				mods.odb.delete(defn);
				count++;
			}
		}
		if (count > 1) {
			irc.sendContextReply(mes, count + " factual definitions for '" + data.search.subject + "' removed.");
		} else if (count == 1) {
			irc.sendContextReply(mes, "1 factual definition for '" + data.search.subject + "' removed.");
		} else {
			irc.sendContextReply(mes, "No factual definitions for '" + data.search.subject + "' found.");
		}
	}

	// Retrieve definitions from the system.
	public String[] helpCommandGet = {
			"Returns a definition for a term.",
			"<term> [" + splitWords + "] [<search>]",
			"<term> is the term to define",
			"<search> limits the definition(s) given, if multiple ones exist (substring or regexp allowed)"
		};

	public void commandGet(final Message mes)
	{
		final FactoidSearchData data = getFactoidSearchDefinitions(mes);

		if (data == null) {
			irc.sendContextReply(mes, "Syntax: 'Factoids2.Get " + helpCommandGet[1] + "'");
			return;
		}

		if (data.definitions.size() == 0) {
			irc.sendContextReply(mes, "Sorry, I don't know anything about '" + data.search.subject + "'!");

		} else {
			final int factCount = countFacts(data.definitions);
			final int rumourCount = countRumours(data.definitions);

			if (factCount > 0) {
				final Factoid fact = pickFact(data.definitions, mes.getContext() + ":" + data.search.subject);
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
				final Factoid rumour = pickRumour(data.definitions, mes.getContext() + ":" + data.search.subject);
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

	public void commandGetFact(final Message mes)
	{
		final FactoidSearchData data = getFactoidSearchDefinitions(mes);

		if (data == null) {
			irc.sendContextReply(mes, "Syntax: 'Factoids2.GetFact " + helpCommandGetFact[1] + "'");
			return;
		}

		if (data.definitions.size() == 0) {
			irc.sendContextReply(mes, "Sorry, I don't know anything about '" + data.search.subject + "'!");

		} else {
			final int factCount = countFacts(data.definitions);

			if (factCount > 0) {
				final Factoid fact = pickFact(data.definitions, mes.getContext() + ":" + data.search.subject);
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

	public void commandGetRumour(final Message mes)
	{
		final FactoidSearchData data = getFactoidSearchDefinitions(mes);

		if (data == null) {
			irc.sendContextReply(mes, "Syntax: 'Factoids2.GetRumour " + helpCommandGetRumour[1] + "'");
			return;
		}

		if (data.definitions.size() == 0) {
			irc.sendContextReply(mes, "Sorry, I don't know anything about '" + data.search.subject + "'!");

		} else {
			final int rumourCount = countRumours(data.definitions);

			if (rumourCount > 0) {
				final Factoid rumour = pickRumour(data.definitions, mes.getContext() + ":" + data.search.subject);
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

	public String[] helpCommandRandomRumour = {
			"Returns a completely random rumour.",
			""
		};

	public void commandRandomRumour(final Message mes)
	{
		final List<Factoid> list = mods.odb.retrieve(Factoid.class, "WHERE fact = 0 ORDER BY RAND() LIMIT 1");

		if (list.size() == 0) {
			irc.sendContextReply(mes, "Oh dear, I don't have any rumours!");
		} else {
			final Factoid rumour = list.get(0);
			irc.sendContextReply(mes, "Rumour has it " + rumour.subject + " " + rumour.info);
		}
	}

	public void webStats(final PrintWriter out, final String args, final String[] from)
	{
		try
		{
			out.println("HTTP/1.0 200 OK");
			out.println("Content-Type: text/html");
			out.println();

			if (args.length() > 0) {
				out.println("<H1>Factoid &quot;" + args + "&quot;</H1>");
			} else {
				out.println("<H1>Factoid Summary</H1>");
			}

			final FactoidSearchData data = new FactoidSearchData();

			if (args.length() > 0) {
				data.search = new FactoidSearch(args);
				data.definitions = getDefinitions(data.search);
			} else {
				data.definitions = mods.odb.retrieve(Factoid.class, "");
			}

			final int factCount   = countFacts(data.definitions);
			final int rumourCount = countRumours(data.definitions);

			if (data.search != null) {
				out.println(data.definitions.size() +
						" factoid" + (data.definitions.size() != 1 ? "s":"") +
						" matched, containing " + factCount + " fact" + (factCount != 1 ? "s":"") +
						" and " + rumourCount + " rumour" + (rumourCount != 1 ? "s":"") + ".");
			} else {
				out.println("Factoids database contains " + factCount + " fact" + (factCount != 1 ? "s":"") + " and " + rumourCount + " rumour" + (rumourCount != 1 ? "s":"") + ".");
			}

			if (data.search != null) {
				out.println("	<STYLE>");
				out.println("		dd { font-size: smaller; font-style: italic; }");
				out.println("	</STYLE>");
				out.println("	<DL>");
				for (int i = 0; i < data.definitions.size(); i++) {
					final Factoid defn = data.definitions.get(i);
					out.println("		<DT>&quot;" + defn.subject + " " + defn.info + "&quot;</DT>");
					out.println("		<DD>" + (defn.fact ? "Fact" : "Rumour") +
							(defn.fact ? " added by " : " collected from ") + defn.nick +
							" and displayed " + defn.reads + " time" + (defn.reads != 1 ? "s":"") + ".</DD>");
				}
				out.println("	</DL>");

			} else {
				final Set<String>         termsUsed   = new HashSet<String>();
				final Map<String,Integer> termFacts   = new HashMap<String,Integer>();
				final Map<String,Integer> termRumours = new HashMap<String,Integer>();

				out.println("	<TABLE BORDER='1'>");
				out.println("		<TR><TH>Term</TH><TH>Facts</TH><TH>Rumours</TH></TR>");

				for (int i = 0; i < data.definitions.size(); i++) {
					final Factoid defn = data.definitions.get(i);
					final String subj = defn.subject.toLowerCase();

					if (!termsUsed.contains(subj)) {
						termsUsed.add(subj);
						termFacts.put(subj, Integer.valueOf(0));
						termRumours.put(subj, Integer.valueOf(0));
					}
					if (defn.fact) {
						termFacts.put(subj, Integer.valueOf(termFacts.get(subj).intValue() + 1));
					} else {
						termRumours.put(subj, Integer.valueOf(termRumours.get(subj).intValue() + 1));
					}
				}

				final ArrayList<String> sortTerms = new ArrayList<String>(termsUsed);
				Collections.sort(sortTerms);
				for (int i = 0; i < sortTerms.size(); i++) {
					final String subj = sortTerms.get(i);
					out.println("		<TR><TD><A HREF='?" + subj + "'>" + subj + "</A></TD><TD>" + termFacts.get(subj) + "</TD><TD>" + termRumours.get(subj) + "</TD></TR>");
				}

				out.println("	</TABLE>");
			}
		}
		catch (final Exception e)
		{
			out.println("ERROR!");
			e.printStackTrace();
		}
	}
}
