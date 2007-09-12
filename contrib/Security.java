import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.event.*
import java.security.*;
import java.util.*;
import java.util.regex.*;

/**
 * Choob plugin for fiddling with security privs
 * @author bucko
 */
public class Security
{
	public String[] info()
	{
		return new String[] {
			"Security manipulation plugin.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private static int TIMEOUT = 300;
	private Map<String,List<String>> linkMap;
	private Modules mods;
	private IRCInterface irc;
	public Security (Modules mods, IRCInterface irc) {
		linkMap = new HashMap<String,List<String>>();
		this.mods = mods;
		this.irc = irc;
	}

	public String[] helpTopics = { "UsingLink", "UsingGroups", "UsingPermissions", "SpecifyingPermissions" };

	public String[] helpCommandAddUser = {
		"Add a user to the database.",
		"[<Name>]",
		"<Name> is an optional username to add (if omitted, your nickname will be used; if specified you need the 'user.add' permission)"
	};
	public void commandAddUser( Message mes )
	{
		mods.security.checkAuth(mes);

		List params = mods.util.getParams( mes );

		String userName;
		if (params.size() == 1)
			userName = mods.security.getUserAuthName(mes.getNick());
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
			mods.security.checkPerm( new ChoobPermission("user.add") , mes);
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
		} catch (IllegalArgumentException e) {
			irc.sendContextReply(mes, e.getMessage());
			return;
		}
		irc.sendContextReply( mes, "OK, user added!" );
	}

	public String[] helpCommandDelUser = {
		"Remove a user from the database.",
		"[<Name>]",
		"<Name> is an optional username to remove (if omitted, your nickname will be used; if specified you need the 'user.del' permission)"
	};
	public void commandDelUser( Message mes )
	{
		mods.security.checkAuth(mes);

		List params = mods.util.getParams( mes );

		String userName;
		if (params.size() == 1)
			userName = mods.security.getUserAuthName(mes.getNick());
		else if (params.size() > 2)
		{
			irc.sendContextReply( mes, "You may only specify one user!" );
			return;
		}
		else
		{
			// Must check permission!
			userName = mods.security.getUserAuthName((String)params.get(1));
			// Sure, this will be checked for us. But what about the user who called us?
			mods.security.checkPerm( new ChoobPermission("user.del") , mes );
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
		irc.sendContextReply( mes, "OK, user deleted!" );
	}

	public String[] helpUsingLink = {
		  "Security related commands require you to link all of your nicknames"
		+ " together, or you'll only be able to use the command on one"
		+ " nickname. To do this, You need to change to your \"root\" nickname"
		+ " (this is the one that'll be used in grant commands etc.) and run"
		+ " 'Security.BeginLink <Nick1> <Nick2> <Nick3> ...' to mark the given"
		+ " nicknames as names you'd like to link to. Then (in any order), you"
		+ " need to change to each of the specified nicknames, make sure you're"
		+ " identified with NickServ, and run 'Security.Link <Root>', where"
		+ " <Root> is your root nickname."
	};
	public String[] helpCommandBeginLink = {
		"Prepare to use the Security.Link command from other nicknames to this one. See Security.UsingLink.",
		"<Nick> [<Nick> ...]",
		"<Nick> is a nickname you'd like to link to your current one"
	};
	public void commandBeginLink( Message mes )
	{
		String userName = mods.security.getUserAuthName(mes.getNick());

		mods.security.checkAuth(mes);

		List params = mods.util.getParams( mes );
		if (params.size() == 1)
		{
			irc.sendContextReply(mes, "Syntax: Security.BeginLink <NICKNAME> [<NICKNAME> ...]");
			return;
		}

		String rootName;
		rootName = mods.security.getRootUser( userName );
		if (rootName == null)
		{
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

		StringBuffer output = new StringBuffer();
		for(int i=1; i<params.size(); i++)
		{
			if (i == 1) {}
			else if (i == params.size() - 1)
				output.append(" and ");
			else
				output.append(", ");
			output.append(((String)params.get(i)).toLowerCase());
			nicks.add(((String)params.get(i)).toLowerCase());
		}

		irc.sendContextReply(mes, "OK, ready to link " + output + " to " + rootName + ". Change nickname, identify, then use \"Security.Link " + rootName + "\".");
	}

	public String[] helpCommandLink = {
		"Link two nicknames together.",
		"<Root> [<Leaf>]",
		"<Root> is the nickname to link to",
		"<Leaf> is an optional nickname to link from (this defaults to your current nickname; if you specify this, it overrides BeginLink and you need the 'user.link' permission)",
	};
	public void commandLink( Message mes )
	{
		List<String> params = mods.util.getParams( mes );

		mods.security.checkAuth(mes);

		String rootName;
		String leafName;
		if (params.size() == 2)
		{
			leafName = mods.security.getUserAuthName(mes.getNick());
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
			leafName = mods.security.getUserAuthName((String)params.get(2));
			// Sure, this will be checked for us. But what about the user who called us?
			mods.security.checkPerm( new ChoobPermission("user.link") , mes);
		}

		// Can add the user...
		try
		{
			mods.security.linkUser( rootName, leafName );
		}
		catch ( ChoobException e )
		{
			irc.sendContextReply( mes, "The user could not be linked: " + e );
			return;
		} catch (IllegalArgumentException e) {
			irc.sendContextReply(mes, e.getMessage());
			return;
		}
		irc.sendContextReply( mes, "OK, user " + leafName + " linked to root " + rootName + "!");
	}

	public boolean groupCheck(String groupName, String userName)
	{
		userName = mods.security.getUserAuthName(userName);
		if (groupName.toLowerCase().startsWith("user."))
		{
			String chunk = groupName.toLowerCase().substring(5);
			if (chunk.startsWith(userName.toLowerCase() + "."))
				return true;
			else
			{
				// Check root, too...
				String rootName = mods.security.getRootUser( userName );
				if (rootName == null)
					rootName = userName;
				if (chunk.startsWith(rootName.toLowerCase() + "."))
					return true;
			}
		}
		return false;
	}

	public String[] helpUsingGroups = {
		  "There are 3 types of groups in the bot: User, Plugin and System.",
		  "System groups are simply just a name - there's no special difference"
		+ " between 'System.Anonymous' and 'System.Root' except for"
		+ " permissions.",
		  "However, the Plugin and User type groups are owned by plugins and"
		+ " users, respectively. That is, plugin <Name> can do what it likes"
		+ " with 'Plugin.<Name>' and 'Plugin.<Name>.*', and similarly for"
		+ " users. This includes creating groups (though the root groups"
		+ " 'Plugin.<Name>' and User.<Name> are created automatically), adding"
		+ " and removing members from groups and granting and revoking"
		+ " permissions from groups.",
		  "Note, though, that the root groups 'User.<Name>' and 'Plugin.<Name>'"
		+ " are used to actually represent the user or plugin in question. That"
		+ " is, adding user 'Fred' to 'User.Paul' will make the bot treat"
		+ " 'Fred' exactly as it treats 'Paul'. This is how nickname links"
		+ " work."
	};

	public String[] helpCommandAddGroup = {
		"Add a group to the database.",
		"<Name>",
		"<Name> is the name of the group to add (if this isn't of the form 'user.<YourNick>.<Something>', you need the 'group.add.<Name>' permission)"
	};
	public void commandAddGroup( Message mes )
	{
		mods.security.checkAuth(mes);

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

			boolean check = groupCheck(groupName, mods.security.getUserAuthName(mes.getNick()));
			// Sure, this will be checked for us. But what about the user who called us?
			if (!check)
				mods.security.checkPerm( new ChoobPermission("group.add." + groupName) , mes);
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
		} catch (IllegalArgumentException e) {
			irc.sendContextReply(mes, e.getMessage());
			return;
		}
		irc.sendContextReply( mes, "OK, group added!" );
	}

	public String[] helpCommandAddToGroup = {
		"Add one group to the members of another. Use the group 'user.<Name>' to add user <Name> to another group.",
		"<Parent> <Child>",
		"<Parent> is the name of the group to add into",
		"<Child> is the name of the group to add",
	};
	public void commandAddToGroup( Message mes )
	{
		this.doGroupMemberChange(mes, true);
	}

	public String[] helpCommandRemoveFromGroup = {
		"Remove one group from the members of another. Use the group 'user.<Name>' to remove user <Name> from another group.",
		"<Parent> <Child>",
		"<Parent> is the name of the group to remove from",
		"<Child> is the name of the group to remove",
	};
	public void commandRemoveFromGroup( Message mes )
	{
		this.doGroupMemberChange(mes, false);
	}

	private void doGroupMemberChange( Message mes, boolean isAdding )
	{
		mods.security.checkAuth(mes);

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
			boolean check = groupCheck(parentName, mods.security.getUserAuthName(mes.getNick()));
			// Sure, this will be checked for us. But what about the user who called us?
			if (!check)
				mods.security.checkPerm( new ChoobPermission("group.members." + parentName) , mes);
		}
		// Can add the user...
		try
		{
			if (isAdding)
			{
				if (isGroup)
					mods.security.addGroupToGroup( parentName, childName );
				else
					mods.security.addUserToGroup( parentName, mods.security.getUserAuthName(childName));
			}
			else
			{
				if (isGroup)
					mods.security.removeGroupFromGroup( parentName, childName );
				else
					mods.security.removeUserFromGroup( parentName, mods.security.getUserAuthName(childName));
			}
		}
		catch ( ChoobException e )
		{
			irc.sendContextReply( mes, "The membership could not be altered: " + e );
			return;
		} catch (IllegalArgumentException e) {
			irc.sendContextReply( mes, e.getMessage());
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

		// All permission. Nothing required.
		if ( lType.equals("all") )
			return new java.security.AllPermission();

		else if (permName == null)
			return null;

		// Permissions that need a name.
		else if ( lType.equals("choob") )
			return new ChoobPermission(permName);

		else if ( lType.equals("runtime") )
			return new java.lang.RuntimePermission(permName);

		else if ( lType.equals("security") )
			return new java.security.SecurityPermission(permName);

		else if ( lType.equals("reflect") )
			return new java.lang.reflect.ReflectPermission(permName);

		else if (permActions == null)
			return null;

		// These permissions use the action parameter.
		else if ( lType.equals("file") )
			return new java.io.FilePermission(permName, permActions);

		else if ( lType.equals("socket") )
			return new java.net.SocketPermission(permName, permActions);

		else if ( lType.equals("property") )
			return new java.util.PropertyPermission(permName, permActions);

		else
			return null;
	}

	public String[] helpSpecifyingPermissions = {
		  "Choob only supports a specific set of permissions for security"
		+ " reasons. These are: All (which requires no extra parameters);"
		+ " Choob, Runtime, Security and Reflect (which all need a name);"
		+ " and File, Socket and Property (which all need both a name and"
		+ " some actions)."
	};

	public String[] helpUsingPermissions = {
		  "It is suggested that rather that granting permissions to users, you"
		+ " should instead create a group and add the user to that group. This"
		+ " way, the permission can easily be granted to many users.",
		  "This does, however, raise complications about revoking permissions:"
		+ " There's no way to directly remove a specific permission from a"
		+ " user. Instead, you need to call 'Security.FindPermission' to"
		+ " discover the means by which the user gained the permission, then"
		+ " either remove the user from a group or revoke the permission on"
		+ " the user or perhaps a group."
	};

	public String[] helpCommandGrant = {
		"Grant a permission to a group.",
		"<Group> <Type> [<Name> [<Actions>]]",
		"<Group> is the group to which permissions should be added",
		"<Type> is the permission's type (see Security.SpecifyingPermissions)",
		"<Name> is the permission's name (optional or not depends on the particular permission)",
		"<Actions> is the permission's actions (optional or not depends on the particular permission)",
	};
	public void commandGrant( Message mes )
	{
		this.doPermChange( mes, true );
	}

	public String[] helpCommandRevoke = {
		"Revoke a specific permission from a group.",
		"<Group> <Type> [<Name> [<Actions>]]",
		"<Group> is the group to which permissions should be revoked",
		"<Type> is the permission's type (see Security.SpecifyingPermissions)",
		"<Name> is the permission's name (optional or not depends on the particular permission)",
		"<Actions> is the permission's actions (optional or not depends on the particular permission)",
	};
	public void commandRevoke( Message mes )
	{
		this.doPermChange( mes, false );
	}

	private void doPermChange( Message mes, boolean isGranting )
	{
		mods.security.checkAuth(mes);

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
		boolean check = groupCheck(groupName, mods.security.getUserAuthName(mes.getNick()));
		// Sure, this will be checked for us. But what about the user who called us?
		if (!check)
			mods.security.checkPerm( new ChoobPermission("group." + permString + groupName) , mes);

		Permission permission = makePermission(permType, permName, actions);
		if (permission == null)
		{
			irc.sendContextReply( mes, "Unknown permission type, or missing name/actions: " + permType );
			return;
		}

		// Now check the user has the permission...
		if (isGranting && ! mods.security.hasPerm( permission, mes) )
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
		} catch (IllegalArgumentException e) {
			irc.sendContextReply(mes, e.getMessage());
			return;
		}
		irc.sendContextReply( mes, "OK, permission changed!" );
	}

	public String[] helpCommandFindPermission = {
		"Locate the means by which a group gains a permission.",
		"<Group> <Type> [<Name> [<Actions>]]",
		"<Group> is the group for which permissions should be located",
		"<Type> is the permission's type (see Security.SpecifyingPermissions)",
		"<Name> is the permission's name (optional or not depends on the particular permission)",
		"<Actions> is the permission's actions (optional or not depends on the particular permission)",
	};
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
		} catch (IllegalArgumentException e) {
			irc.sendContextReply(mes, e.getMessage());
			return;
		}
	}
}
