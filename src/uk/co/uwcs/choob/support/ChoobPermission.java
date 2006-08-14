/*
 * ChoobPermission.java
 *
 * Created on June 29, 2005, 3:42 AM
 */

package uk.co.uwcs.choob.support;

import java.security.*;

/**
 * Skeleton Permission for integration into modules that have priviledged access.
 * @author sadiq
 */
public class ChoobPermission extends BasicPermission
{
	private static final long serialVersionUID = 8086104141611581652L;

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
	 * @param name
	 * @param actions
	 */
	public ChoobPermission( String name, String actions ) {
		this(name);
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
	 */
	public boolean implies(java.security.Permission permission)
	{
		boolean retValue;

		retValue = super.implies(permission);
		return retValue;
	}

}
