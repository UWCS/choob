import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.text.*;

/**
 * Choob "passthrough" module.
 * You'll have to edit the source to change the nickname you want to pass through to.
 * 
 * @author bucko
 * 
 */

public class PassThru
{
	public String[] info()
	{
		return new String[] {
			"Hack plugin to allow smoother transition between bots.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			mods.util.getVersion()
		};
	}

	Modules mods;
	IRCInterface irc;

	Map<String,ContextEvent> reply = new HashMap<String,ContextEvent>();

	public PassThru(Modules mods, IRCInterface irc)
	{
		this.irc = irc;
		this.mods = mods;
	}

	public String[] helpCommandSend = {
		"Send a passthrough command to another bot.",
		"<Bot> <Message>",
		"<Bot> is the bot's nickname",
		"<Message> is the command"
	};
	public void commandSend( Message mes )
	{
		List<String> params = mods.util.getParams( mes, 2 );

		if (params.size() != 3)
		{
			irc.sendContextReply( mes, "Syntax: " + params.get(0) + " <Bot> <Message>" );
			return;
		}

		String target = params.get(1).toLowerCase();
		if (!target.equals("jinglybot"))
		{
			irc.sendContextReply( mes, "Sorry, I'm not allowed to pass through to " + params.get(1) );
			return;
		}

		reply.put(target, mes);

		irc.sendMessage( params.get(1), params.get(2) );
	}

	public void onPrivateMessage( PrivateMessage mes )
	{
		System.out.println("Test!");
		ContextEvent context = reply.get(mes.getNick().toLowerCase());
		if (context != null)
			irc.sendContextReply( context, mes.getMessage() );
	}
}
