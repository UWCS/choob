package uk.co.uwcs.choob.support;

/**
 * 
 * @author benji
 */
public class NoSuchPluginException extends ChoobException
{
	public NoSuchPluginException(String pluginName)
	{
		super(pluginName);
	}
}
