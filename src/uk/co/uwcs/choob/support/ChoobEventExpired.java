/**
 * Exception for when events have expired, and hence are no longer valid for security checks.
 */

package uk.co.uwcs.choob.support;

public class ChoobEventExpired extends ChoobError
{
	private static final long serialVersionUID = -5489466339452958618L;
	public ChoobEventExpired(String text)
	{
		super(text);
	}
	public ChoobEventExpired(String text, Throwable e)
	{
		super(text, e);
	}
	public String toString()
	{
		return getMessage();
	}
}
