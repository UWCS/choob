/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class ChannelMessage extends Message implements ChannelEvent, CommandEvent, FilterEvent
{
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
	@Override
	public String getContext() {
		return getChannel();
	}


	/**
	 * Construct a new ChannelMessage.
	 */
	public ChannelMessage(final String methodName, final long millis, final int random, final String message, final String nick, final String login, final String hostname, final String target, final String channel)
	{
		super(methodName, millis, random, message, nick, login, hostname, target);
		this.channel = channel;
	}

	/**
	 * Synthesize a new ChannelMessage from an old one.
	 */
	public ChannelMessage(final ChannelMessage old, final String message)
	{
		super(old, message);
		this.channel = old.channel;
	}

	/**
	 * Synthesize a new ChannelMessage from this one.
	 * @return The new ChannelMessage object.
	 */
	@Override
	public Event cloneEvent(final String message)
	{
		return new ChannelMessage(this, message);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof ChannelMessage))
			return false;
		if ( !super.equals(obj) )
			return false;
		final ChannelMessage thing = (ChannelMessage)obj;
		if ( true && channel.equals(thing.channel) )
			return true;
		return false;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("ChannelMessage(");
		out.append(super.toString());
		out.append(", channel = " + channel);
		out.append(")");
		return out.toString();
	}

}
