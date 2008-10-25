import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.ContextEvent;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.PrivateMessage;

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
			"$Rev$$Date$"
		};
	}

	Modules mods;
	IRCInterface irc;

	Map<String,ContextEvent> reply = new HashMap<String,ContextEvent>();

	public PassThru(final Modules mods, final IRCInterface irc)
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
	public void commandSend( final Message mes )
	{
		final List<String> params = mods.util.getParams( mes, 2 );

		if (params.size() != 3)
		{
			irc.sendContextReply( mes, "Syntax: " + params.get(0) + " <Bot> <Message>" );
			return;
		}

		final String target = params.get(1).toLowerCase();
		if (!target.equals("jinglybot"))
		{
			irc.sendContextReply( mes, "Sorry, I'm not allowed to pass through to " + params.get(1) );
			return;
		}

		reply.put(target, mes);

		irc.sendMessage( params.get(1), params.get(2) );
	}

	public synchronized void onPrivateMessage( final PrivateMessage mes )
	{
		final ContextEvent context = reply.get(mes.getNick().toLowerCase());
		if (context != null)
			irc.sendContextReply( context, mes.getMessage() );
	}
}
