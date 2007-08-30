/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.event;

public class ChannelNotice extends Message implements ChannelEvent {
	/**
	 * channel
	 */
	private final String channel;

	/**
	 * Get the value of channel
	 * 
	 * @return The value of channel
	 */
	public String getChannel() {
		return channel;
	}

	/**
	 * Get the reply context in which this event resides
	 * 
	 * @return The context
	 */
	public String getContext() {
		return getChannel();
	}

	/**
	 * Construct a new ChannelNotice.
	 */
	public ChannelNotice(String methodName, long millis, int random,
			String message, String nick, String login, String hostname,
			String target, String channel) {
		super(methodName, millis, random, message, nick, login, hostname,
				target);
		this.channel = channel;
	}

	/**
	 * Synthesize a new ChannelNotice from an old one.
	 */
	public ChannelNotice(ChannelNotice old, String message) {
		super(old, message);
		this.channel = old.channel;
	}

	/**
	 * Synthesize a new ChannelNotice from this one.
	 * 
	 * @return The new ChannelNotice object.
	 */
	public Event cloneEvent(String message) {
		return new ChannelNotice(this, message);
	}

	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof ChannelNotice))
			return false;
		if (!super.equals(obj))
			return false;
		ChannelNotice thing = (ChannelNotice) obj;
		if (true && channel.equals(thing.channel))
			return true;
		return false;
	}

	public String toString() {
		StringBuffer out = new StringBuffer("ChannelNotice(");
		out.append(super.toString());
		out.append(", channel = " + channel);
		out.append(")");
		return out.toString();
	}

}
