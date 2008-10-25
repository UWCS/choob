package uk.co.uwcs.choob.support;

import java.security.Permission;
import java.security.ProtectionDomain;

import uk.co.uwcs.choob.modules.SecurityModule;

/**
 * Choob protection domain implementation.
 * Just shells out to modules.SecurityModule really.
 * @author bucko
 */
public final class ChoobProtectionDomain extends ProtectionDomain
{
	private final SecurityModule mod;
	private final String pluginName;

	public ChoobProtectionDomain( final SecurityModule mod, final String pluginName )
	{
		super( null, null );
		this.mod = mod;
		this.pluginName = pluginName;
	}

	@Override
	public boolean implies( final Permission perm )
	{
		// XXX HAX ATTACK XXX
		if ( perm instanceof ChoobSpecialStackPermission )
		{
			((ChoobSpecialStackPermission)perm).add(pluginName);
			return true;
		}
		return mod.hasPluginPerm( perm, pluginName );
	}
}
