/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public class ChannelParamMode extends ChannelMode implements ParamEvent
{
	/**
	 * set
	 */
	private boolean set;

	/**
	 * param
	 */
	private String param;


	/**
	 * Construct a new ChannelParamMode
	 */
	public ChannelParamMode(String methodName, String channel, String mode, boolean set, String param)
	{
		super(methodName, channel, mode, (boolean)set);
		this.set = set;
		this.param = param;

	}

	/**
	 * Synthesize a new ChannelParamMode from an old one.
	 */
	public ChannelParamMode(ChannelParamMode old)
	{
		super(old);
		this.set = old.set;
		this.param = old.param;

	}

	/**
	 * Get the value of set
	 * @returns The value of set
	 */
	public boolean isSet() {
		return set;
	}

	/**
	 * Get the value of param
	 * @returns The value of param
	 */
	public String getParam() {
		return param;
	}



}
