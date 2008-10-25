/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;

public class PluginReLoaded extends InternalEvent implements PluginEvent
{
	/**
	 * pluginName
	 */
	private final String pluginName;

	/**
	 * Get the value of pluginName
	 * @return The value of pluginName
	 */
	public String getPluginName() {
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
	public int getPluginStatus() {
		 return pluginStatus;
	}


	/**
	 * Construct a new PluginReLoaded.
	 */
	public PluginReLoaded(final String methodName, final String pluginName, final int pluginStatus)
	{
		super(methodName);
		this.pluginName = pluginName;
		this.pluginStatus = pluginStatus;
	}

	/**
	 * Synthesize a new PluginReLoaded from an old one.
	 */
	public PluginReLoaded(final PluginReLoaded old)
	{
		super(old);
		this.pluginName = old.pluginName;
		this.pluginStatus = old.pluginStatus;
	}

	/**
	 * Synthesize a new PluginReLoaded from this one.
	 * @return The new PluginReLoaded object.
	 */
	@Override
	public Event cloneEvent()
	{
		return new PluginReLoaded(this);
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (obj == null || !(obj instanceof PluginReLoaded))
			return false;
		if ( !super.equals(obj) )
			return false;
		final PluginReLoaded thing = (PluginReLoaded)obj;
		if ( true && pluginName.equals(thing.pluginName) && pluginStatus == thing.pluginStatus )
			return true;
		return false;
	}

	@Override
	public String toString()
	{
		final StringBuffer out = new StringBuffer("PluginReLoaded(");
		out.append(super.toString());
		out.append(", pluginName = " + pluginName);
		out.append(", pluginStatus = " + pluginStatus);
		out.append(")");
		return out.toString();
	}

}
