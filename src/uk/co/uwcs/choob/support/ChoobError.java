/**
 * Exception for Choob errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;

public class ChoobError extends RuntimeException
{
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
