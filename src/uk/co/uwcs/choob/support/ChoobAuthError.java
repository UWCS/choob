/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;
import java.security.Permission;

public abstract class ChoobAuthError extends ChoobError
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
		if (name != null && !name.equals(""))
		{
			output += " with name \"" + name + "\"";
			if (actions != null && !actions.equals(""))
				output += " and actions \"" + actions + "\"";
		}
		else if (actions != null && !actions.equals(""))
			output += " with actions \"" + actions + "\"";

		return output;
	}
	public ChoobAuthError(String text)
	{
		super(text);
	}
}
