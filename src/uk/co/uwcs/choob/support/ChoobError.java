/**
 * Exception for Choob errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;

public class ChoobError extends RuntimeException
{
	private static final long serialVersionUID = 1795297479611957869L;
	public ChoobError(String text)
	{
		super(text);
	}
	public ChoobError(String text, Throwable e)
	{
		super(text, e);
	}
	public String toString()
	{
		return getMessage();
	}
}
