/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class PluginUnLoaded extends InternalEvent implements PluginEvent
{
	/**
	 * pluginName
	 */
	private final String pluginName;

	/**
	 * Get the value of pluginName
	 * @return The value of pluginName
	 */
	@Override public String getPluginName() {
		 return pluginName;
	}

	/**
	 * pluginStatus
	 */
	private final int pluginStatus;

	/**
	 * Get the value of pluginStatus
	 * @return The value of pluginStatus
	 */
	@Override public int getPluginStatus() {
		 return pluginStatus;
	}


	/**
	 * Construct a new PluginUnLoaded.
	 */
	public PluginUnLoaded(final String methodName, final String pluginName, final int pluginStatus)
	{
		super(methodName);
		this.pluginName = pluginName;
		this.pluginStatus = pluginStatus;
	}

	/**
	 * Synthesize a new PluginUnLoaded from an old one.
	 */
	public PluginUnLoaded(final PluginUnLoaded old)
	{
		super(old);
		this.pluginName = old.pluginName;
		this.pluginStatus = old.pluginStatus;
	}

	/**
	 * Synthesize a new PluginUnLoaded from this one.
	 * @return The new PluginUnLoaded object.
	 */
	@Override
	public Event cloneEvent()
	{
		return new PluginUnLoaded(this);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof PluginUnLoaded))
			return false;
		if ( !super.equals(obj) )
			return false;
		final PluginUnLoaded thing = (PluginUnLoaded)obj;
		if ( true && pluginName.equals(thing.pluginName) && pluginStatus == thing.pluginStatus )
			return true;
		return false;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("PluginUnLoaded(");
		out.append(super.toString());
		out.append(", pluginName = " + pluginName);
		out.append(", pluginStatus = " + pluginStatus);
		out.append(")");
		return out.toString();
	}

}
