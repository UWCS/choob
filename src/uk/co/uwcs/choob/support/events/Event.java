/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;
import uk.co.uwcs.choob.support.events.*;

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
	public Event(String methodName)
	{
		this.methodName = methodName;
	}

	/**
	 * Synthesize a new Event from an old one.
	 */
	public Event(Event old)
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

	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof Event))
			return false;
		Event thing = (Event)obj;
		if ( true && methodName.equals(thing.methodName) )
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("Event(");
		out.append(", methodName = " + methodName);
		out.append(")");
		return out.toString();
	}

}
