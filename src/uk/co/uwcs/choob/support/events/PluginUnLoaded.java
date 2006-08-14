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
	 * Construct a new PluginUnLoaded.
	 */
	public PluginUnLoaded(String methodName, String pluginName, int pluginStatus)
	{
		super(methodName);
		this.pluginName = pluginName;
		this.pluginStatus = pluginStatus;
	}

	/**
	 * Synthesize a new PluginUnLoaded from an old one.
	 */
	public PluginUnLoaded(PluginUnLoaded old)
	{
		super(old);
		this.pluginName = old.pluginName;
		this.pluginStatus = old.pluginStatus;
	}

	/**
	 * Synthesize a new PluginUnLoaded from this one.
	 * @return The new PluginUnLoaded object.
	 */
	public Event cloneEvent()
	{
		return new PluginUnLoaded(this);
	}

	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof PluginUnLoaded))
			return false;
		if ( !super.equals(obj) )
			return false;
		PluginUnLoaded thing = (PluginUnLoaded)obj;
		if ( true && pluginName.equals(thing.pluginName) && (pluginStatus == thing.pluginStatus) )
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("PluginUnLoaded(");
		out.append(super.toString());
		out.append(", pluginName = " + pluginName);
		out.append(", pluginStatus = " + pluginStatus);
		out.append(")");
		return out.toString();
	}

}
