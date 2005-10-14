/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package org.uwcs.choob.support;
import java.security.Permission;

public class ChoobPluginAuthException extends ChoobAuthException
{
	private Permission permission;
	private String plugin;
	public ChoobPluginAuthException(String plugin, Permission permission)
	{
		super("The plugin " + plugin + " needs this permission: " + getPermissionText(permission));
		this.permission = permission;
	}
	public Permission getPermission()
	{
		return permission;
	}
	public String getPlugin()
	{
		return plugin;
	}
}
