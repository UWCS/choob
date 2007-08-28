/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class ChannelAction extends Message implements ChannelEvent,
		ActionEvent, FilterEvent {
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
	 * Construct a new ChannelAction.
	 */
	public ChannelAction(String methodName, long millis, int random,
			String message, String nick, String login, String hostname,
			String target, String channel) {
		super(methodName, millis, random, message, nick, login, hostname,
				target);
		this.channel = channel;
	}

	/**
	 * Synthesize a new ChannelAction from an old one.
	 */
	public ChannelAction(ChannelAction old, String message) {
		super(old, message);
		this.channel = old.channel;
	}

	/**
	 * Synthesize a new ChannelAction from this one.
	 * 
	 * @return The new ChannelAction object.
	 */
	public Event cloneEvent(String message) {
		return new ChannelAction(this, message);
	}

	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof ChannelAction))
			return false;
		if (!super.equals(obj))
			return false;
		ChannelAction thing = (ChannelAction) obj;
		if (true && channel.equals(thing.channel))
			return true;
		return false;
	}

	public String toString() {
		StringBuffer out = new StringBuffer("ChannelAction(");
		out.append(super.toString());
		out.append(", channel = " + channel);
		out.append(")");
		return out.toString();
	}

}
