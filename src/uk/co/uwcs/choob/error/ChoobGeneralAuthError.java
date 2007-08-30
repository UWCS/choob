package uk.co.uwcs.choob.error;


public class ChoobGeneralAuthError extends ChoobAuthError {
	private static final long serialVersionUID = 748596321456987854L;

	public ChoobGeneralAuthError() {
		super("Permission denied. Valid authentication must first be obtained.");
	}
}
