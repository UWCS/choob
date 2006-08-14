/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;

public class ChoobInternalError extends ChoobError
{
	private static final long serialVersionUID = 8597707175814703653L;
	public ChoobInternalError(String text, Throwable e)
	{
		super(text, e);
	}
	public ChoobInternalError(String text)
	{
		super(text);
	}
}
