package uk.co.uwcs.choob.support;

import java.security.Permission;
import java.security.ProtectionDomain;
import java.util.List;

/**
 * Fake protection domain. Implies every permission, but does the
 * ChoobSpecialStackPermission hack. This is used in doAPI calls.
 * Just shells out to modules.SecurityModule really.
 * @author bucko
 */
public final class ChoobFakeProtectionDomain extends ProtectionDomain
{
	private final List<String> pluginNames;

	public ChoobFakeProtectionDomain( final List<String> pluginNames )
	{
		super( null, null );
		this.pluginNames = pluginNames;
	}

	@Override
	public boolean implies( final Permission perm )
	{
		// XXX HAX ATTACK XXX
		if ( perm instanceof ChoobSpecialStackPermission )
		{
			((ChoobSpecialStackPermission)perm).root(pluginNames);
		}
		return true;
	}
}
