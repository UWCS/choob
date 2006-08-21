package uk.co.uwcs.choob;

import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;

public class ChoobDecoderTask extends ChoobTask
{
	private static DbConnectionBroker dbBroker;
	private static Modules modules;
	private static IRCInterface irc;
	private static Pattern triggerPattern;
	private static Pattern commandPattern;
	private Event event;

	static void initialise(DbConnectionBroker dbBroker, Modules modules, IRCInterface irc)
	{
		if (ChoobDecoderTask.dbBroker != null)
			return;
		ChoobDecoderTask.dbBroker = dbBroker;
		ChoobDecoderTask.modules = modules;
		ChoobDecoderTask.irc = irc;
		commandPattern = Pattern.compile("^([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)$");
		updatePatterns();
	}

	static void updatePatterns()
	{
		triggerPattern = Pattern.compile("^(?:" + irc.getTriggerRegex() + ")", Pattern.CASE_INSENSITIVE);
	}
	
	/** Creates a new instance of ChoobThread */
	ChoobDecoderTask(Event event)
	{
		super(null, "ChoobDecoderTask:" + event.getMethodName());
		this.event = event;
	}

	public synchronized void run()
	{
		List<ChoobTask> tasks = new LinkedList<ChoobTask>();

		if (event instanceof NickChange)
		{
			NickChange nc = (NickChange)event;
			// Note: the IRC library has already handled this message, so we
			// match the *new* nickname with the bot's.
			if (nc.getNewNick().equals(irc.getNickname())) {
				updatePatterns();
				// Make sure the trigger checking code is up-to-date with the current nickname.
				modules.util.updateTrigger();
			}
		}

		// Process event calls first
		tasks.addAll(modules.plugin.getPlugMan().eventTasks(event));

		boolean ignoreTriggers = false;
		if (event instanceof UserEvent)
		{
			// TODO - This should happen only when a trigger might actually be activated...
			try
			{
				if (1 == (Integer)ChoobDecoderTask.modules.plugin.callAPI("UserTypeCheck", "Status", ((UserEvent)event).getNick(), "bot"))
					ignoreTriggers = true;
			}
			catch (ChoobNoSuchCallException e)
			{
				// This is fine.
			}
			catch (Throwable e)
			{
				// This isn't.
				System.err.println("EXCEPTION: " + e.toString());
			}
		}

		// Then filters
		if (!ignoreTriggers && event instanceof FilterEvent)
		{
			// FilterEvents are messages
			Message mes = (Message) event;
			tasks.addAll(modules.plugin.getPlugMan().filterTasks(mes));
		}

		// Now if it's a message, deal with that too
		if (!ignoreTriggers && event instanceof CommandEvent)
		{
			// CommandEvents are messages
			Message mes = (Message) event;

			Matcher ma;

			// First, is does it have a trigger?
			String matchAgainst = mes.getMessage();
			ma = triggerPattern.matcher(matchAgainst);

			boolean mafind = ma.find();

			if ( mafind || mes instanceof PrivateMessage )
			{
				// OK, it's a command!

				// Decode into a string we can match as a command.
				int commandStart = (mafind ? ma.end() : 0);
				int commandEnd = matchAgainst.indexOf(' ', commandStart);
				if (commandEnd != -1)
					matchAgainst = matchAgainst.substring(commandStart, commandEnd);
				else
					matchAgainst = matchAgainst.substring(commandStart);

				if (matchAgainst.indexOf(' ') >= 0)
					matchAgainst = matchAgainst.substring(0, matchAgainst.indexOf(' '));

				ma = commandPattern.matcher(matchAgainst);
				if( ma.matches() )
				{
					try
					{
						int ret = (Integer)modules.plugin.callAPI("Flood", "IsFlooding", mes.getNick(), 1500, 4);
						if (ret != 0)
						{
							if (ret == 1)
								irc.sendContextReply(mes, "You're flooding, ignored. Please wait at least 1.5s between your messages.");
							return;
						}
					}
					catch (ChoobNoSuchCallException e)
					{ } // Ignore
					catch (Throwable e)
					{
						System.err.println("Couldn't do antiflood call: " + e);
					}

					String pluginName  = ma.group(1);
					String commandName = ma.group(2);

					ChoobTask task = modules.plugin.getPlugMan().commandTask(pluginName, commandName, mes);
					if (task != null)
						tasks.add(task);
				}
			}
		}

		// We now have a neat list of tasks to perform. Queue them all.
		for(ChoobTask task: tasks)
		{
			ChoobThreadManager.queueTask(task);
		}

		// And done.
	}
}
