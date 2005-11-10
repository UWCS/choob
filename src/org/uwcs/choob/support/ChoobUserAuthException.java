/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package org.uwcs.choob.support;
import java.security.Permission;

public final class ChoobUserAuthException extends ChoobAuthException
{
	private Permission permission;
	public ChoobUserAuthException(Permission permission)
	{
		super("I can't let you do that, Dave! You need this permission: " + getPermissionText(permission));
		this.permission = permission;
	}
	public Permission getPermission()
	{
		return permission;
	}
}
