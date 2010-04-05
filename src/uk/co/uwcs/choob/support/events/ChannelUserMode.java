/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class ChannelUserMode extends ChannelMode implements AimedEvent
{
	/**
	 * target
	 */
	private final String target;

	/**
	 * Get the value of target
	 * @return The value of target
	 */
	@Override public String getTarget() {
		 return target;
	}


	/**
	 * Construct a new ChannelUserMode.
	 */
	public ChannelUserMode(final String methodName, final long millis, final int random, final String channel, final String mode, final boolean set, final String target)
	{
		super(methodName, millis, random, channel, mode, set);
		this.target = target;
	}

	/**
	 * Synthesize a new ChannelUserMode from an old one.
	 */
	public ChannelUserMode(final ChannelUserMode old)
	{
		super(old);
		this.target = old.target;
	}

	/**
	 * Synthesize a new ChannelUserMode from this one.
	 * @return The new ChannelUserMode object.
	 */
	@Override
	public Event cloneEvent()
	{
		return new ChannelUserMode(this);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof ChannelUserMode))
			return false;
		if ( !super.equals(obj) )
			return false;
		final ChannelUserMode thing = (ChannelUserMode)obj;
		if ( true && target.equals(thing.target) )
			return true;
		return false;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("ChannelUserMode(");
		out.append(super.toString());
		out.append(", target = " + target);
		out.append(")");
		return out.toString();
	}

}
