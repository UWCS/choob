/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class ChannelTopic extends IRCEvent implements MessageEvent, ChannelEvent, ContextEvent
{
	/**
	 * message
	 */
	private final String message;

	/**
	 * Get the value of message
	 * @return The value of message
	 */
	@Override public String getMessage() {
		 return message;
	}

	/**
	 * channel
	 */
	private final String channel;

	/**
	 * Get the value of channel
	 * @return The value of channel
	 */
	@Override public String getChannel() {
		 return channel;
	}

	/**
	 * Get the reply context in which this event resides
	 * @return The context
	 */
	@Override public String getContext() {
		return getChannel();
	}


	/**
	 * Construct a new ChannelTopic.
	 */
	public ChannelTopic(final String methodName, final long millis, final int random, final String message, final String channel)
	{
		super(methodName, millis, random);
		this.message = message;
		this.channel = channel;
	}

	/**
	 * Synthesize a new ChannelTopic from an old one.
	 */
	public ChannelTopic(final ChannelTopic old, final String message)
	{
		super(old);
		this.message = message;
		this.channel = old.channel;
	}

	/**
	 * Synthesize a new ChannelTopic from this one.
	 * @return The new ChannelTopic object.
	 */
	public Event cloneEvent(final String message)
	{
		return new ChannelTopic(this, message);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof ChannelTopic))
			return false;
		if ( !super.equals(obj) )
			return false;
		final ChannelTopic thing = (ChannelTopic)obj;
		if ( true && message.equals(thing.message) && channel.equals(thing.channel) )
			return true;
		return false;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("ChannelTopic(");
		out.append(super.toString());
		out.append(", message = " + message);
		out.append(", channel = " + channel);
		out.append(")");
		return out.toString();
	}

}
