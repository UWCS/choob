/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class PrivateNotice extends Message implements PrivateEvent
{
	/**
	 * Get the reply context in which this event resides
	 * @return The context
	 */
	@Override
	public String getContext() {
		return getNick();
	}


	/**
	 * Construct a new PrivateNotice.
	 */
	public PrivateNotice(final String methodName, final long millis, final int random, final String message, final String nick, final String login, final String hostname, final String target)
	{
		super(methodName, millis, random, message, nick, login, hostname, target);
	}

	/**
	 * Synthesize a new PrivateNotice from an old one.
	 */
	public PrivateNotice(final PrivateNotice old, final String message)
	{
		super(old, message);
	}

	/**
	 * Synthesize a new PrivateNotice from this one.
	 * @return The new PrivateNotice object.
	 */
	@Override
	public Event cloneEvent(final String message)
	{
		return new PrivateNotice(this, message);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof PrivateNotice))
			return false;
		if ( !super.equals(obj) )
			return false;
			return true;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("PrivateNotice(");
		out.append(super.toString());
		out.append(")");
		return out.toString();
	}

}
