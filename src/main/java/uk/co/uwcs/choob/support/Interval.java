/*
 * Interval.java
 *
 * Created on June 16, 2005, 3:06 PM
 */

package uk.co.uwcs.choob.support;

/**
 *
 * @author	sadiq
 */
public final class Interval
{
	private String plugin;

	private long trigger;

	private Object parameter;

	private int id;

	/** Creates a new instance of Interval */
	public Interval()
	{
	}

	public Interval(final String plugin, final Object parameter, final long trigger, final int id)
	{
		this.plugin = plugin;
		this.parameter = parameter;
		this.trigger = trigger;
		this.id = id;
	}

	/**
	 * Getter for property trigger.
	 * @return Value of property trigger.
	 */
	public long getTrigger()
	{
		return trigger;
	}

	/**
	 * Setter for property trigger.
	 * @param trigger New value of property trigger.
	 */
	public void setTrigger(final long trigger)
	{
		this.trigger = trigger;
	}

	/**
	 * Getter for property id.
	 * @return Value of property id.
	 */
	public int getId()
	{
		return id;
	}

	/**
	 * Setter for property id.
	 * @param id New value of property id.
	 */
	public void setId(final int id)
	{
		this.id = id;
	}

	/**
	 * Getter for property plugin.
	 * @return Value of property plugin.
	 */
	public java.lang.String getPlugin()
	{
		return plugin;
	}

	/**
	 * Setter for property plugin.
	 * @param plugin New value of property plugin.
	 */
	public void setPlugin(final java.lang.String plugin)
	{
		this.plugin = plugin;
	}

	/**
	 * Getter for property parameter.
	 * @return Value of property parameter.
	 */
	public java.lang.Object getParameter()
	{
		return parameter;
	}

	/**
	 * Setter for property parameter.
	 * @param parameter New value of property parameter.
	 */
	public void setParameter(final java.lang.Object parameter)
	{
		this.parameter = parameter;
	}

	@Override
	public String toString()
	{
		return "Interval( Date: " + getTrigger() + ", Plugin: " + getPlugin() +" )";
	}

}
