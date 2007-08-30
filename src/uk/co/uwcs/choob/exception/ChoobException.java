/**
 * Exception for Choob errors.
 * @author bucko
 */

package uk.co.uwcs.choob.exception;

public class ChoobException extends Exception {
	private static final long serialVersionUID = 5447830036241630751L;

	public ChoobException(String text) {
		super(text);
	}

	public ChoobException(String text, Throwable e) {
		super(text, e);
	}

	public String toString() {
		return getMessage();
	}
}
