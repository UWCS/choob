/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.co.uwcs.choob.support;

import uk.co.uwcs.choob.support.ChoobException;

/**
 *
 * @author benji
 */
public class HelpNotSpecifiedException extends ChoobException
{
	public HelpNotSpecifiedException()
	{
		super("HelpNotSpecified");
	}
}
