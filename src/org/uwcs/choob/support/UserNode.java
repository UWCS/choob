/**
 * Choob class encapsulating a user/group node.
 * @author bucko
 */
package org.uwcs.choob.support;

public final class UserNode
{
	private final String nodeName;
	private final String rootName;
	private final int nodeType;

	public UserNode(String groupName)
	{
		this(groupName, false);
	}

	public UserNode(String nodeName, int nodeType)
	{
		this.nodeType = nodeType;
		if ( nodeType == 0 )
		{
			this.rootName = null;
			this.nodeName = nodeName;
		}
		else if (nodeType < 0 || nodeType > 3)
		{
			throw new IllegalArgumentException("Invalid node type: " + nodeType);
		}
		else
		{
			int pos = nodeName.indexOf('.');

			if (pos != -1)
				this.rootName = nodeName.substring(0, pos);
			else
				this.rootName = nodeName;

			this.nodeName = nodeName;
		}
	}

	public UserNode(String groupName, boolean isUser)
	{
		if ( isUser )
		{
			nodeType = 0;
			rootName = null;
			nodeName = groupName;
		}
		else
		{
			String[] parts = groupName.split("\\.");

			if (parts.length < 2)
				throw new IllegalArgumentException("Invalid group name: " + groupName);

			if (parts[0].toLowerCase().equals("user"))
				nodeType = 1;
			else if (parts[0].toLowerCase().equals("plugin"))
				nodeType = 2;
			else if (parts[0].toLowerCase().equals("system"))
				nodeType = 3;
			else
				throw new IllegalArgumentException("Invalid group name: " + groupName);

			rootName = parts[1];

			String nodeTmp = parts[1];
			for(int i=2; i<parts.length; i++)
				nodeTmp = nodeTmp.concat("." + parts[i]);
			nodeName = nodeTmp;
		}
	}

	public String getName()
	{
		return nodeName;
	}

	public int getType()
	{
		return nodeType;
	}

	public String getRootName()
	{
		return rootName;
	}

	public String toString()
	{
		if (nodeType == 0) return nodeName;
		else if (nodeType == 1) return "user." + nodeName;
		else if (nodeType == 2) return "plugin." + nodeName;
		else if (nodeType == 3) return "system." + nodeName;
		return "unknown("+nodeName+")";
	}
}
