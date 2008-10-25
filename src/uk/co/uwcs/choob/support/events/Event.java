/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class Event
{
	/**
	 * methodName
	 */
	private final String methodName;

	/**
	 * Get the value of methodName
	 * @return The value of methodName
	 */
	public String getMethodName() {
		 return methodName;
	}


	/**
	 * Construct a new Event.
	 */
	public Event(final String methodName)
	{
		this.methodName = methodName;
	}

	/**
	 * Synthesize a new Event from an old one.
	 */
	public Event(final Event old)
	{
		this.methodName = old.methodName;
	}

	/**
	 * Synthesize a new Event from this one.
	 * @return The new Event object.
	 */
	public Event cloneEvent()
	{
		return new Event(this);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof Event))
			return false;
		final Event thing = (Event)obj;
		if ( true && methodName.equals(thing.methodName) )
			return true;
		return false;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("Event(");
		out.append(", methodName = " + methodName);
		out.append(")");
		return out.toString();
	}

}
