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
    public UtilModule() {
        
    }
    
    public List getParms( Context con )
    {
        List tempList = new ArrayList();
        
        StringTokenizer tokens = new StringTokenizer(con.getText()," ");
        
        while( tokens.hasMoreTokens() )
        {
            tempList.add( tokens.nextToken() );
        }
        
        return tempList;
    }
}
