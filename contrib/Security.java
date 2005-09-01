import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;

/**
 * Choob plugin for fiddling with security privs
 * @author bucko
 */
class Security
{
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
			int offset = mes.getNick().length() + 6;
			if (groupName.length() > offset
					&& groupName.substring(0, offset).toLowerCase().equals("user." + mes.getNick().toLowerCase() + "."))
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

	public void commandAddUserToGroup( Message mes, Modules mods, IRCInterface irc )
	{
		List params = mods.util.getParams( mes );

		String groupName;
		String userName;
		if (params.size() != 3)
		{
			irc.sendContextReply( mes, "You must specify a user and a group!" );
			return;
		}
		else
		{
			// Must check permission!
			userName = (String)params.get(1);
			groupName = (String)params.get(2);
			// Sure, this will be checked for us. But what about the user who called us?
			int offset = mes.getNick().length() + 6;
			if (groupName.length() > offset
					&& groupName.substring(0, offset).toLowerCase().equals("user." + mes.getNick().toLowerCase() + "."))
			{
				// OK to add
			}
			else if (! mods.security.hasPerm( new ChoobPermission("group.addMember." + groupName) , mes.getNick() ) )
			{
				irc.sendContextReply( mes, "You don't have permission to add arbitrary groups!" );
				return;
			}
		}
		// Can add the user...
		try
		{
			mods.security.addUserToGroup( groupName, userName );
		}
		catch ( ChoobException e )
		{
			irc.sendContextReply( mes, "The member could not be added: " + e );
			return;
		}
		catch ( SecurityException e )
		{
			irc.sendContextReply( mes, "Urgh. We got a security exception." );
			return;
		}
		irc.sendContextReply( mes, "OK, member added!" );
	}
}
