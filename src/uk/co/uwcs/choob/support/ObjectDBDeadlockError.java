/**
 * Exception for Choob errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;

public class ObjectDBDeadlockError extends ObjectDBError
{
	public ObjectDBDeadlockError()
	{
		super("A deadlock occurred while processing this operation.");
	}
}
