/*
 * ChoobPermission.java
 *
 * Created on June 29, 2005, 3:42 AM
 */

package uk.co.uwcs.choob.support;

import java.security.BasicPermission;

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
	public ChoobPermission( final String name )
	{
		super(name);
	}

	/**
	 *
	 * @param name
	 * @param actions
	 */
	public ChoobPermission( final String name, final String actions ) {
		this(name);
	}
	/**
	 *
	 * @param obj
	 * @throws SecurityException
	 */
	@Override
	public void checkGuard(final Object obj) throws java.lang.SecurityException
	{
		super.checkGuard(obj);
	}

	/**
	 *
	 */
	@Override
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
	@Override
	public boolean implies(final java.security.Permission permission)
	{
		boolean retValue;

		retValue = super.implies(permission);
		return retValue;
	}

}
