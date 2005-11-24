/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;
import uk.co.uwcs.choob.support.events.*;

public class InternalEvent extends Event implements InternalRootEvent
{

	/**
	 * Construct a new InternalEvent.
	 */
	public InternalEvent(String methodName)
	{
		super(methodName);
	}

	/**
	 * Synthesize a new InternalEvent from an old one.
	 */
	public InternalEvent(InternalEvent old)
	{
		super(old);
	}

	/**
	 * Synthesize a new InternalEvent from this one.
	 * @return The new InternalEvent object.
	 */
	public Event cloneEvent()
	{
		return new InternalEvent(this);
	}

	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof InternalEvent))
			return false;
		if ( !super.equals(obj) )
			return false;
		InternalEvent thing = (InternalEvent)obj;
		if ( true )
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("InternalEvent(");
		out.append(super.toString());
		out.append(")");
		return out.toString();
	}

}
