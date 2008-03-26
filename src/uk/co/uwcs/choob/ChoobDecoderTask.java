package uk.co.uwcs.choob;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;

public class ChoobDecoderTask extends ChoobTask
{
	private static DbConnectionBroker dbBroker;
	private static Modules modules;
	private static IRCInterface irc;
	private static Pattern triggerPattern;
	private static Pattern commandPattern;
	private Event event;

	static void initialise(DbConnectionBroker broker, Modules mods, IRCInterface ircinter)
	{
		if (ChoobDecoderTask.dbBroker != null)
			return;
		ChoobDecoderTask.dbBroker = broker;
		ChoobDecoderTask.modules = mods;
		ChoobDecoderTask.irc = ircinter;
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

	@Override
	public synchronized void run()
	{
		List<ChoobTask> tasks = new LinkedList<ChoobTask>();
		
		Message mes = null;
		if (event instanceof Message)
			mes = (Message)event;

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
		
		// Check if the message looks like a command in any way.
		Matcher ma = null;
		boolean mafind = false;
		if (event instanceof CommandEvent && mes != null)
		{
			// First, is does it have a trigger?
			String matchAgainst = mes.getMessage();
			ma = triggerPattern.matcher(matchAgainst);
			
			mafind = ma.find();
			if (mafind || mes instanceof PrivateMessage)
			{
				// Decode into a string we can match as a command.
				int commandStart = (mafind ? ma.end() : 0);
				int commandEnd = matchAgainst.indexOf(' ', commandStart);
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
		tasks.addAll(modules.plugin.getPlugMan().eventTasks(event));
		
		boolean ignoreTriggers = false;
		if ((event instanceof UserEvent) &&
		    ((event instanceof FilterEvent) ||
		     ((mes != null) && mes.getFlags().containsKey("command"))))
		{
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
		if (!ignoreTriggers)
		{
			if (event instanceof FilterEvent)
			{
				tasks.addAll(modules.plugin.getPlugMan().filterTasks(mes));
			}

			// Now if it's a message, deal with that too
			if ((mes != null) && mes.getFlags().containsKey("command"))
			{
				ma = commandPattern.matcher(mes.getFlags().get("command"));
				if (ma.matches())
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
