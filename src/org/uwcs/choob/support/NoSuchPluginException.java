/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package org.uwcs.choob.support;

public class NoSuchPluginException extends ChoobException
{
	public NoSuchPluginException(String text)
	{
		super(text);
	}
	public NoSuchPluginException(String text, Throwable e)
	{
		super(text, e);
	}
	public String toString()
	{
		return getMessage();
	}
}
