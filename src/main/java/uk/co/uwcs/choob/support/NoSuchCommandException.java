package uk.co.uwcs.choob.support;

/**
 * 
 * @author benji
 */
public class NoSuchCommandException extends ChoobException
{
	public NoSuchCommandException(String commandName)
	{
		super(commandName);
	}
}
