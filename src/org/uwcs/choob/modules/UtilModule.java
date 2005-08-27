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
}
