package org.uwcs.choob.support;

import java.security.ProtectionDomain;
import java.security.Permission;
import org.uwcs.choob.modules.SecurityModule;
import java.util.List;

/**
 * Choob protection domain implementation.
 * Just shells out to modules.SecurityModule really.
 * @author bucko
 */
public class ChoobProtectionDomain extends ProtectionDomain
{
	private SecurityModule mod;
	private String pluginName;

	public ChoobProtectionDomain( SecurityModule mod, String pluginName )
	{
		super( null, null );
		this.mod = mod;
		this.pluginName = pluginName;
	}

	public boolean implies( Permission perm )
	{
		return mod.hasPluginPerm( perm, pluginName );
	}
}
