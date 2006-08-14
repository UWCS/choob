/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;
import java.security.Permission;

public final class ChoobUserAuthError extends ChoobAuthError
{
	private static final long serialVersionUID = 2241956630197541493L;
	private Permission permission;
	public ChoobUserAuthError(Permission permission)
	{
		super("I can't let you do that, Dave! You need this permission: " + getPermissionText(permission));
		this.permission = permission;
	}
	public Permission getPermission()
	{
		return permission;
	}
}
