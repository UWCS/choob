/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class ChannelMode extends IRCEvent implements ChannelEvent, ModeEvent
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
	 * mode
	 */
	private final String mode;

	/**
	 * Get the value of mode
	 * @return The value of mode
	 */
	@Override public String getMode() {
		 return mode;
	}

	/**
	 * set
	 */
	private final boolean set;

	/**
	 * Get the value of set
	 * @return The value of set
	 */
	@Override public boolean isSet() {
		 return set;
	}


	/**
	 * Construct a new ChannelMode.
	 */
	public ChannelMode(final String methodName, final long millis, final int random, final String channel, final String mode, final boolean set)
	{
		super(methodName, millis, random);
		this.channel = channel;
		this.mode = mode;
		this.set = set;
	}

	/**
	 * Synthesize a new ChannelMode from an old one.
	 */
	public ChannelMode(final ChannelMode old)
	{
		super(old);
		this.channel = old.channel;
		this.mode = old.mode;
		this.set = old.set;
	}

	/**
	 * Synthesize a new ChannelMode from this one.
	 * @return The new ChannelMode object.
	 */
	@Override
	public Event cloneEvent()
	{
		return new ChannelMode(this);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof ChannelMode))
			return false;
		if ( !super.equals(obj) )
			return false;
		final ChannelMode thing = (ChannelMode)obj;
		if ( true && channel.equals(thing.channel) && mode.equals(thing.mode) && set == thing.set )
			return true;
		return false;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("ChannelMode(");
		out.append(super.toString());
		out.append(", channel = " + channel);
		out.append(", mode = " + mode);
		out.append(", set = " + set);
		out.append(")");
		return out.toString();
	}

}
