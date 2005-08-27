
package org.uwcs.choob.support;

// Synthetic messages can be altered only in certain ways.
public final class SyntheticMessage extends Message
{

	/**
	 * Holds the "synthesis depth."
	 */
	private int synthLevel;

	/**
	 * Setter for property channel.
	 * @param channel New value of property channel.
	 */
	public void setChannel(String channel)
	{
		// XXX
	}

	/**
	 * Setter for property privMessage.
	 * @param privMessage New value of property privMessage.
	 */
	public void setPrivMessage(boolean privMessage)
	{
		// XXX
	}

	/**
	 * Setter for property nick.
	 * @return Value of property nick.
	 */
	public void setNick(String nick)
	{
		// XXX
	}

	/**
	 * Returns how deep in the synthesis tree this message is.
	 * @return Value of property text.
	 */
	public int getSynthLevel()
	{
		return synthLevel;
	}

	/**
	 * Constructs a synthetic message from an existing message.
	 * @param original
	 */
	public SyntheticMessage(Message original)
	{
		super(original.getNick(), original.getChannel(), original.getText(), original.isPrivMessage());
		this.random = ((int)(Math.random()*127));
		this.millis = System.currentTimeMillis();

		if (original instanceof SyntheticMessage)
			synthLevel = ((SyntheticMessage)original).getSynthLevel() + 1;
		else
			synthLevel = 1;
	}
}
