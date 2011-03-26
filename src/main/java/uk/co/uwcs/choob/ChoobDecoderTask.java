package uk.co.uwcs.choob;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.events.CommandEvent;
import uk.co.uwcs.choob.support.events.Event;
import uk.co.uwcs.choob.support.events.FilterEvent;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.NickChange;
import uk.co.uwcs.choob.support.events.PrivateMessage;
import uk.co.uwcs.choob.support.events.UserEvent;

public class ChoobDecoderTask extends ChoobTask
{
	private final ChoobDecoderTaskData data;
	private final Event event;

	private static final Pattern commandPattern = Pattern.compile("^([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)$");

	/** Creates a new instance of ChoobThread */
	ChoobDecoderTask(final ChoobDecoderTaskData data, final Event event)
	{
		super(null, "ChoobDecoderTask:" + event.getMethodName());
		this.event = event;
		this.data = data;
	}

	@Override
	public synchronized void run()
	{
		final List<ChoobTask> tasks = new LinkedList<ChoobTask>();

		Message mes = null;
		if (event instanceof Message)
			mes = (Message)event;

		if (event instanceof NickChange)
		{
			final NickChange nc = (NickChange)event;
			// Note: the IRC library has already handled this message, so we
			// match the *new* nickname with the bot's.
			if (nc.getNewNick().equals(data.irc.getNickname())) {
				data.updatePatterns();
				// Make sure the trigger checking code is up-to-date with the current nickname.
				data.modules.util.updateTrigger();
			}
		}

		// Check if the message looks like a command in any way.
		Matcher ma = null;
		boolean mafind = false;
		if (event instanceof CommandEvent && mes != null)
		{
			// First, is does it have a trigger?
			String matchAgainst = mes.getMessage();
			ma = data.triggerPattern.matcher(matchAgainst);

			mafind = ma.find();
			if (mafind || mes instanceof PrivateMessage)
			{
				// Decode into a string we can match as a command.
				final int commandStart = mafind ? ma.end() : 0;
				final int commandEnd = matchAgainst.indexOf(' ', commandStart);
				if (commandEnd != -1)
					matchAgainst = matchAgainst.substring(commandStart, commandEnd);
				else
					matchAgainst = matchAgainst.substring(commandStart);

				if (matchAgainst.indexOf(' ') >= 0)
					matchAgainst = matchAgainst.substring(0, matchAgainst.indexOf(' '));

				// Store the command name for convenience.
				mes.getFlags().put("command", matchAgainst);
			}
		}

		// Process event calls first
		tasks.addAll(data.modules.plugin.getPlugMan().eventTasks(event));

		boolean ignoreTriggers = false;
		if (event instanceof UserEvent &&
		    (event instanceof FilterEvent ||
		     mes != null && mes.getFlags().containsKey("command")))
		{
			try
			{
				if (1 == (Integer)data.modules.plugin.callAPI("UserTypeCheck", "Status", ((UserEvent)event).getNick(), "bot"))
					ignoreTriggers = true;
			}
			catch (final ChoobNoSuchCallException e)
			{
				// This is fine.
			}
			catch (final Throwable e)
			{
				// This isn't.
				System.err.println("EXCEPTION: " + e.toString());
			}
		}

		// Then filters
		if (!ignoreTriggers)
		{
			if (event instanceof FilterEvent)
			{
				tasks.addAll(data.modules.plugin.getPlugMan().filterTasks(mes));
			}

			// Now if it's a message, deal with that too
			if (mes != null && mes.getFlags().containsKey("command"))
			{
				ma = commandPattern.matcher(mes.getFlags().get("command"));
				if (ma.matches())
				{
					try
					{
						final int ret = (Integer)data.modules.plugin.callAPI("Flood", "IsFlooding", mes.getNick(), 1500, 4);
						if (ret != 0)
						{
							if (ret == 1)
								data.irc.sendContextReply(mes, "You're flooding, ignored. Please wait at least 1.5s between your messages.");
							return;
						}
					}
					catch (final ChoobNoSuchCallException e)
					{ } // Ignore
					catch (final Throwable e)
					{
						System.err.println("Couldn't do antiflood call: " + e);
					}

					final String pluginName  = ma.group(1);
					final String commandName = ma.group(2);

					final ChoobTask task = data.modules.plugin.getPlugMan().commandTask(pluginName, commandName, mes);
					if (task != null)
						tasks.add(task);
				}
			}
		}

		// We now have a neat list of tasks to perform. Queue them all.
		for(final ChoobTask task: tasks)
		{
			data.ctm.queueTask(task);
		}

		// And done.
	}
}
