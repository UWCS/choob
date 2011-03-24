/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class UnknownEvent extends IRCEvent
{

	/**
	 * Construct a new UnknownEvent.
	 */
	public UnknownEvent(final String methodName, final long millis, final int random)
	{
		super(methodName, millis, random);
	}

	/**
	 * Synthesize a new UnknownEvent from an old one.
	 */
	public UnknownEvent(final UnknownEvent old)
	{
		super(old);
	}

	/**
	 * Synthesize a new UnknownEvent from this one.
	 * @return The new UnknownEvent object.
	 */
	@Override
	public Event cloneEvent()
	{
		return new UnknownEvent(this);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof UnknownEvent))
			return false;
		if ( !super.equals(obj) )
			return false;
			return true;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("UnknownEvent(");
		out.append(super.toString());
		out.append(")");
		return out.toString();
	}

}
