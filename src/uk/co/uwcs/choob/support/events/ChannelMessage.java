/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class ChannelMessage extends Message implements ChannelEvent,
		CommandEvent, FilterEvent {
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
	 * Construct a new ChannelMessage.
	 */
	public ChannelMessage(String methodName, long millis, int random,
			String message, String nick, String login, String hostname,
			String target, String channel) {
		super(methodName, millis, random, message, nick, login, hostname,
				target);
		this.channel = channel;
	}

	/**
	 * Synthesize a new ChannelMessage from an old one.
	 */
	public ChannelMessage(ChannelMessage old, String message) {
		super(old, message);
		this.channel = old.channel;
	}

	/**
	 * Synthesize a new ChannelMessage from this one.
	 * 
	 * @return The new ChannelMessage object.
	 */
	public Event cloneEvent(String message) {
		return new ChannelMessage(this, message);
	}

	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof ChannelMessage))
			return false;
		if (!super.equals(obj))
			return false;
		ChannelMessage thing = (ChannelMessage) obj;
		if (true && channel.equals(thing.channel))
			return true;
		return false;
	}

	public String toString() {
		StringBuffer out = new StringBuffer("ChannelMessage(");
		out.append(super.toString());
		out.append(", channel = " + channel);
		out.append(")");
		return out.toString();
	}

}
