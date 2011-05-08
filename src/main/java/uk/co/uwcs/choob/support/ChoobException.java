/**
 * Exception for Choob errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;


public class ChoobException extends RuntimeException
{
	private static final long serialVersionUID = 5447830036241630751L;
	public ChoobException(final String text)
	{
		super(text);
	}
	public ChoobException(final String text, final Throwable e)
	{
		super(text, e);
	}
	public ChoobException(final Throwable t)
	{
		super(t);
	}
	@Override
	public String toString()
	{
		return getMessage();
	}
}
