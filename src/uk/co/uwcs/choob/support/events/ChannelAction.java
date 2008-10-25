/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class ChannelAction extends Message implements ChannelEvent, ActionEvent, FilterEvent
{
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
	 * Get the reply context in which this event resides
	 * @return The context
	 */
	@Override
	public String getContext() {
		return getChannel();
	}


	/**
	 * Construct a new ChannelAction.
	 */
	public ChannelAction(final String methodName, final long millis, final int random, final String message, final String nick, final String login, final String hostname, final String target, final String channel)
	{
		super(methodName, millis, random, message, nick, login, hostname, target);
		this.channel = channel;
	}

	/**
	 * Synthesize a new ChannelAction from an old one.
	 */
	public ChannelAction(final ChannelAction old, final String message)
	{
		super(old, message);
		this.channel = old.channel;
	}

	/**
	 * Synthesize a new ChannelAction from this one.
	 * @return The new ChannelAction object.
	 */
	@Override
	public Event cloneEvent(final String message)
	{
		return new ChannelAction(this, message);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof ChannelAction))
			return false;
		if ( !super.equals(obj) )
			return false;
		final ChannelAction thing = (ChannelAction)obj;
		if ( true && channel.equals(thing.channel) )
			return true;
		return false;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("ChannelAction(");
		out.append(super.toString());
		out.append(", channel = " + channel);
		out.append(")");
		return out.toString();
	}

}
