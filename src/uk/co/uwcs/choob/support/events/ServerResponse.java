/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class ServerResponse extends IRCEvent implements ServerEvent
{
	/**
	 * code
	 */
	private final int code;

	/**
	 * Get the value of code
	 * @return The value of code
	 */
	public int getCode() {
		 return code;
	}

	/**
	 * response
	 */
	private final String response;

	/**
	 * Get the value of response
	 * @return The value of response
	 */
	public String getResponse() {
		 return response;
	}


	/**
	 * Construct a new ServerResponse.
	 */
	public ServerResponse(final String methodName, final long millis, final int random, final int code, final String response)
	{
		super(methodName, millis, random);
		this.code = code;
		this.response = response;
	}

	/**
	 * Synthesize a new ServerResponse from an old one.
	 */
	public ServerResponse(final ServerResponse old)
	{
		super(old);
		this.code = old.code;
		this.response = old.response;
	}

	/**
	 * Synthesize a new ServerResponse from this one.
	 * @return The new ServerResponse object.
	 */
	@Override
	public Event cloneEvent()
	{
		return new ServerResponse(this);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof ServerResponse))
			return false;
		if ( !super.equals(obj) )
			return false;
		final ServerResponse thing = (ServerResponse)obj;
		if ( true && code == thing.code && response.equals(thing.response) )
			return true;
		return false;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("ServerResponse(");
		out.append(super.toString());
		out.append(", code = " + code);
		out.append(", response = " + response);
		out.append(")");
		return out.toString();
	}

}
