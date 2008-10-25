/*
 * Filter.java
 *
 * Created on June 16, 2005, 3:05 PM
 */

package uk.co.uwcs.choob.support;

/**
 *
 * @author	sadiq
 */
public final class Filter
{

	private String name;

	private String regex;

	private String plugin;

	/** Creates a new instance of Filter */
	public Filter( final String name, final String regex, final String plugin )
	{
		this.name = name;
		this.regex = regex;
		this.plugin = plugin;
	}

	/**
	 * Getter for property name.
	 * @return Value of property name.
	 */
	public java.lang.String getName()
	{
		return name;
	}

	/**
	 * Setter for property name.
	 * @param name New value of property name.
	 */
	public void setName(final java.lang.String name)
	{
		this.name = name;
	}

	/**
	 * Getter for property regex.
	 * @return Value of property regex.
	 */
	public java.lang.String getRegex()
	{
		return regex;
	}

	/**
	 * Setter for property regex.
	 * @param regex New value of property regex.
	 */
	public void setRegex(final java.lang.String regex)
	{
		this.regex = regex;
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

}
