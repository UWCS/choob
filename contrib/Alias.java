import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;

public class AliasObject
{
	public AliasObject(String name, String converted, String owner)
	{
		this.name = name;
		this.converted = converted;
		this.owner = owner;
		this.locked = false;
		this.id = 0;
	}
	public AliasObject() {}
	public int id;
	public String name;
	public String converted;
	public String owner;
	public boolean locked;
}

public class Alias
{
	private final String validator="[^A-Za-z_0-9]+";

	private Modules mods;
	private IRCInterface irc;
	public Alias(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public void commandAdd( Message mes ) throws ChoobException
	{
		List<String> params = mods.util.getParams(mes, 2);

		if (params.size() <= 2)
		{
			irc.sendContextReply(mes, "Syntax: Alias.Add <aliasname> <string>");
			return;
		}

		String name = params.get(1).replaceAll(validator, "").toLowerCase();
		String conv = params.get(2);

		AliasObject alias = getAlias(name);

		String nick = mods.security.getRootUser(mes.getNick());

		String oldAlias = ""; // Set to content of old alias, if there was one.
		if (alias != null)
		{
			if (alias.locked)
			{
				if (alias.owner.toLowerCase().equals(nick.toLowerCase()))
					mods.security.checkNS(mes.getNick());
				else
					mods.security.checkNickPerm(new ChoobPermission("plugins.alias.unlock"), mes.getNick());
			}

			oldAlias = " (was " + alias.converted + ")";

			alias.converted = conv;
			alias.owner = nick;

			mods.odb.update(alias);
		}
		else
			mods.odb.save(new AliasObject(name, conv, nick));

		irc.sendContextReply(mes, "Aliased '" + name + "' to '" + conv + "'" + oldAlias + ".");
	}

	public void commandRemove( Message mes ) throws ChoobException
	{
		List<String> params = mods.util.getParams(mes, 2);

		if (params.size() <= 1)
		{
			irc.sendContextReply(mes, "Syntax: Alias.Remove <aliasname>");
			return;
		}

		String name = params.get(1).replaceAll(validator, "").toLowerCase();
		String conv = params.get(2);

		AliasObject alias = getAlias(name);

		String nick = mods.security.getRootUser(mes.getNick());

		String oldAlias = ""; // Set to content of old alias, if there was one.
		if (alias != null)
		{
			if (alias.locked)
			{
				if (alias.owner.toLowerCase().equals(nick.toLowerCase()))
					mods.security.checkNS(mes.getNick());
				else
					mods.security.checkNickPerm(new ChoobPermission("plugins.alias.unlock"), mes.getNick());
			}

			oldAlias = " (was " + alias.converted + ")";

			mods.odb.delete(alias);
			
			irc.sendContextReply(mes, "Deleted '" + alias.name + "', was aliased to '" + alias.converted + "'.");
		}
		else
			irc.sendContextReply(mes, "Alias not found.");
	}

	public void commandList( Message mes ) throws ChoobException
	{
		String clause = "locked = 0";

		String parm = mods.util.getParamString(mes).toLowerCase();
		if (parm.equals("locked"))
			clause = "locked = 1";
		else if (parm.equals("all"))
			clause = "1";

		List<AliasObject> results = mods.odb.retrieve( AliasObject.class, "WHERE " + clause );

		if (results.size() == 0)
			irc.sendContextReply(mes, "No aliases.");
		else
		{
			String list = "Alias list: ";
			for (int i=0; i < results.size(); i++)
			{
				list += results.get(i).name;
				if (i < results.size() - 2)
					list += ", ";
				else if (i == results.size() - 2)
				{
					if (i == 0)
						list += " and ";
					else
						list += ", and ";
				}
			}
			list += ".";
			irc.sendContextReply(mes, list);
		}
	}

	public void commandShow( Message mes ) throws ChoobException
	{
		List<String> params = mods.util.getParams(mes, 2);

		if (params.size() < 2)
		{
			irc.sendContextReply(mes, "Please specify the name of the alias to show.");
			return;
		}

		String name = params.get(1).replaceAll(validator,"").toLowerCase();

		AliasObject alias = getAlias(name);

		if (alias == null)
			irc.sendContextReply(mes, "Alias not found.");
		else
			irc.sendContextReply(mes, "'" + alias.name + "'" + (alias.locked ? "[LOCKED]" : "") + " was aliased to '" + alias.converted + "' by '" + alias.owner + "'.");
	}

	public void commandLock( Message mes ) throws ChoobException
	{
		List<String> params = mods.util.getParams(mes, 2);

		if (params.size() < 2)
		{
			irc.sendContextReply(mes, "Please specify the name of the alias to lock.");
			return;
		}

		String name = params.get(1).replaceAll(validator,"").toLowerCase();

		AliasObject alias = getAlias(name);

		if (alias != null)
		{
			// No need to NS check here.
			alias.locked = true;
			mods.odb.update(alias);
			irc.sendContextReply(mes, "Locked " + name + "!");
		}
		else
		{
			irc.sendContextReply(mes, "Alias " + name + " not found.");
		}
	}

	public void commandUnLock( Message mes ) throws ChoobException
	{
		List<String> params = mods.util.getParams(mes, 2);

		if (params.size() < 2)
		{
			irc.sendContextReply(mes, "Please specify the name of the alias to unlock.");
			return;
		}

		String name = params.get(1).replaceAll(validator,"").toLowerCase();

		AliasObject alias = getAlias(name);

		if (alias != null)
		{
			String nick = mods.security.getRootUser(mes.getNick());

			if (nick.toLowerCase().equals(alias.owner.toLowerCase()))
				mods.security.checkNS(mes.getNick());
			else
				mods.security.checkNickPerm(new ChoobPermission("plugins.alias.unlock"), mes.getNick());

			alias.locked = false;
			mods.odb.update(alias);
			irc.sendContextReply(mes, "Unlocked " + name + "!");
		}
		else
		{
			irc.sendContextReply(mes, "Alias " + name + " not found.");
		}
	}

	private AliasObject getAlias( String name ) throws ChoobException
	{
		String alias = name.replaceAll(validator,"").toLowerCase();

		List<AliasObject> results = mods.odb.retrieve( AliasObject.class, "WHERE name='" + alias + "'" );

		if (results.size() == 0)
			return null;
		else
			return results.get(0);
	}

	public void onPrivateMessage( Message mes ) throws ChoobException
	{
		onMessage( mes );
	}

	// Muhahahahahahahahaha --bucko
	public void onMessage( Message mes ) throws ChoobException
	{
		String text = mes.getMessage();

		Matcher matcher = Pattern.compile(irc.getTriggerRegex()).matcher(text);
		int offset = 0;

		// Make sure this is actually a command...
		if (matcher.find())
		{
			offset = matcher.end();
		}
		else if (!(mes instanceof PrivateEvent))
		{
			return;
		}

		// Stop recursion
		if (mes.getSynthLevel() > 1) {
			irc.sendContextReply(mes, "Synthetic event recursion detected. Stopping.");
			return;
		}

		// Text is everything up to the next space...
		int cmdEnd = text.indexOf(' ', offset);
		String cmdParams;
		if (cmdEnd == -1)
		{
			cmdEnd = text.length();
			cmdParams = "";
		}
		else
		{
			cmdParams = text.substring(cmdEnd);
		}

		int dotIndex = text.indexOf('.', offset);
		// Real command, not an alias...
		if (dotIndex != -1 && dotIndex < cmdEnd)
			return;

		String command = text.substring(offset, cmdEnd);
		List all = mods.odb.retrieve( AliasObject.class, "WHERE name=\"" + command.replaceAll(validator, "") + "\"" );

		if (all.size() == 0)
		{
			// Consider an error here...
			return;
		}

		AliasObject alias = (AliasObject)all.get(0);

		command = irc.getTrigger() + alias.converted + cmdParams;

		Message newMes = (Message)mes.cloneEvent( command );

		System.out.println("Converted " + text + " -> " + command);

		mods.synthetic.doSyntheticMessage( newMes );
	}
}
