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
	private List domainStack;

	public ChoobProtectionDomain( SecurityModule mod, String pluginName, List domainStack )
	{
		super( null, null );
		this.mod = mod;
		this.pluginName = pluginName;
		this.domainStack = domainStack;
	}

	public boolean implies( Permission perm )
	{
		// XXX HAX ATTACK XXX
		if ( perm instanceof ChoobSpecialStackPermission )
		{
			List toStack = ((ChoobSpecialStackPermission)perm).getHaxList();
			toStack.addAll(domainStack);
			return true;
		}
		return mod.hasPluginPerm( perm, pluginName );
	}
}
