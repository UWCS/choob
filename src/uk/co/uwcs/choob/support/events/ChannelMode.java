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
	public String getChannel() {
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
	public String getMode() {
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
	public boolean isSet() {
		 return set;
	}


	/**
	 * Construct a new ChannelMode.
	 */
	public ChannelMode(String methodName, long millis, int random, String channel, String mode, boolean set)
	{
		super(methodName, millis, random);
		this.channel = channel;
		this.mode = mode;
		this.set = set;
	}

	/**
	 * Synthesize a new ChannelMode from an old one.
	 */
	public ChannelMode(ChannelMode old)
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
	public Event cloneEvent()
	{
		return new ChannelMode(this);
	}

	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof ChannelMode))
			return false;
		if ( !super.equals(obj) )
			return false;
		ChannelMode thing = (ChannelMode)obj;
		if ( true && channel.equals(thing.channel) && mode.equals(thing.mode) && (set == thing.set) )
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("ChannelMode(");
		out.append(super.toString());
		out.append(", channel = " + channel);
		out.append(", mode = " + mode);
		out.append(", set = " + set);
		out.append(")");
		return out.toString();
	}

}
