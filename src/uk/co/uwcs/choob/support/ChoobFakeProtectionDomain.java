package uk.co.uwcs.choob.support;

import java.security.ProtectionDomain;
import java.security.Permission;
import uk.co.uwcs.choob.modules.SecurityModule;
import java.util.List;

/**
 * Fake protection domain. Implies every permission, but does the
 * ChoobSpecialStackPermission hack. This is used in doAPI calls.
 * Just shells out to modules.SecurityModule really.
 * @author bucko
 */
public final class ChoobFakeProtectionDomain extends ProtectionDomain
{
	private SecurityModule mod;
	private List<String> pluginNames;

	public ChoobFakeProtectionDomain( List<String> pluginNames )
	{
		super( null, null );
		this.pluginNames = pluginNames;
	}

	public boolean implies( Permission perm )
	{
		// XXX HAX ATTACK XXX
		if ( perm instanceof ChoobSpecialStackPermission )
		{
			((ChoobSpecialStackPermission)perm).root(pluginNames);
		}
		return true;
	}
}
