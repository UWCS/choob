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
import java.util.*;
import java.util.regex.*;

/**
 *
 * @author  sadiq
 */
public class UtilModule {

	/** Creates a new instance of UtilModule */
	public UtilModule( Choob bot ) {
	}

	public String getParamString( Message mes )
	{
		int spacePos = mes.getText().indexOf(' ');
		if (spacePos != -1)
			return mes.getText().substring(spacePos + 1);

		return "";
	}

	public List getParms( Message mes )
	{
		List tempList = new ArrayList();

		StringTokenizer tokens = new StringTokenizer(mes.getText()," ");

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
	 * Note that the command token /IS/ included in the count.
	 */
	public List getParms( Message mes, int count )
	{
		List tempList = new ArrayList();

		String text = mes.getText();

		int currentPos = text.indexOf(' ');
		int lastPos = 0;
		for(int i=0; i<count; i++) {
			tempList.add( text.substring( lastPos, currentPos ) );
			while (true) { // How do I do an "until" loop? --bucko
				lastPos = currentPos + 1;
				currentPos = text.indexOf(' ', lastPos);

				// I don't think there's a possible race condition here.
				// Also note that lastPos can't be -1, so an indexOf fail
				//   doesn't break this, either.
				// Make sure we skip "empty" parameters.
				if (currentPos != lastPos)
					break;
			}
			if (currentPos == -1) {
				// Last parameter!
				tempList.add( text.substring( lastPos ) );
				break;
			}
		}

		if (currentPos != -1) {
			// Above loop finished without already slurping the final
			// parameter.
			tempList.add( text.substring( lastPos ) );
		}

		System.out.println("Done.");

		return tempList;
	}
}
