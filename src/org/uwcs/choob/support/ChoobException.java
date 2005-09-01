/**
 * Exception for Choob errors.
 * @author bucko
 */

package org.uwcs.choob.support;

public class ChoobException extends Exception
{
	public ChoobException(String text)
	{
		super(text);
	}
	public String toString()
	{
		return getMessage();
	}
}
