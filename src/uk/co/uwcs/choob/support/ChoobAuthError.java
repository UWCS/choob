/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;
import java.security.Permission;

public abstract class ChoobAuthError extends ChoobError
{
	private static final long serialVersionUID = 3779784728261098709L;
	public static final String getPermissionText(final Permission permission)
	{
		if (permission instanceof java.security.AllPermission)
			return "ALL";

		String output;
		final String className = permission.getClass().getSimpleName();
		if (className.endsWith("Permission"))
			output = className.substring(0, className.length() - 10);
		else
			output = className;

		final String name = permission.getName();
		final String actions = permission.getActions();
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
	public ChoobAuthError(final String text)
	{
		super(text);
	}
}
