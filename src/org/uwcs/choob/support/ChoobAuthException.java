/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package org.uwcs.choob.support;
import java.security.Permission;

public abstract class ChoobAuthException extends ChoobException
{
	public static final String getPermissionText(Permission permission)
	{
		if (permission instanceof java.security.AllPermission)
			return "ALL";

		String output;
		String className = permission.getClass().getSimpleName();
		if (className.endsWith("Permission"))
			output = className.substring(0, className.length() - 10);
		else
			output = className;

		String name = permission.getName();
		String actions = permission.getActions();
		if (name != null)
		{
			output += " with name \"" + name + "\"";
			if (actions != null)
				output += " and actions \"" + actions + "\"";
		}
		else if (actions != null)
			output += " and actions \"" + actions + "\"";

		return output;
	}
	public ChoobAuthException(String text)
	{
		super(text);
	}
}
