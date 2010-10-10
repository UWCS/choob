/*
 * UtilModule.java
 *
 * Created on July 4, 2005, 9:08 PM
 */

package uk.co.uwcs.choob.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.MessageEvent;

/**
 * Set of general functions that tend to be frequently used in plugins.
 */
public final class UtilModule
{
	private final IRCInterface irc;
	private Pattern triggerPattern;

	long startTime;

	/** Creates a new instance of UtilModule */
	UtilModule(final IRCInterface irc) {
		this.irc = irc;
		updateTrigger();
		startTime = new java.util.Date().getTime();
	}

	public void updateTrigger()
	{
		this.triggerPattern = Pattern.compile(irc.getTriggerRegex(), Pattern.CASE_INSENSITIVE);
	}

	/** Returns the pre-compiled and pre-cached Pattern for command matching. */
	public Pattern getTriggerPattern()
	{
		return this.triggerPattern;
	}

	/** Get the offset of the trigger in the list of arguments */
	private int getTriggerOffset(final String str)
	{
		final Matcher ma = triggerPattern.matcher(str);
		if (ma.find())
			return ma.end();
		return 0;
	}

	public String getVersion()
	{
		return "$Date$$Rev$";
	}

	/** Java's {@link String#trim()} removes control characters like bold, too, donotwant.
	 *
	 *  Completely original implementation, not based on Sun's at all, in any way. */
	private static String trimSpaces(String subs)
	{
		char[] val = subs.toCharArray();
		int st = 0, len = subs.length();
		while (st < len && val[st] == ' ')
		    st++;
		while (st < len && val[len - 1] == ' ')
		    len--;
		return subs.substring(st, len);
	}

	/** Get the parameter string (ie. message without the command) from a Message object */
	public String getParamString(final Message mes)
	{
		final String str = trimSpaces(mes.getMessage());
		final int offset = getTriggerOffset(str);
		final int spacePos = str.indexOf(' ', offset);
		if (spacePos != -1)
			return str.substring(spacePos + 1);
		return "";
	}

	/** Split the parameters of a Message event into a List of Strings */
	public String[] getParamArray(final MessageEvent mes)
	{
		return getParamArray(trimSpaces(mes.getMessage()));
	}

	public String[] getParamArray(final String str)
	{
		final int offset = getTriggerOffset(str);
		return str.substring(offset).split("\\s+");
	}

	public List<String> getParams(final MessageEvent mes)
	{
		return Arrays.asList(getParamArray(mes));
	}

	/** 1-indexed list of params, like {@link #getParams(Message)}.
	 * argv[0] is "", not command name */
	public List<String> getParams(final String str)
	{
		String[] params = getParamArray(str);
		List<String> ret = new ArrayList<String>(params.length + 1);
		ret.add("");
		// Don't add params if it's just [""].
		if (!(params.length == 1 && "".equals(params[0])))
			ret.addAll(Arrays.asList(params));
		return ret;
	}

	/**
	 * Get the first count parameters, then slurp any remaining into the
	 * count+1th.
	 *
	 * Note that the command token is /NOT/ included in the count!
	 */
	public String[] getParamArray(final Message mes, final int count)
	{
		return getParamArray(trimSpaces(mes.getMessage()), count);
	}

	public String[] getParamArray(final String str, final int count)
	{
		final int offset = getTriggerOffset(str);
		return str.substring(offset).split("\\s+", count + 1);
	}

	public List<String> getParams(final Message mes, final int count)
	{
		final String[] params = getParamArray(mes, count);
		final List<String> temp = new ArrayList<String>(params.length);
		for(final String param: params)
			temp.add(param);
		return temp;
	}

	public long getStartTime()
	{
		return startTime;
	}

	public static <T> String hrList(final List<T> el)
	{
		return hrList(el, " and ");
	}

	public static <T> String hrList(final List<T> el, final String inclusor)
	{
		final StringBuilder ret = new StringBuilder();
		for (int i=0; i<el.size(); ++i)
		{
			ret.append(el.get(i));
			if (i == el.size() - 2)
				ret.append(inclusor);
			else if (i != el.size() - 1)
				ret.append(", ");
		}
		return ret.toString();
	}

	public static void main(String[] args)
	{
		System.out.println(hrList(Arrays.asList("pony", "badger", "monkey")));
	}
}
