package uk.co.uwcs.choob;

import uk.co.uwcs.choob.plugins.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;


// Keeps track of the most recent messages from people.
final class LastEvents
{
	private long lastmes[];
	static final int LENGTH = 3; // Number of message times to remember.
	static final int DELAY = 5000; // Warn once every (this) ms.
	private int lastOffset;
	private long nextWarn;

	LastEvents()
	{
		lastmes = new long[LENGTH];
		for(int i=0; i<LENGTH; i++)
			lastmes[i] = 0;
		lastOffset = 0;
		nextWarn = 0; // Always warn on first offense.
	}

	public long average()
	{
		return (lastmes[lastOffset] - lastmes[(lastOffset + 1) % LENGTH]) / LENGTH;
	}

	public boolean warn()
	{
		long time = System.currentTimeMillis();
		if (time > nextWarn)
		{
			nextWarn = time + DELAY;
			return true;
		}
		else
			return false;
	}

	public void newEvent()
	{
		lastOffset = (lastOffset + 1) % LENGTH;
		lastmes[lastOffset] = System.currentTimeMillis();
	}
}

final class ChoobDecoderTask extends ChoobTask
{
	private static DbConnectionBroker dbBroker;
	private static Modules modules;
	private static IRCInterface irc;
	private static Pattern triggerPattern;
	private static Pattern aliasPattern;
	private static Pattern commandPattern;
	private Event event;

	static Map<String,LastEvents>lastMessage = Collections.synchronizedMap(new HashMap<String,LastEvents>()); // Nick, Timestamp.

	static final long AVERAGE_MESSAGE_GAP = 2000; // If message rate from one user exceeds this, ignore them.

	static void initialise(DbConnectionBroker dbBroker, Modules modules, IRCInterface irc)
	{
		if (ChoobDecoderTask.dbBroker != null)
			return;
		ChoobDecoderTask.dbBroker = dbBroker;
		ChoobDecoderTask.modules = modules;
		ChoobDecoderTask.irc = irc;
		triggerPattern = Pattern.compile("^(?:" + irc.getTriggerRegex() + ")", Pattern.CASE_INSENSITIVE);
		commandPattern = Pattern.compile("^([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)$");
	}

	/** Creates a new instance of ChoobThread */
	ChoobDecoderTask(Event event)
	{
		super(null);
		this.event = event;
	}

	public synchronized void run()
	{
		List<ChoobTask> tasks = new LinkedList<ChoobTask>();

		if (event instanceof NickChange)
		{
			// FIXME: There is no way I can see to make this work here.
			// It needs to pick up when the BOT changes name, even through
			// external forces, and poke UtilModule about it.

			//NickChange nc = (NickChange)event;
			//if (nc.getNick().equals()) {
			//	// Make sure the trigger checking code is up-to-date with the current nickname.
			//	modules.util.updateTrigger();
			//}
		}

		// Log every Message we recieve.
		if (event instanceof Message)
			modules.history.addLog( (Message) event );

		// Process event calls first
		tasks.addAll(modules.plugin.getPlugMan().eventTasks(event));

		boolean ignoreTriggers = false;
		if (event instanceof UserEvent && ((UserEvent)event).getNick().toLowerCase().indexOf("bot") != -1)
		{
			ignoreTriggers = true;
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
					LastEvents recent = lastMessage.get(mes.getNick());
					if (recent == null)
						lastMessage.put(mes.getNick(), new LastEvents());
					else
					{
						recent.newEvent();
						long average = recent.average();
						if (average < AVERAGE_MESSAGE_GAP)
						{
							// It's actually pretty hard to work out how long
							// it'll be before they next won't get ignored if
							// we change the queue length...
							if (recent.warn())
								irc.sendContextReply(mes, "You're flooding, ignored. Please wait at least " + (AVERAGE_MESSAGE_GAP/1000.0) + "s between your messages.");
							return;
						}
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
