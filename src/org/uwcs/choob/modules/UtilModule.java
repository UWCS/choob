/*
 * UtilModule.java
 *
 * Created on July 4, 2005, 9:08 PM
 */

package org.uwcs.choob.modules;

import org.uwcs.choob.*;
import org.uwcs.choob.plugins.*;
import org.uwcs.choob.modules.*;
import java.sql.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;

/**
 * Set of general functions that tend to be frequently used in plugins.
 */
public class UtilModule
{
	private IRCInterface irc;
	private Pattern triggerPattern;

	/** Creates a new instance of UtilModule */
	public UtilModule( IRCInterface irc ) {
		this.irc = irc;
		updateTrigger();
	}

	public void updateTrigger()
	{
		this.triggerPattern = Pattern.compile(this.irc.getTriggerRegex());
	}

	/** Get the offset of the trigger in the list of arguments */
	private int getTriggerOffset( String text )
	{
		Matcher ma = triggerPattern.matcher(text);
		if (ma.find())
			return ma.end() + 1;

		return 0;
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
	public List<String> getParams( Message mes )
	{
		String text = mes.getMessage();
		int offset = getTriggerOffset(text);

		List<String> tempList = new LinkedList<String>();

		StringTokenizer tokens = new StringTokenizer(text.substring(offset), " ");

		while( tokens.hasMoreTokens() )
		{
			tempList.add( tokens.nextToken() );
		}

		return tempList;
	}

	/**
	 * Get the first count parameters, then slurp any remaining into the
	 * count+1th.
	 *
	 * Note that the command token is /NOT/ included in the count!
	 */
	public List<String> getParams( Message mes, int count )
	{
		String text = mes.getMessage();
		int offset = getTriggerOffset(text);

		List<String> tempList = new LinkedList<String>();

		int currentPos = text.indexOf(' ', offset);
		int lastPos = offset;
		if (currentPos != -1)
			for(int i=0; i<count; i++)
			{
				tempList.add( text.substring( lastPos, currentPos ) );
				do
				{
					lastPos = currentPos + 1;
					currentPos = text.indexOf(' ', lastPos);

					// I don't think there's a possible race condition here.
					// Also note that lastPos can't be -1, so an indexOf fail doesn't break this, either.
					// Make sure we skip "empty" parameters.
				}
				while (currentPos == lastPos);

				if (currentPos == -1)
				{
					// Last parameter!
					tempList.add( text.substring( lastPos ).trim() );
					break;
				}
			}
		else
			tempList.add( text );

		if (currentPos != -1) {
			// Above loop finished without already slurping the final
			// parameter.
			tempList.add( text.substring( lastPos ).trim() );
		}

		System.out.println("Done.");

		return tempList;
	}
}
