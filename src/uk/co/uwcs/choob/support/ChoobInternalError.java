/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;

public class ChoobInternalError extends ChoobError
{
	private static final long serialVersionUID = 8597707175814703653L;
	public ChoobInternalError(final String text, final Throwable e)
	{
		super(text, e);
	}
	public ChoobInternalError(final String text)
	{
		super(text);
	}
}
