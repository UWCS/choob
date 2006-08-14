/**
 * Exception for Choob errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;

public class ObjectDBDeadlockError extends ObjectDBError
{
	private static final long serialVersionUID = 3837611620666096677L;

	public ObjectDBDeadlockError()
	{
		super("A deadlock occurred while processing this operation.");
	}
}
