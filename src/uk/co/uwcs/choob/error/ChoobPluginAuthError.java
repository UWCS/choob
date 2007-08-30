/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package uk.co.uwcs.choob.error;

import java.security.Permission;


public final class ChoobPluginAuthError extends ChoobAuthError {
	private static final long serialVersionUID = -4702733953713229195L;

	private Permission permission;

	private String plugin;

	public ChoobPluginAuthError(String plugin, Permission permission) {
		super("The plugin " + plugin + " needs this permission: "
				+ getPermissionText(permission));
		this.permission = permission;
	}

	public Permission getPermission() {
		return permission;
	}

	public String getPlugin() {
		return plugin;
	}
}
