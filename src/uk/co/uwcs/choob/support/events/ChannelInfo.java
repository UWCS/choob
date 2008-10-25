/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class ChannelInfo extends IRCEvent implements MessageEvent, ChannelEvent
{
	/**
	 * message
	 */
	private final String message;

	/**
	 * Get the value of message
	 * @return The value of message
	 */
	public String getMessage() {
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
	public String getChannel() {
		 return channel;
	}


	/**
	 * Construct a new ChannelInfo.
	 */
	public ChannelInfo(final String methodName, final long millis, final int random, final String message, final String channel)
	{
		super(methodName, millis, random);
		this.message = message;
		this.channel = channel;
	}

	/**
	 * Synthesize a new ChannelInfo from an old one.
	 */
	public ChannelInfo(final ChannelInfo old, final String message)
	{
		super(old);
		this.message = message;
		this.channel = old.channel;
	}

	/**
	 * Synthesize a new ChannelInfo from this one.
	 * @return The new ChannelInfo object.
	 */
	public Event cloneEvent(final String message)
	{
		return new ChannelInfo(this, message);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof ChannelInfo))
			return false;
		if ( !super.equals(obj) )
			return false;
		final ChannelInfo thing = (ChannelInfo)obj;
		if ( true && message.equals(thing.message) && channel.equals(thing.channel) )
			return true;
		return false;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("ChannelInfo(");
		out.append(super.toString());
		out.append(", message = " + message);
		out.append(", channel = " + channel);
		out.append(")");
		return out.toString();
	}

}
