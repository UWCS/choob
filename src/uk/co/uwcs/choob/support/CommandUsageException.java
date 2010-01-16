/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.co.uwcs.choob.support;

/**
 *
 * @author benji
 */
public class CommandUsageException extends ChoobException
{
	public CommandUsageException(String usage)
	{
		super(usage);
	}
}
