package uk.co.uwcs.choob.support;

public final class ChoobQAuthError extends ChoobAuthError {

	private static final long serialVersionUID = 125129581275812757L;

	public ChoobQAuthError() {
		super(
				"Permission denied. Valid Q authentication must first be obtained.");
	}
}
