/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.event;

public class PrivateAction extends Message implements PrivateEvent,
		ActionEvent, FilterEvent {
	/**
	 * Get the reply context in which this event resides
	 * 
	 * @return The context
	 */
	public String getContext() {
		return getNick();
	}

	/**
	 * Construct a new PrivateAction.
	 */
	public PrivateAction(String methodName, long millis, int random,
			String message, String nick, String login, String hostname,
			String target) {
		super(methodName, millis, random, message, nick, login, hostname,
				target);
	}

	/**
	 * Synthesize a new PrivateAction from an old one.
	 */
	public PrivateAction(PrivateAction old, String message) {
		super(old, message);
	}

	/**
	 * Synthesize a new PrivateAction from this one.
	 * 
	 * @return The new PrivateAction object.
	 */
	public Event cloneEvent(String message) {
		return new PrivateAction(this, message);
	}

	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof PrivateAction))
			return false;
		if (!super.equals(obj))
			return false;
		PrivateAction thing = (PrivateAction) obj;
		if (true)
			return true;
		return false;
	}

	public String toString() {
		StringBuffer out = new StringBuffer("PrivateAction(");
		out.append(super.toString());
		out.append(")");
		return out.toString();
	}

}
