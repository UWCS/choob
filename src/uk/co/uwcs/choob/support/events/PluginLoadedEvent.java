/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;
import uk.co.uwcs.choob.support.events.*;

public class PluginLoadedEvent extends InternalEvent implements PluginEvent
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
	 * Construct a new PluginLoadedEvent.
	 */
	public PluginLoadedEvent(String methodName, String pluginName, int pluginStatus)
	{
		super(methodName);
		this.pluginName = pluginName;
		this.pluginStatus = pluginStatus;
	}

	/**
	 * Synthesize a new PluginLoadedEvent from an old one.
	 */
	public PluginLoadedEvent(PluginLoadedEvent old)
	{
		super(old);
		this.pluginName = old.pluginName;
		this.pluginStatus = old.pluginStatus;
	}

	/**
	 * Synthesize a new PluginLoadedEvent from this one.
	 * @return The new PluginLoadedEvent object.
	 */
	public Event cloneEvent()
	{
		return new PluginLoadedEvent(this);
	}

	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof PluginLoadedEvent))
			return false;
		if ( !super.equals(obj) )
			return false;
		PluginLoadedEvent thing = (PluginLoadedEvent)obj;
		if ( true && pluginName.equals(thing.pluginName) && (pluginStatus == thing.pluginStatus) )
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("PluginLoadedEvent(");
		out.append(super.toString());
		out.append(", pluginName = " + pluginName);
		out.append(", pluginStatus = " + pluginStatus);
		out.append(")");
		return out.toString();
	}

}
