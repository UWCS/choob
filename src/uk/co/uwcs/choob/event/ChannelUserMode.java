/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.event;

public class ChannelUserMode extends ChannelMode implements AimedEvent {
	/**
	 * target
	 */
	private final String target;

	/**
	 * Get the value of target
	 * 
	 * @return The value of target
	 */
	public String getTarget() {
		return target;
	}

	/**
	 * Construct a new ChannelUserMode.
	 */
	public ChannelUserMode(String methodName, long millis, int random,
			String channel, String mode, boolean set, String target) {
		super(methodName, millis, random, channel, mode, set);
		this.target = target;
	}

	/**
	 * Synthesize a new ChannelUserMode from an old one.
	 */
	public ChannelUserMode(ChannelUserMode old) {
		super(old);
		this.target = old.target;
	}

	/**
	 * Synthesize a new ChannelUserMode from this one.
	 * 
	 * @return The new ChannelUserMode object.
	 */
	public Event cloneEvent() {
		return new ChannelUserMode(this);
	}

	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof ChannelUserMode))
			return false;
		if (!super.equals(obj))
			return false;
		ChannelUserMode thing = (ChannelUserMode) obj;
		if (true && target.equals(thing.target))
			return true;
		return false;
	}

	public String toString() {
		StringBuffer out = new StringBuffer("ChannelUserMode(");
		out.append(super.toString());
		out.append(", target = " + target);
		out.append(")");
		return out.toString();
	}

}
