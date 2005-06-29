/*
 * ChoobPermission.java
 *
 * Created on June 29, 2005, 3:42 AM
 */

package org.uwcs.choob.support;

import java.security.*;

/**
 *
 * @author  sadiq
 */
public class ChoobPermission extends BasicPermission
{
    public ChoobPermission( String name )
    {
        super(name);
    }
    
    public void checkGuard(Object obj) throws java.lang.SecurityException
    {
        super.checkGuard(obj);
    }
    
    public String getActions()
    {
        String retValue;
        
        retValue = super.getActions();
        return retValue;
    }
    
    public boolean implies(java.security.Permission permission)
    {
        boolean retValue;
        
        retValue = super.implies(permission);
        return retValue;
    }
    
}
