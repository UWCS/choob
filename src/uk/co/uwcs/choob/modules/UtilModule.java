/*
 * UtilModule.java
 *
 * Created on July 4, 2005, 9:08 PM
 */

package uk.co.uwcs.choob.modules;

import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.plugins.*;
import uk.co.uwcs.choob.modules.*;
import java.sql.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;

/**
 * Set of general functions that tend to be frequently used in plugins.
 */
public final class UtilModule
{
	private IRCInterface irc;
	private Pattern triggerPattern;

	long starttime;

	/** Creates a new instance of UtilModule */
	UtilModule( IRCInterface irc ) {
		this.irc = irc;
		updateTrigger();
		starttime=(new java.util.Date()).getTime();
	}

	public void updateTrigger()
	{
		this.triggerPattern = Pattern.compile(irc.getTriggerRegex(), Pattern.CASE_INSENSITIVE);
	}

	/** Get the offset of the trigger in the list of arguments */
	private int getTriggerOffset( String text )
	{
		Matcher ma = triggerPattern.matcher(text);
		if (ma.find())
			return ma.end();

		return 0;
	}

	public String getVersion()
	{
		return "$Date$$Rev$";
	}

	/** Get the parameter string (ie. message without the command) from a Message object */
	public String getParamString( Message mes )
	{
		String text = mes.getMessage();
		int offset = getTriggerOffset(text);
		int spacePos = text.indexOf(' ', offset);
		if (spacePos != -1)
			return text.substring(spacePos + 1).trim();

		return "";
	}

	/** Split the parameters of a Message event into a List of Strings */
	public String[] getParamArray( Message mes )
	{
		String text = mes.getMessage();
		int offset = getTriggerOffset(text);

		return text.substring(offset).split("\\s+");
	}

	public List<String> getParams( Message mes )
	{
		String[] params = getParamArray( mes );
		List<String> temp = new ArrayList<String>(params.length);
		for(String param: params)
			temp.add(param);
		return temp;
	}

	/**
	 * Get the first count parameters, then slurp any remaining into the
	 * count+1th.
	 *
	 * Note that the command token is /NOT/ included in the count!
	 */
	public String[] getParamArray( Message mes, int count )
	{
		String text = mes.getMessage();
		int offset = getTriggerOffset(text);

		return text.substring(offset).split("\\s+", count + 1);
	}

	public List<String> getParams( Message mes, int count )
	{
		String[] params = getParamArray( mes, count );
		List<String> temp = new ArrayList<String>(params.length);
		for(String param: params)
			temp.add(param);
		return temp;
	}

	public long getStartTime()
	{
		return starttime;
	}
}
