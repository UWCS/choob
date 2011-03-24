/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class PrivateMessage extends Message implements PrivateEvent, CommandEvent, FilterEvent
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
	 * Construct a new PrivateMessage.
	 */
	public PrivateMessage(final String methodName, final long millis, final int random, final String message, final String nick, final String login, final String hostname, final String target)
	{
		super(methodName, millis, random, message, nick, login, hostname, target);
	}

	/**
	 * Synthesize a new PrivateMessage from an old one.
	 */
	public PrivateMessage(final PrivateMessage old, final String message)
	{
		super(old, message);
	}

	/**
	 * Synthesize a new PrivateMessage from this one.
	 * @return The new PrivateMessage object.
	 */
	@Override
	public Event cloneEvent(final String message)
	{
		return new PrivateMessage(this, message);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof PrivateMessage))
			return false;
		if ( !super.equals(obj) )
			return false;
			return true;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("PrivateMessage(");
		out.append(super.toString());
		out.append(")");
		return out.toString();
	}

}
