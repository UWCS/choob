/*
 * Interval.java
 *
 * Created on June 16, 2005, 3:06 PM
 */

package org.uwcs.choob.support;

import java.util.*;

/**
 *
 * @author	sadiq
 */
public class Interval
{
	private String plugin;

	private Date trigger;

	private Object parameter;

	/** Creates a new instance of Interval */
	public Interval()
	{
	}

	/**
	 * Getter for property trigger.
	 * @return Value of property trigger.
	 */
	public java.util.Date getTrigger()
	{
		return trigger;
	}

	/**
	 * Setter for property trigger.
	 * @param trigger New value of property trigger.
	 */
	public void setTrigger(java.util.Date trigger)
	{
		this.trigger = trigger;
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
	public void setPlugin(java.lang.String plugin)
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
	public void setParameter(java.lang.Object parameter)
	{
		this.parameter = parameter;
	}

	public String toString()
	{
		return "Interval( Date: " + getTrigger() + ", Plugin: " + getPlugin() +" )";
	}

}
