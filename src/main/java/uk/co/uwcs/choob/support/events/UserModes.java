/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class UserModes extends IRCEvent implements MultiModeEvent
{
	/**
	 * modes
	 */
	private final String modes;

	/**
	 * Get the value of modes
	 * @return The value of modes
	 */
	@Override public String getModes() {
		 return modes;
	}


	/**
	 * Construct a new UserModes.
	 */
	public UserModes(final String methodName, final long millis, final int random, final String modes)
	{
		super(methodName, millis, random);
		this.modes = modes;
	}

	/**
	 * Synthesize a new UserModes from an old one.
	 */
	public UserModes(final UserModes old)
	{
		super(old);
		this.modes = old.modes;
	}

	/**
	 * Synthesize a new UserModes from this one.
	 * @return The new UserModes object.
	 */
	@Override
	public Event cloneEvent()
	{
		return new UserModes(this);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof UserModes))
			return false;
		if ( !super.equals(obj) )
			return false;
		final UserModes thing = (UserModes)obj;
		if ( true && modes.equals(thing.modes) )
			return true;
		return false;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("UserModes(");
		out.append(super.toString());
		out.append(", modes = " + modes);
		out.append(")");
		return out.toString();
	}

}
