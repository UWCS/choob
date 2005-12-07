/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;

public class ChoobInternalError extends ChoobError
{
	public ChoobInternalError(String text, Throwable e)
	{
		super(text, e);
	}
	public ChoobInternalError(String text)
	{
		super(text);
	}
}
