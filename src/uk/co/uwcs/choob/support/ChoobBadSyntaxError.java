/**
 * Exception for Choob errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;

public class ChoobBadSyntaxError extends ChoobError {
	private static final long serialVersionUID = -8323767160761278430L;

	public ChoobBadSyntaxError() {
		super("Bad syntax!");
	}
}
