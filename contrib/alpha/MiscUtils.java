import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.ChannelAction;
import uk.co.uwcs.choob.support.events.ChannelMessage;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.PrivateAction;

public class MiscUtils
{


	private static final int DOT_CODE_POINT = ".".codePointAt(0);
	Modules mods;
	private final IRCInterface irc;

	static class SedArgs
	{
		boolean warn = true; // show errors that occurred
		boolean case_ins; // regex i: case insensitive
		boolean global; // regex g: replace all
		boolean treat_single; // regex s: treat input as single line
		boolean prefer; // p: prefer my lines
	}


	public MiscUtils(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public String[] info()
	{
		return new String[] { "Plugin for random misc stuff", "The Choob Team", "choob@uwcs.co.uk",
				"$Rev$$Date$" };
	}

	private static final String REGEX_ARGS = "((?i)[wpigs]{0,5})";
	private static final Pattern LINE_PICK_REGEX = Pattern.compile("(.)(.*)\\1" + REGEX_ARGS);
	public final String filterReplaceRegex = "s(.)(.*\\1.*\\1)" + REGEX_ARGS + "(?:(?:\\s+)|$)";
	private final int MAXLENGTH = 300;

	// I'm sorry.
	public Pattern apiLinePicker(String regex)
	{
		Matcher matcher = LINE_PICK_REGEX.matcher(regex);
		if (!matcher.find())
			throw new IllegalArgumentException("Can't process " + regex + " as regex 1");
		return Pattern.compile(matcher.group(2), processArgs(matcher.group(3)).case_ins ? Pattern.CASE_INSENSITIVE : 0);
	}

	private final Pattern compiledFilterReplaceRegex = Pattern.compile(filterReplaceRegex);
	public String apiSed(final String params, final String stdin)
	{
		System.out.println("Sed " + params + " - " + stdin);
		final Matcher matcher = compiledFilterReplaceRegex.matcher(params);
		if (!matcher.find())
			throw new RuntimeException("Couldn't read sed line 1");

		final SedArgs args = processArgs(matcher.group(3));
		final Matcher fromTo = getFromTo(matcher);

		if (!fromTo.matches())
			throw new RuntimeException("Couldn't read sed line 2");

		final Matcher lm = makeLineMatcher(args.case_ins, fromTo.group(1), stdin);
		if (!lm.find())
		{
			System.out.println("no line matcher for " + fromTo.group(1));
			return stdin;
		}
		return doReplace(fromTo.group(2), args.global, lm);
	}

	public void filterReplace(final Message mes)
	{
		if (!(mes instanceof ChannelMessage))
			return;

		final String command = mods.util.getParamArray(mes, 1)[0];
		if (isCommand(command))
			return;

		final String message = mes.getMessage();

		SedArgs args = null;
		try
		{
			// Run the filter regex with the trigger.
			Matcher matcher = Pattern.compile(irc.getTriggerRegex() + filterReplaceRegex).matcher(message);
			if (!matcher.find())
				return;

			args = processArgs(matcher.group(3));
			matcher = getFromTo(matcher);

			if (!matcher.matches())
				return;

			final String original = matcher.group(1);
			final String replacement = matcher.group(2);

			final List<Message> history = mods.history.getLastMessages(mes, 10);
			final Pattern trigger = Pattern.compile(irc.getTriggerRegex());

			if (args.treat_single)
			{
				StringBuilder sb = new StringBuilder();
				for (int i = history.size() - 1; i >= 0; --i)
					sb.append(stringize(history.get(i))).append("\n");
				final String thisLine = sb.toString();
				final Matcher matt = makeLineMatcher(args.case_ins, original, thisLine);
				if (matt.find())
					processReplacement(mes, replacement, ", in sed, in twenty mintues,", args.global,
							matt, null);
				else if (args.warn)
					irc.sendContextReply(mes, "Didn't match.");
				return;
			}

			if (args.prefer)
				for (int i = 0; i < history.size(); ++i)
				{
					final Message thisLine = history.get(i);
					final Matcher matt = makeLineMatcher(args.case_ins, original, thisLine.getMessage());
					if (thisLine.getNick().equals(mes.getNick())
							&& qualifies(mes, trigger, thisLine, matt))
					{
						processReplacement(mes, replacement, "", args.global, matt, isActionOrNull(thisLine));
						return;
					}
				}

			for (int i = 0; i < history.size(); ++i)
			{
				final Message thisLine = history.get(i);
				final Matcher matt = makeLineMatcher(args.case_ins, original, thisLine.getMessage());
				if (qualifies(mes, trigger, thisLine, matt))
				{
					processReplacement(mes, replacement, " thinks " + thisLine.getNick(), args.global,
							matt, isActionOrNull(thisLine));
					return;
				}
			}
			if (args.warn)
				irc.sendContextReply(mes, "Nothing matched.");
		}
		catch (final Exception e)
		{
			if (args.warn)
				irc.sendContextReply(mes, e.toString());
			e.printStackTrace();
		}
	}

	private Matcher getFromTo(Matcher matcher) {
		// Get the separator, the main body and process the arguments.
		final String sep = matcher.group(1);
		final String body = matcher.group(2);


		// This 'pattern' is the body of the regex, including support for
		// escaped seperators.
		final String unescapedseps = "(?:\\\\.|[^" + inSquaresEscape(sep) + "\\\\])";
		final String pattern = "(" + unescapedseps + "+)" + Pattern.quote(sep) + "("
				+ unescapedseps + "*)" + Pattern.quote(sep);

		// Pull out the "from" and the "to".
		return Pattern.compile(pattern).matcher(body);
	}

	/** {@link #getFromTo(Matcher)} but with /a/ instead of /a/b/ */
	private Matcher getFromOnly(Matcher matcher) {
		// Get the separator, the main body and process the arguments.
		final String sep = matcher.group(1);
		final String body = matcher.group(2);


		// This 'pattern' is the body of the regex, including support for
		// escaped seperators.
		final String unescapedseps = "(?:\\\\.|[^" + inSquaresEscape(sep) + "\\\\])";
		final String pattern = "(" + unescapedseps + "+)" + Pattern.quote(sep);

		System.out.println(pattern + " // " + body);
		// Pull out the "from" and the "to".
		return Pattern.compile(pattern).matcher(body);
	}

	private SedArgs processArgs(final String args) {
		SedArgs sargs;
		sargs = new SedArgs();
		// on by default
		sargs.case_ins = args.indexOf('I') == -1;
		sargs.warn = args.indexOf('W') == -1;
		sargs.prefer = args.indexOf('P') == -1;

		// off by default
		sargs.global = args.indexOf('g') != -1;
		sargs.treat_single = args.indexOf('s') != -1;
		return sargs;
	}

	/** Check if the string is potentially a valid command */
	private boolean isCommand(final String command)
	{
		if (command.length() <= 4) // !a.b
			return false;
		boolean seen_dot = false;
		for (int i = 1; i < command.codePointCount(1, command.length()); ++i)
			if (command.codePointAt(i) == DOT_CODE_POINT)
				if (seen_dot)
					return false;
				else
					seen_dot = true;
			else if (!Character.isJavaIdentifierPart(command.codePointAt(i)))
				return false;
		return mods.plugin.validCommand(command);
	}

	private String isActionOrNull(final Message thisLine)
	{
		return isAction(thisLine) ? thisLine.getNick() : null;
	}

	private String stringize(Message m)
	{
		if (isAction(m))
			return " * " + m.getNick() + " " + m.getMessage();
		return "< " + m.getNick() + "> " + m.getMessage();
	}

	private boolean isAction(Message m)
	{
		return m instanceof ChannelAction || m instanceof PrivateAction;
	}

	private Matcher makeLineMatcher(boolean case_ins, final String original, final String str)
	{
		return Pattern.compile(original, case_ins ? Pattern.CASE_INSENSITIVE : 0).matcher(str);
	}

	private void processReplacement(final Message mes, final String replacement,
			String additional, boolean global, Matcher matt, String action_style)
	{
		String newLine = doReplace(replacement, global, matt);

		if (newLine.length() > MAXLENGTH)
			newLine = newLine.substring(0, MAXLENGTH);

		irc.sendContextMessage(mes, mes.getNick() + additional + " meant: "
				+ (action_style != null ? "* " + action_style + " " : "")
				+ newLine.replaceAll("\n", "; "));
	}

	private String doReplace(final String replacement, boolean global, Matcher matt) {
		String newLine;
		if (global)
			newLine = matt.replaceAll(replacement);
		else
			newLine = matt.replaceFirst(replacement);
		return newLine;
	}

	private boolean qualifies(final Message mes, final Pattern trigger, final Message thisLine,
			Matcher matt)
	{
		final String message = thisLine.getMessage();
		return thisLine.getContext().equals(mes.getContext()) && matt.find()
				&& !message.matches(filterReplaceRegex) && !trigger.matcher(message).find();
	}

	private static String inSquaresEscape(final String sep)
	{
		if (sep.equals("\\"))
			return "\\\\";
		if (sep.equals("]"))
			return "\\]";
		return sep;
	}

	public String[] helpCommandFilter = {
			"Replace <ORIGINAL CHARS> with <REPLACEMENT CHARS> in <MESSAGE>",
			"<ORIGINAL CHARS> <REPLACEMENT CHARS> <MESSAGE>." };

	public String commandFilter(final String mes)
	{
		final List<String> parm = mods.util.getParams(mes);

		String oldString = parm.get(1);
		String replacementString = parm.get(2);
		replacementString = replacementString.replaceAll("\\[:SPACE:\\]", " ");
		oldString = oldString.replaceAll("\\[:SPACE:\\]", " ");

		final char[] oldChars = oldString.toCharArray();
		final char[] replacementChars = replacementString.toCharArray();

		String returnedString = mes;
		returnedString = returnedString.substring(returnedString.indexOf(" ") + 1);
		returnedString = returnedString.substring(returnedString.indexOf(" ") + 1);
		final String originalString = returnedString;
		for (int i = 0; i < returnedString.length(); i++)
		{
			for (int ii = 0; ii < oldChars.length; ii++)
			{
				if (originalString.charAt(i) == oldChars[ii])
				{
					final char[] temp = returnedString.toCharArray();
					if (ii < replacementChars.length)
						temp[i] = replacementChars[ii];
					else
						temp[i] = replacementChars[replacementChars.length - 1];
					returnedString = new String(temp);
				}
			}
		}

		return returnedString;
	}

	public String[] helpCommandReplaceAll = {
			"Replace all instances of <; Separated list of regexes> with <; Separated list of strings>, NB: each replacement is performed successively, so replaceAll a;b b;a will not change anything",
			"<ORIGINAL REGEX(s)> <REPLACEMENT STRING(s)> <MESSAGE>." };

	public String commandReplaceAll(final String mes)
	{
		final List<String> parms = mods.util.getParams(mes);

		final String[] parm = new String[parms.size()];

		for (int i = 0; i < parms.size(); i++)
		{
			parm[i] = parms.get(i);
		}

		for (int i = 1; i < 3; i++)
		{
			parm[i] = parm[i].replaceAll("\\\\;", "[:INSERTCOLON:]");
		}
		final String[] oldStrings = parm[1].replaceAll("\"", "").split(";");
		final String[] replacementStrings = parm[2].replaceAll("\"", "").split(";");

		for (int i = 0; i < oldStrings.length; i++)
		{
			oldStrings[i] = oldStrings[i].replaceAll("\\[:SPACE:\\]", " ").replaceAll(
					"\\[:BLANK:\\]|\\[:NULL:\\]", "");
		}

		for (int i = 0; i < replacementStrings.length; i++)
		{
			replacementStrings[i] = replacementStrings[i].replaceAll("\\[:SPACE:\\]", " ")
					.replaceAll("\\[:BLANK:\\]|\\[:NULL:\\]", "");
		}

		if (oldStrings.length != replacementStrings.length)
			return "Error, old and replacement parameters do not match";

		for (int i = 0; i < oldStrings.length; i++)
		{
			oldStrings[i] = oldStrings[i].replaceAll("\\[:INSERTCOLON:\\]", "\\\\;");
			replacementStrings[i] = replacementStrings[i]
					.replaceAll("\\[:INSERTCOLON:\\]", "\\\\;");
		}

		String returnedString = mes;
		returnedString = returnedString.substring(returnedString.indexOf(" ") + 1);
		returnedString = returnedString.substring(returnedString.indexOf(" ") + 1);
		for (int i = 0; i < oldStrings.length; i++)
			returnedString = returnedString.replaceAll(oldStrings[i], replacementStrings[i]);

		return returnedString;
	}

	final private static Pattern trans_args = Pattern
			.compile("((?:\\\\.|[^\\s\\\\])+)(?:\\s+((?:\\\\.|[^\\s\\\\])+))?(?:\\s+(.)(.*?)\\3)?.*");

	public void commandTrans(final Message mes)
	{
		Matcher ma = trans_args.matcher(mods.util.getParamString(mes));
		if (!ma.matches())
		{
			irc.sendContextReply(mes,
					"Couldn't undertand arguments, expected: expr [expr] [/regex/]");
			return;
		}

		final List<Message> history = mods.history.getLastMessages(mes, 10);
		String regexp = ma.group(4);
		if (regexp == null)
			regexp = "";

		final Pattern reg = Pattern.compile(regexp, Pattern.CASE_INSENSITIVE);
		for (Message m : history)
			if (reg.matcher(m.getMessage()).find())
			{
				final List<Integer> firstExpr = processExp(ma.group(1));
				final String second = ma.group(2);
				final String message = m.getMessage();

				irc.sendContextReply(mes, doTrans(firstExpr, second, message));
				return;
			}
		irc.sendContextReply(mes, "Didn't match any messages with: /" + regexp + "/");
	}

	final private static Pattern api_trans_args = Pattern
			.compile("((?:\\\\.|[^\\s\\\\])+)(?:\\s+((?:\\\\.|[^\\s\\\\])+))?");

	public String apiTrans(final String args, final String input)
	{
		Matcher ma = api_trans_args.matcher(args);
		if (!ma.matches())
			return "Couldn't undertand arguments, expected: expr [expr]";

		return doTrans(processExp(ma.group(1)), ma.group(2), input);
	}

	private String doTrans(final List<Integer> firstExpr,
			final String second, final String message) {

		if (second != null && !second.isEmpty())
		{
			final List<Integer> secondExpr = processExp(second);
			if (secondExpr.size() != firstExpr.size())
			{
				return "Expecting sets of equal size.  "
						+ firstExpr.size() + " and " + secondExpr.size() + " recieved.";
			}

			Map<Integer, Integer> trans = new HashMap<Integer, Integer>();
			for (int i = 0; i < firstExpr.size(); ++i)
			{
				Integer first = firstExpr.get(i);
				if (trans.put(first, secondExpr.get(i)) != null)
				{
					return "First set illegally contains two (or more) references to '"
									+ toString(first.intValue()) + "'.";
				}
			}
			final StringBuilder sb = new StringBuilder();

			for (int i = 0; i < message.length(); ++i)
			{
				final int cp = message.codePointAt(i);
				final Integer transed = trans.get(Integer.valueOf(cp));
				if (transed != null)
					sb.append(toString(transed.intValue()));
				else
					sb.append(toString(cp));
			}
			return sb.toString();
		}

		String working = message;
		for (Integer i : firstExpr)
			working = working.replaceAll(Pattern.quote(toString(i.intValue())), "");

		return working;
	}

	private String toString(int first)
	{
		return new String(new int[] { first }, 0, 1);
	}

	/**
	 * Process a tr group to a list of code points. e.g. ab -> ['a','b'] a-c ->
	 * ['a','b','c'] c-a -> ['c','b','a'] qc-a -> ['q','c','b','a']
	 */
	private List<Integer> processExp(String group)
	{
		List<Integer> ret = new ArrayList<Integer>(group.length());
		int prev = 0;
		for (int i = 0; i < group.length(); ++i)
		{
			int curr = group.codePointAt(i);
			if (curr == '-')
			{
				int next = group.codePointAt(i + 1);
				if (next > prev)
					for (int j = prev + 1; j < next; ++j)
						ret.add(Integer.valueOf(j));
				else
					for (int j = prev - 1; j > next; --j)
						ret.add(Integer.valueOf(j));
			}
			else
				ret.add(Integer.valueOf(curr));
			prev = curr;
		}

		return ret;
	}
}
