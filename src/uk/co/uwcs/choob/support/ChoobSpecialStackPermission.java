package uk.co.uwcs.choob.support;

import java.security.BasicPermission;
import java.util.List;

/**
 * Haxxy hack of a permission to enable us to get a stack trace of plugins.
 * @author bucko
 */

public final class ChoobSpecialStackPermission extends BasicPermission
{
	private List<String> haxList;
	private List<String> startList;
	public ChoobSpecialStackPermission(List<String> haxList)
	{
		super("HAX");
		this.haxList = haxList;
		this.startList = null;
	}

	public void add(String pluginName)
	{
		haxList.add(pluginName);
	}

	public void root(List<String> list)
	{
		startList = list;
	}

	public void patch()
	{
		if (startList != null)
			haxList.addAll(startList);
	}
}
