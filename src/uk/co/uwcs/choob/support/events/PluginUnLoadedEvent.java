/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;
import uk.co.uwcs.choob.support.events.*;

public class PluginUnLoadedEvent extends InternalEvent implements PluginEvent
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
	 * Construct a new PluginUnLoadedEvent.
	 */
	public PluginUnLoadedEvent(String methodName, String pluginName, int pluginStatus)
	{
		super(methodName);
		this.pluginName = pluginName;
		this.pluginStatus = pluginStatus;
	}

	/**
	 * Synthesize a new PluginUnLoadedEvent from an old one.
	 */
	public PluginUnLoadedEvent(PluginUnLoadedEvent old)
	{
		super(old);
		this.pluginName = old.pluginName;
		this.pluginStatus = old.pluginStatus;
	}

	/**
	 * Synthesize a new PluginUnLoadedEvent from this one.
	 * @return The new PluginUnLoadedEvent object.
	 */
	public Event cloneEvent()
	{
		return new PluginUnLoadedEvent(this);
	}

	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof PluginUnLoadedEvent))
			return false;
		if ( !super.equals(obj) )
			return false;
		PluginUnLoadedEvent thing = (PluginUnLoadedEvent)obj;
		if ( true && pluginName.equals(thing.pluginName) && (pluginStatus == thing.pluginStatus) )
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("PluginUnLoadedEvent(");
		out.append(super.toString());
		out.append(", pluginName = " + pluginName);
		out.append(", pluginStatus = " + pluginStatus);
		out.append(")");
		return out.toString();
	}

}
