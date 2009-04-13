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

/**
 * Set of general functions that tend to be frequently used in plugins.
 */
public final class UtilModule
{
	private final IRCInterface irc;
	private Pattern triggerPattern;

	long starttime;

	/** Creates a new instance of UtilModule */
	UtilModule( final IRCInterface irc ) {
		this.irc = irc;
		updateTrigger();
		starttime=new java.util.Date().getTime();
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
	private int getTriggerOffset( final String text )
	{
		final Matcher ma = triggerPattern.matcher(text);
		if (ma.find())
			return ma.end();

		return 0;
	}

	public String getVersion()
	{
		return "$Date$$Rev$";
	}

	/** Get the parameter string (ie. message without the command) from a Message object */
	public String getParamString( final Message mes )
	{
		final String text = mes.getMessage();
		final int offset = getTriggerOffset(text);
		final int spacePos = text.indexOf(' ', offset);
		if (spacePos != -1)
			return text.substring(spacePos + 1).trim();

		return "";
	}

	/** Split the parameters of a Message event into a List of Strings */
	public String[] getParamArray( final Message mes )
	{
		final String text = mes.getMessage();
		return getParamArray(text);
	}

	private String[] getParamArray(final String str)
	{
		final int offset = getTriggerOffset(str);

		return str.substring(offset).split("\\s+");
	}

	/** 1-indexed list of params, like {@link #getParams(Message)}.
	 * argv[0] is "", not command name */
	public List<String> getParams( final String str )
	{
		String[] params = getParamArray(str);
		List<String> ret = new ArrayList<String>(params.length + 1);
		ret.add("");
		ret.addAll(Arrays.asList(params));
		return ret;
	}

	public List<String> getParams( final Message mes )
	{
		return Arrays.asList(getParamArray( mes ));
	}

	/**
	 * Get the first count parameters, then slurp any remaining into the
	 * count+1th.
	 *
	 * Note that the command token is /NOT/ included in the count!
	 */
	public String[] getParamArray( final Message mes, final int count )
	{
		final String text = mes.getMessage();
		final int offset = getTriggerOffset(text);

		return text.substring(offset).split("\\s+", count + 1);
	}

	public List<String> getParams( final Message mes, final int count )
	{
		final String[] params = getParamArray( mes, count );
		final List<String> temp = new ArrayList<String>(params.length);
		for(final String param: params)
			temp.add(param);
		return temp;
	}

	public long getStartTime()
	{
		return starttime;
	}
}
