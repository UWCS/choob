/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.event;

public class UnknownEvent extends IRCEvent {

	/**
	 * Construct a new UnknownEvent.
	 */
	public UnknownEvent(String methodName, long millis, int random) {
		super(methodName, millis, random);
	}

	/**
	 * Synthesize a new UnknownEvent from an old one.
	 */
	public UnknownEvent(UnknownEvent old) {
		super(old);
	}

	/**
	 * Synthesize a new UnknownEvent from this one.
	 * 
	 * @return The new UnknownEvent object.
	 */
	public Event cloneEvent() {
		return new UnknownEvent(this);
	}

	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof UnknownEvent))
			return false;
		if (!super.equals(obj))
			return false;
		UnknownEvent thing = (UnknownEvent) obj;
		if (true)
			return true;
		return false;
	}

	public String toString() {
		StringBuffer out = new StringBuffer("UnknownEvent(");
		out.append(super.toString());
		out.append(")");
		return out.toString();
	}

}
