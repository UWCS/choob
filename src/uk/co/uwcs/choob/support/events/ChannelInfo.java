/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;
import uk.co.uwcs.choob.support.events.*;

public class ChannelInfo extends IRCEvent implements MessageEvent, ChannelEvent
{
	/**
	 * message
	 */
	private final String message;

	/**
	 * Get the value of message
	 * @return The value of message
	 */
	public String getMessage() {
		 return message;
	}

	/**
	 * channel
	 */
	private final String channel;

	/**
	 * Get the value of channel
	 * @return The value of channel
	 */
	public String getChannel() {
		 return channel;
	}


	/**
	 * Construct a new ChannelInfo.
	 */
	public ChannelInfo(String methodName, long millis, int random, String message, String channel)
	{
		super(methodName, millis, random);
		this.message = message;
		this.channel = channel;
	}

	/**
	 * Synthesize a new ChannelInfo from an old one.
	 */
	public ChannelInfo(ChannelInfo old, String message)
	{
		super(old);
		this.message = message;
		this.channel = old.channel;
	}

	/**
	 * Synthesize a new ChannelInfo from this one.
	 * @return The new ChannelInfo object.
	 */
	public Event cloneEvent(String message)
	{
		return new ChannelInfo(this, message);
	}

	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof ChannelInfo))
			return false;
		if ( !super.equals(obj) )
			return false;
		ChannelInfo thing = (ChannelInfo)obj;
		if ( true && message.equals(thing.message) && channel.equals(thing.channel) )
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("ChannelInfo(");
		out.append(super.toString());
		out.append(", message = " + message);
		out.append(", channel = " + channel);
		out.append(")");
		return out.toString();
	}

}
