/*
 * ChoobPermission.java
 *
 * Created on June 29, 2005, 3:42 AM
 */

package org.uwcs.choob.support;

import java.security.*;

/**
 * Skeleton Permission for integration into modules that have priviledged access.
 * @author sadiq
 */
public class ChoobPermission extends BasicPermission
{
    /**
     *
     * @param name
     */    
    public ChoobPermission( String name )
    {
        super(name);
    }
    
    /**
     *
     * @param obj
     * @throws SecurityException
     */    
    public void checkGuard(Object obj) throws java.lang.SecurityException
    {
        super.checkGuard(obj);
    }
    
    /**
     *
     * @return
     */    
    public String getActions()
    {
        String retValue;
        
        retValue = super.getActions();
        return retValue;
    }
    
    /**
     *
     * @param permission
     * @return
     */    
    public boolean implies(java.security.Permission permission)
    {
        boolean retValue;
        
        retValue = super.implies(permission);
        return retValue;
    }
    
}