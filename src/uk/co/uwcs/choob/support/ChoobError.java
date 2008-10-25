/**
 * Exception for Choob errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;

public class ChoobError extends RuntimeException
{
	private static final long serialVersionUID = 1795297479611957869L;
	public ChoobError(final String text)
	{
		super(text);
	}
	public ChoobError(final String text, final Throwable e)
	{
		super(text, e);
	}
	@Override
	public String toString()
	{
		return getMessage();
	}
}
