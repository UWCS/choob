
package org.uwcs.choob.support;

public class Message extends anEvent
{

	/**
	 * Holds value of property channel.
	 */
	private String channel;

	/**
	 * Holds value of property privMessage.
	 */
	private boolean privMessage;

	/**
	 * Holds value of property text.
	 */
	private String text;


	/**
	 * Getter for property channel.
	 * @return Value of property channel.
	 */
	public String getChannel()
	{
		return this.channel;
	}

	/**
	 * Setter for property channel.
	 * @param channel New value of property channel.
	 */
	public void setChannel(String channel)
	{
		this.channel = channel;
	}

	/**
	 * Getter for property privMessage.
	 * @return Value of property privMessage.
	 */
	public boolean isPrivMessage()
	{
		return this.privMessage;
	}

	/**
	 * Setter for property privMessage.
	 * @param privMessage New value of property privMessage.
	 */
	public void setPrivMessage(boolean privMessage)
	{
		this.privMessage = privMessage;
	}

	/**
	 * Getter for property text.
	 * @return Value of property text.
	 */
	public String getText()
	{
		return this.text;
	}

	/**
	 * Setter for property text.
	 * @param text New value of property text.
	 */
	public void setText(String text)
	{
		this.text = text;
	}

	/**
	 * Constructs an object.
	 * @param nick
	 * @param channel
	 * @param text
	 * @param privMessage
	 * @param bot
	 */
	public Message(String nick, String channel, String text, boolean privMessage)
	{
		this.nick = nick;
		this.channel = channel;
		this.text = text;
		this.privMessage = privMessage;
		this.random = ((int)(Math.random()*127));
		this.millis = System.currentTimeMillis();
	}
}