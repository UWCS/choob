/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class ChannelParamMode extends ChannelMode implements ParamEvent
{
	/**
	 * param
	 */
	private final String param;

	/**
	 * Get the value of param
	 * @return The value of param
	 */
	@Override public String getParam() {
		 return param;
	}


	/**
	 * Construct a new ChannelParamMode.
	 */
	public ChannelParamMode(final String methodName, final long millis, final int random, final String channel, final String mode, final boolean set, final String param)
	{
		super(methodName, millis, random, channel, mode, set);
		this.param = param;
	}

	/**
	 * Synthesize a new ChannelParamMode from an old one.
	 */
	public ChannelParamMode(final ChannelParamMode old)
	{
		super(old);
		this.param = old.param;
	}

	/**
	 * Synthesize a new ChannelParamMode from this one.
	 * @return The new ChannelParamMode object.
	 */
	@Override
	public Event cloneEvent()
	{
		return new ChannelParamMode(this);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof ChannelParamMode))
			return false;
		if ( !super.equals(obj) )
			return false;
		final ChannelParamMode thing = (ChannelParamMode)obj;
		if ( true && param.equals(thing.param) )
			return true;
		return false;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("ChannelParamMode(");
		out.append(super.toString());
		out.append(", param = " + param);
		out.append(")");
		return out.toString();
	}

}
