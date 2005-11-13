/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package org.uwcs.choob.support;

public final class ChoobNSAuthError extends ChoobAuthError
{
	public ChoobNSAuthError()
	{
		super("I can't let you do that, Dave! You need to be identified with NickServ!");
	}
}
