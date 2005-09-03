import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.security.*;

/**
 * Choob plugin for fiddling with security privs
 * @author bucko
 */
class Security
{
	//public ChoobHelp help;
	public void create() {
		/* Possible help interface
		helpAddUser = new ChoobHelp(
				"[<username>]",
				"Add either your nickname or the specified username to the user database, as well as the group user.<username>.",
				"You need a ChoobPermission of user.add.<username> to add <username>.");
		helpAddGroup = new ChoobHelp(
				"<groupname>",
				"Add the named group to the database. See Security.HelpGroups for more information.",
				"You need a ChoobPermission of group.add.<groupname> to add <groupname>, unless the group is named user.<nickname>.<name> where <nickname> is your nickname.");
		helpAddToGroup = new ChoobHelp(
				"<child> <parentGroup>",
				"Add the named child (either a user or a group) to the group parentGroup.",
				"You need a ChoobPermission of group.members.<groupname> to add to <groupname>, unless the group is named user.<nickname>.<name> where <nickname> is your nickname.");
		helpRemoveFromGroup = new ChoobHelp(
				"<child> <parentGroup>",
				"Remove the named child (either a user or a group) from the group parentGroup.",
				"You need a ChoobPermission of group.members.<groupname> to remove from <groupname>, unless the group is named user.<nickname>.<name> where <nickname> is your nickname."); */
	}

	public void commandAddUser( Message mes, Modules mods, IRCInterface irc )
	{
		List params = mods.util.getParams( mes );

		String userName;
		if (params.size() == 1)
			userName = mes.getNick();
		else if (params.size() == 3)
		{
			// Hacky alias
			commandAddUserToGroup( mes, mods, irc );
			return;
		}
		else if (params.size() > 2)
		{
			irc.sendContextReply( mes, "You may only specify one user!" );
			return;
		}
		else
		{
			// Must check permission!
			userName = (String)params.get(1);
			// Sure, this will be checked for us. But what about the user who called us?
			if (! mods.security.hasPerm( new ChoobPermission("user.add." + userName) , mes.getNick() ) )
			{
				irc.sendContextReply( mes, "You don't have permission to add arbitrary users!" );
				return;
			}
		}
		// Can add the user...
		try
		{
			mods.security.addUser( userName );
		}
		catch ( ChoobException e )
		{
			irc.sendContextReply( mes, "The user could not be added: " + e );
			return;
		}
		catch ( SecurityException e )
		{
			irc.sendContextReply( mes, "Urgh. We got a security exception." );
			return;
		}
		irc.sendContextReply( mes, "OK, user added!" );
	}

	public void commandAddGroup( Message mes, Modules mods, IRCInterface irc )
	{
		List params = mods.util.getParams( mes );

		String groupName;
		if (params.size() != 2)
		{
			irc.sendContextReply( mes, "You must specify exactly one group!" );
			return;
		}
		else
		{
			// Must check permission!
			groupName = (String)params.get(1);

			// Sure, this will be checked for us. But what about the user who called us?
			int len = mes.getNick().length() + 6;
			if (groupName.length() > offset && groupName.regionMatches(true, 0, "user." + mes.getNick() + ".", 0, len))
			{
				// OK to add
			}
			else if (! mods.security.hasPerm( new ChoobPermission("group.add." + groupName) , mes.getNick() ) )
			{
				irc.sendContextReply( mes, "You don't have permission to add arbitrary groups!" );
				return;
			}
		}
		// Can add the user...
		try
		{
			mods.security.addGroup( groupName );
		}
		catch ( ChoobException e )
		{
			irc.sendContextReply( mes, "The group could not be added: " + e );
			return;
		}
		catch ( SecurityException e )
		{
			irc.sendContextReply( mes, "Urgh. We got a security exception." );
			return;
		}
		irc.sendContextReply( mes, "OK, group added!" );
	}

	//public ChoobHelp helpAddToGroup;
	public void commandAddToGroup( Message mes, Modules mods, IRCInterface irc )
	{
		this.doGroupMemberChange(mes, mods, irc, true);
	}

	public void commandRemoveFromGroup( Message mes, Modules mods, IRCInterface irc )
	{
		this.doGroupMemberChange(mes, mods, irc, false);
	}

	public void doGroupMemberChange( Message mes, Modules mods, IRCInterface irc, boolean isAdding )
	{
		List params = mods.util.getParams( mes );

		String childName;
		String parentName;
		boolean isGroup = false;
		if (params.size() != 3)
		{
			irc.sendContextReply( mes, "You must specify a child user/group and a parent group!" );
			return;
		}
		else
		{
			// Must check permission!
			childName = (String)params.get(1);
			if (childName.indexOf('.') != -1)
				isGroup = true;
			parentName = (String)params.get(2);
			// Sure, this will be checked for us. But what about the user who called us?
			int len = mes.getNick().length() + 6;
			if (parentName.length() > len && parentName.regionMatches(true, 0, "user." + mes.getNick() + ".", 0, len))
			{
				// OK to add
			}
			else if (! mods.security.hasPerm( new ChoobPermission("group.members." + groupName) , mes.getNick() ) )
			{
				irc.sendContextReply( mes, "You don't have permission to alter members of arbitrary groups!" );
				return;
			}
		}
		// Can add the user...
		try
		{
			if (isAdding)
			{
				if (isGroup)
					mods.security.addGroupToGroup( parentName, childName );
				else
					mods.security.addUserToGroup( parentName, childName );
			}
			else
			{
				if (isGroup)
					mods.security.removeGroupFromGroup( parentName, childName );
				else
					mods.security.removeUserFromGroup( parentName, childName );
			}
		}
		catch ( ChoobException e )
		{
			irc.sendContextReply( mes, "The membership could not be altered: " + e );
			return;
		}
		catch ( SecurityException e )
		{
			irc.sendContextReply( mes, "Urgh. We got a security exception." );
			return;
		}
		irc.sendContextReply( mes, "OK, membership altered!" );
	}

	private Permission makePermission(String permType, String permName, String permActions)
	{
		if (permType.toLowerCase().equals("choob")
				|| permType.toLowerCase().equals("choobpermission")
				|| permType.toLowerCase().equals("org.uwcs.choob.support.choobpermission"))
		{
			return new ChoobPermission(permName);
		}
		else if (permType.toLowerCase().equals("all")
				|| permType.toLowerCase().equals("allpermission")
				|| permType.toLowerCase().equals("java.security.allpermission"))
		{
			return new java.security.AllPermission();
		}
		return null;
	}

	public void commandGrant( Message mes, Modules mods, IRCInterface irc )
	{
		this.doPermChange( mes, mods, irc, true );
	}

	public void commandRevoke( Message mes, Modules mods, IRCInterface irc )
	{
		this.doPermChange( mes, mods, irc, false );
	}

	public void doPermChange( Message mes, Modules mods, IRCInterface irc, boolean isGranting )
	{
		List params = mods.util.getParams( mes );

		String actions = null;
		if (params.size() < 4 || params.size() > 5)
		{
			irc.sendContextReply( mes, "You must specify a child user/group and a parent group!" );
			return;
		}
		else if (params.size() == 5)
		{
			actions = (String)params.get(4);
		}
		String groupName = (String)params.get(1);
		String permType = (String)params.get(2);
		String permName = (String)params.get(3);

		int len = mes.getNick().length() + 6;
		String permString = isGranting ? "grant." : "revoke.";
		if (groupName.length() > len && groupName.regionMatches(true, 0, "user." + mes.getNick() + ".", 0, len))
		{
			// OK to add
		}
		else if (! mods.security.hasPerm( new ChoobPermission("group." + permString + groupName) , mes.getNick() ) )
		{
			irc.sendContextReply( mes, "You don't have permission to do that!" );
			return;
		}

		Permission permission = makePermission(permType, permName, actions);
		if (permission == null)
		{
			irc.sendContextReply( mes, "Unknown permission type: " + permType );
			return;
		}

		// Now check the user has the permission...
		if (isGranting && ! mods.security.hasPerm( permission, mes.getNick() ) )
		{
			irc.sendContextReply( mes, "You can only grant privileges you yourself have!" );
			return;
		}

		try
		{
			if ( isGranting )
				mods.security.grantPermission( groupName, permission );
			else
				mods.security.revokePermission( groupName, permission );
		}
		catch ( ChoobException e )
		{
			irc.sendContextReply( mes, "The permission could not be changed: " + e );
			return;
		}
		catch ( SecurityException e )
		{
			irc.sendContextReply( mes, "Urgh. We got a security exception." );
			return;
		}
		irc.sendContextReply( mes, "OK, permission changed!" );
	}

	public void commandFindPermission( Message mes, Modules mods, IRCInterface irc )
	{
		List params = mods.util.getParams( mes );

		String actions = null;
		if (params.size() < 4 || params.size() > 5)
		{
			irc.sendContextReply( mes, "You must specify a child user/group and a parent group!" );
			return;
		}
		else if (params.size() == 5)
		{
			actions = (String)params.get(4);
		}
		String groupName = (String)params.get(1);
		String permType = (String)params.get(2);
		String permName = (String)params.get(3);

		Permission permission = makePermission(permType, permName, actions);
		if (permission == null)
		{
			irc.sendContextReply( mes, "Unknown permission type: " + permType );
			return;
		}

		try
		{
			String[] perms = mods.security.findPermission( groupName, permission );
			if (perms.length == 0)
				irc.sendContextReply(mes, "The given group does not have this permission.");
			else
			{
				String reply = "Found permissions: ";
				for(int i=0; i<perms.length - 1; i++)
					reply += perms[i] + ",";
				reply += perms[perms.length - 1] + ".";
				irc.sendContextReply(mes, reply);
			}
		}
		catch ( ChoobException e )
		{
			irc.sendContextReply( mes, "The permission could not be found: " + e );
			return;
		}
		catch ( SecurityException e )
		{
			irc.sendContextReply( mes, "Urgh. We got a security exception." );
			return;
		}
	}
}
