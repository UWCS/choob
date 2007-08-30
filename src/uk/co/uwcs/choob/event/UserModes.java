/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.event;

public class UserModes extends IRCEvent implements MultiModeEvent {
	/**
	 * modes
	 */
	private final String modes;

	/**
	 * Get the value of modes
	 * 
	 * @return The value of modes
	 */
	public String getModes() {
		return modes;
	}

	/**
	 * Construct a new UserModes.
	 */
	public UserModes(String methodName, long millis, int random, String modes) {
		super(methodName, millis, random);
		this.modes = modes;
	}

	/**
	 * Synthesize a new UserModes from an old one.
	 */
	public UserModes(UserModes old) {
		super(old);
		this.modes = old.modes;
	}

	/**
	 * Synthesize a new UserModes from this one.
	 * 
	 * @return The new UserModes object.
	 */
	public Event cloneEvent() {
		return new UserModes(this);
	}

	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof UserModes))
			return false;
		if (!super.equals(obj))
			return false;
		UserModes thing = (UserModes) obj;
		if (true && modes.equals(thing.modes))
			return true;
		return false;
	}

	public String toString() {
		StringBuffer out = new StringBuffer("UserModes(");
		out.append(super.toString());
		out.append(", modes = " + modes);
		out.append(")");
		return out.toString();
	}

}
