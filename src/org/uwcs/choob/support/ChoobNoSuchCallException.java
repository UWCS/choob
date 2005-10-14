/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package org.uwcs.choob.support;

public class ChoobNoSuchCallException extends ChoobException
{
	private String call;
	public ChoobNoSuchCallException(String call)
	{
		super("The plugin call " + call + " did not exist!");
		this.call = call;
	}
	public String getCall()
	{
		return call;
	}
}
