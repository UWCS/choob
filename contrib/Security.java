import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.security.*;
import java.util.*;
import java.util.regex.*;

/**
 * Choob plugin for fiddling with security privs
 * @author bucko
 */
public class Security
{
	private static int TIMEOUT = 300;
	private Map<String,List<String>> linkMap;
	private Modules mods;
	private IRCInterface irc;
	public Security (Modules mods, IRCInterface irc) {
		linkMap = new HashMap<String,List<String>>();
		this.mods = mods;
		this.irc = irc;
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

	private boolean nsCheck( String nick ) {
		try
		{
			return (Boolean)mods.plugin.callAPI("NickServ", "Check", nick);
		}
		catch (ChoobException e)
		{
			System.err.println("NickServ Check failed: Assuming invalid");
			return false;
		}
	}

	public void commandAddUser( Message mes )
	{
		if (!nsCheck(mes.getNick()))
		{
			irc.sendContextReply(mes, "Sorry, you must be identified with NickServ to add users.");
			return;
		}

		List params = mods.util.getParams( mes );

		String userName;
		if (params.size() == 1)
			userName = mes.getNick();
		else if (params.size() == 3)
		{
			// Hacky alias
			commandAddToGroup( mes );
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
			if (! mods.security.hasPerm( new ChoobPermission("user.add") , mes.getNick() ) )
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

	public void commandDelUser( Message mes )
	{
		if (!nsCheck(mes.getNick()))
		{
			irc.sendContextReply(mes, "Sorry, you must be identified with NickServ to delete users.");
			return;
		}

		List params = mods.util.getParams( mes );

		String userName;
		if (params.size() == 1)
			userName = mes.getNick();
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
			if (! mods.security.hasPerm( new ChoobPermission("user.del") , mes.getNick() ) )
			{
				irc.sendContextReply( mes, "You don't have permission to add arbitrary users!" );
				return;
			}
		}
		// Can add the user...
		try
		{
			mods.security.delUser( userName );
		}
		catch ( ChoobException e )
		{
			irc.sendContextReply( mes, "The user could not be deleted: " + e );
			return;
		}
		catch ( SecurityException e )
		{
			irc.sendContextReply( mes, "Urgh. We got a security exception." );
			return;
		}
		irc.sendContextReply( mes, "OK, user added!" );
	}

	/**
	 * Mark the user's nickname as pokable.
	 */
	public void commandBeginLink( Message mes )
	{
		String userName = mes.getNick();

		if (!nsCheck(userName))
		{
			irc.sendContextReply(mes, "Sorry, you must be identified with NickServ to begin nick linking.");
			return;
		}

		List params = mods.util.getParams( mes );
		if (params.size() == 1)
		{
			irc.sendContextReply(mes, "Syntax: Security.BeginLink <NICKNAME> [<NICKNAME> ...]");
			return;
		}

		String rootName;
		try
		{
			rootName = mods.security.getRootUser( userName );
		}
		catch (ChoobException e)
		{
			System.out.println(e.getMessage());
			irc.sendContextReply(mes, "Your username has not been added to the bot. Try Security.AddUser first!");
			return;
		}

		List<String> nicks;
		synchronized(linkMap)
		{
			nicks = linkMap.get(rootName.toLowerCase());
			if (nicks == null)
			{
				nicks = new ArrayList<String>();
				linkMap.put(rootName.toLowerCase(), nicks);
			}
		}

		for(int i=1; i<params.size(); i++)
		{
			nicks.add(((String)params.get(i)).toLowerCase());
		}

		irc.sendContextReply(mes, "OK, ready to link to " + rootName + ". Change nickname, identify, then use \"Security.Link " + rootName + "\".");
	}

	public void commandLink( Message mes )
	{
		List params = mods.util.getParams( mes );

		if (!nsCheck(mes.getNick()))
		{
			irc.sendContextReply(mes, "Sorry, you must be identified with NickServ to link nicks.");
			return;
		}

		String rootName;
		String leafName;
		if (params.size() == 2)
		{
			leafName = mes.getNick();
			rootName = (String)params.get(1);
			List<String> nicks = null;
			synchronized(linkMap)
			{
				nicks = linkMap.get(rootName.toLowerCase());
			}
			if (nicks == null || !nicks.contains( leafName.toLowerCase() )) {
				irc.sendContextReply( mes, "You haven't called \"Security.BeginLink " + leafName + "\" as " + rootName + "! Please change to " + rootName + " and do this.");
				return;
			}
		}
		else if (params.size() > 3)
		{
			irc.sendContextReply( mes, "Syntax: Security.Link <ROOT> [<LEAF>] - to link <LEAF> (or your current nick) to <ROOT>." );
			return;
		}
		else
		{
			// Must check permission!
			rootName = (String)params.get(1);
			leafName = (String)params.get(2);
			// Sure, this will be checked for us. But what about the user who called us?
			if (! mods.security.hasPerm( new ChoobPermission("user.link") , mes.getNick() ) )
			{
				irc.sendContextReply( mes, "You don't have permission to link arbitrary users!" );
				return;
			}
		}

		// Can add the user...
		try
		{
			mods.security.linkUser( rootName, leafName );
		}
		catch ( ChoobException e )
		{
			irc.sendContextReply( mes, "The user could not be deleted: " + e );
			return;
		}
		catch ( SecurityException e )
		{
			irc.sendContextReply( mes, "Urgh. We got a security exception." );
			return;
		}
		irc.sendContextReply( mes, "OK, user " + leafName + " linked to root " + rootName + "!");
	}

	public boolean groupCheck(String groupName, String userName)
	{
		if (groupName.toLowerCase().startsWith("user."))
		{
			String chunk = groupName.toLowerCase().substring(5);
			if (chunk.startsWith(userName.toLowerCase() + "."))
				return true;
			else
			{
				// Check root, too...
				String rootName;
				try
				{
					rootName = mods.security.getRootUser( userName );
					if (chunk.startsWith(rootName.toLowerCase() + "."))
						return true;
				}
				catch (ChoobException e)
				{
					// Squelch
				}
			}
		}
		return false;
	}

	public void commandAddGroup( Message mes )
	{
		if (!nsCheck(mes.getNick()))
		{
			irc.sendContextReply(mes, "Sorry, you must be identified with NickServ to link nicks.");
			return;
		}

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

			boolean check = groupCheck(groupName, mes.getNick());
			// Sure, this will be checked for us. But what about the user who called us?
			if (!check && !mods.security.hasPerm( new ChoobPermission("group.add." + groupName) , mes.getNick() ) )
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
	public void commandAddToGroup( Message mes )
	{
		this.doGroupMemberChange(mes, true);
	}

	public void commandRemoveFromGroup( Message mes )
	{
		this.doGroupMemberChange(mes, false);
	}

	private void doGroupMemberChange( Message mes, boolean isAdding )
	{
		if (!nsCheck(mes.getNick()))
		{
			irc.sendContextReply(mes, "Sorry, you must be identified with NickServ to change group members.");
			return;
		}

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
			parentName = (String)params.get(1);
			childName = ((String)params.get(2));
			if (childName.indexOf('.') != -1)
				isGroup = true;
			boolean check = groupCheck(parentName, mes.getNick());
			// Sure, this will be checked for us. But what about the user who called us?
			if (!check && ! mods.security.hasPerm( new ChoobPermission("group.members." + parentName) , mes.getNick() ) )
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
		irc.sendContextReply( mes, "OK, membership of " + childName + " in " + parentName + " altered!" );
	}

	private Permission makePermission(String permType, String permName, String permActions)
	{
		String lType = permType.toLowerCase();
		Matcher ma = Pattern.compile("(?:.*\\.)?(\\w+)(?:permission)?").matcher(lType);
		if (ma.matches())
			lType = ma.group(1);
		System.out.println("Permission type: " + lType);

		// Choob permission (primary)
		if ( lType.equals("choob") )
			return new ChoobPermission(permName);

		// All permission
		else if ( lType.equals("all") )
			return new java.security.AllPermission();

		// These permissions use the action parameter
		else if ( lType.equals("file") )
			return new java.io.FilePermission(permName, permActions);

		else if ( lType.equals("socket") )
			return new java.net.SocketPermission(permName, permActions);

		// The remainder are all BasicPermission-derived
		else if ( lType.equals("runtime") )
			return new java.lang.RuntimePermission(permName);

		else if ( lType.equals("security") )
			return new java.security.SecurityPermission(permName);

		else if ( lType.equals("property") )
			return new java.util.PropertyPermission(permName, permActions);

		else if ( lType.equals("reflect") )
			return new java.lang.reflect.ReflectPermission(permName);

		else
			return null;
	}

	public void commandGrant( Message mes )
	{
		this.doPermChange( mes, true );
	}

	public void commandRevoke( Message mes )
	{
		this.doPermChange( mes, false );
	}

	private void doPermChange( Message mes, boolean isGranting )
	{
		if (!nsCheck(mes.getNick()))
		{
			irc.sendContextReply(mes, "Sorry, you must be identified with NickServ to link nicks.");
			return;
		}

		List params = mods.util.getParams( mes );

		if (params.size() < 3 || params.size() > 5)
		{
			irc.sendContextReply( mes, "You must specify a group and a permission!" );
			return;
		}

		String actions = null;
		if (params.size() == 5)
			actions = (String)params.get(4);

		String permName = null;
		if (params.size() >= 4)
			permName = (String)params.get(3);

		String permType = (String)params.get(2);
		String groupName = (String)params.get(1);

		int len = mes.getNick().length() + 6;
		String permString = isGranting ? "grant." : "revoke.";
		boolean check = groupCheck(groupName, mes.getNick());
		// Sure, this will be checked for us. But what about the user who called us?
		if (!(check || mods.security.hasPerm( new ChoobPermission("group." + permString + groupName) , mes.getNick() )) )
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

	public void commandFindPermission( Message mes )
	{
		List params = mods.util.getParams( mes );

		if (params.size() < 3 || params.size() > 5)
		{
			irc.sendContextReply( mes, "You must specify a group and a permission!" );
			return;
		}

		String actions = null;
		if (params.size() == 5)
			actions = (String)params.get(4);

		String permName = null;
		if (params.size() >= 4)
			permName = (String)params.get(3);

		String permType = (String)params.get(2);
		String groupName = (String)params.get(1);

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
