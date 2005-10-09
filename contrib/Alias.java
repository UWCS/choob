import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.*;

public class Alias
{
	private final String validator="[^A-Za-z_0-9]+";

	public void commandAdd( Message con, Modules mods, IRCInterface irc ) throws ChoobException
	{
		String propend="";
		List<String>s=mods.util.getParams(con, 2);

		String alias=s.get(1).replaceAll(validator,"").toLowerCase();
		String conv=s.get(2);

		List<AliasObject> existing = mods.odb.retrieve( AliasObject.class, "WHERE name='" + alias + "'" );

		if (existing.size() != 0)
		{
			AliasObject aO = existing.get(0);

			propend=", was " + aO.converted + ".";

			aO.converted=conv;

			mods.odb.update(aO);
		}
		else
			mods.odb.save(new AliasObject(alias, conv));

		irc.sendContextReply(con, "Aliased '" + alias + "' to '" + conv + "'" + propend);
	}

	public void commandList( Message con, Modules mods, IRCInterface irc ) throws ChoobException
	{
		List<AliasObject> existing = mods.odb.retrieve( AliasObject.class, "WHERE 1" );
		String list="Alias list: ";
		if (existing.size()==0)
			irc.sendContextReply(con, "No aliases.");
		else
		{
			for (int i=0; i<existing.size(); i++)
				list+=existing.get(i).name + ": " + existing.get(i).converted + ", ";
			irc.sendContextReply(con, list.substring(0,list.length()-2) + ".");
		}
	}

	public void commandShow( Message con, Modules mods, IRCInterface irc ) throws ChoobException
	{
		List<String>s=mods.util.getParams(con, 2);
		String alias = s.get(1).replaceAll(validator,"").toLowerCase();

		List<AliasObject> existing = mods.odb.retrieve( AliasObject.class, "WHERE name='" + alias + "'" );

		if (existing.size()==0)
			irc.sendContextReply(con, "Alias not found.");
		else
		{
			AliasObject aO=existing.get(0);
			irc.sendContextReply(con, "'" + aO.name + "' is aliased to '" + aO.converted + "'.");
		}
	}

	public void commandDelete( Message con, Modules mods, IRCInterface irc ) throws ChoobException
	{
		List<String>s=mods.util.getParams(con, 2);
		String alias = s.get(1).replaceAll(validator,"").toLowerCase();

		List<AliasObject> existing = mods.odb.retrieve( AliasObject.class, "WHERE name='" + alias + "'" );

		if (existing.size()==0)
			irc.sendContextReply(con, "Alias not found.");
		else
		{
			AliasObject aO=existing.get(0);
			mods.odb.delete(aO);
			irc.sendContextReply(con, "Deleted '" + aO.name + "', was aliased to '" + aO.converted + "'.");
		}
	}

}
