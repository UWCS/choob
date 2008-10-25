/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class InternalEvent extends Event implements InternalRootEvent
{

	/**
	 * Construct a new InternalEvent.
	 */
	public InternalEvent(final String methodName)
	{
		super(methodName);
	}

	/**
	 * Synthesize a new InternalEvent from an old one.
	 */
	public InternalEvent(final InternalEvent old)
	{
		super(old);
	}

	/**
	 * Synthesize a new InternalEvent from this one.
	 * @return The new InternalEvent object.
	 */
	@Override
	public Event cloneEvent()
	{
		return new InternalEvent(this);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof InternalEvent))
			return false;
		if ( !super.equals(obj) )
			return false;
			return true;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("InternalEvent(");
		out.append(super.toString());
		out.append(")");
		return out.toString();
	}

}
