/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package org.uwcs.choob.support;
import java.security.Permission;

public class ChoobNSAuthException extends ChoobAuthException
{
	public ChoobNSAuthException()
	{
		super("I can't let you do that, Dave! You need to be identified with NickServ!");
	}
}
