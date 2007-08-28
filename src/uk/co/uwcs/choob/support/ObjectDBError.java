/**
 * Exception for Choob errors.
 * @author bucko
 */

package uk.co.uwcs.choob.support;

public class ObjectDBError extends ChoobError {
	private static final long serialVersionUID = 3844543968065225317L;

	public ObjectDBError(String text) {
		super(text);
	}

	public ObjectDBError(String text, Throwable e) {
		super(text, e);
	}
}
