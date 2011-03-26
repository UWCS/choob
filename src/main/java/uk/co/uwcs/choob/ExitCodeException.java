package uk.co.uwcs.choob;

public class ExitCodeException extends RuntimeException {
	private final int code;

	public ExitCodeException(int code) {
		super("exiting: " + code);
		this.code = code;
	}
}
