
package org.uwcs.choob.support;

public class anEvent
{

	public anEvent()
	{
		this.random = ((int)(Math.random()*127));
		this.millis = System.currentTimeMillis();
	}

	public anEvent(String nick)
	{
		this.nick = nick;
		this.random = ((int)(Math.random()*127));
		this.millis = System.currentTimeMillis();
	}


	/**
	 * Holds the timestamp for the line from IRC.
	 */
	protected long millis;

	/**
	 * Holds value of property nick.
	 */
	protected String nick;

	/**
	 * Random number used to differentiate two lines from IRC sent in quick succession
	 * on systems with a crap timer.
	 */
	protected int random;

	/**
	 * Holds value of property channel.
	 */
	private String channel;

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
	 * Getter for property {@link millis}.
	 * @return Value of property millis.
	 */
	public long getMillis()
	{
		return this.millis;
	}

	/**
	 * Setter for property {@link millis}.
	 * @param millis New value of property millis.
	 */
	public void setMillis(long millis)
	{
		this.millis = millis;
	}

	/**
	 * Getter for property nick.
	 * @return Value of property nick.
	 */
	public String getNick()
	{
		return this.nick;
	}

	/**
	 * Setter for property nick.
	 * @param nick New value of property nick.
	 */
	public void setNick(String nick)
	{
		this.nick = nick;
	}

	/**
	 * Getter for property {@link random}.
	 * @return Value of property random.
	 */
	public int getRandom()
	{
		return this.random;
	}

	/**
	 * Setter for property random.
	 * @param random New value of property random.
	 */
	public void setRandom(int random)
	{
		this.random = random;
	}
}
