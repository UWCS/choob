/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class PrivateAction extends Message implements PrivateEvent, ActionEvent, FilterEvent
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
	 * Construct a new PrivateAction.
	 */
	public PrivateAction(final String methodName, final long millis, final int random, final String message, final String nick, final String login, final String hostname, final String target)
	{
		super(methodName, millis, random, message, nick, login, hostname, target);
	}

	/**
	 * Synthesize a new PrivateAction from an old one.
	 */
	public PrivateAction(final PrivateAction old, final String message)
	{
		super(old, message);
	}

	/**
	 * Synthesize a new PrivateAction from this one.
	 * @return The new PrivateAction object.
	 */
	@Override
	public Event cloneEvent(final String message)
	{
		return new PrivateAction(this, message);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof PrivateAction))
			return false;
		if ( !super.equals(obj) )
			return false;
			return true;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("PrivateAction(");
		out.append(super.toString());
		out.append(")");
		return out.toString();
	}

}
