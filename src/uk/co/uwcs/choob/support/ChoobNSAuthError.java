/**
 * Exception for Choob plugin not found errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;

public final class ChoobNSAuthError extends ChoobAuthError {
	private static final long serialVersionUID = 626363002114671469L;

	public ChoobNSAuthError() {
		super(
				"I can't let you do that, Dave! You need to be identified with NickServ!");
	}
}
