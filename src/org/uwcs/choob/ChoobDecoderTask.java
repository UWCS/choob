/*
 * ChoobThread.java
 *
 * Created on June 16, 2005, 7:25 PM
 */

package org.uwcs.choob;

import org.uwcs.choob.plugins.*;
import org.uwcs.choob.modules.*;
import java.sql.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;

/**
 * Worker thread. Waits on it's waitObject and then wakes, performs the operations
 * required on the line from IRC and then goes back to sleep.
 * @author	sadiq
 */
final class ChoobDecoderTask extends ChoobTask
{
	private static DbConnectionBroker dbBroker;
	private static Modules modules;
	private static IRCInterface irc;
	private static String trigger;
	private static Pattern aliasPattern;
	private static Pattern commandPattern;
	private IRCEvent event;

	static void initialise(DbConnectionBroker dbBroker, Modules modules, IRCInterface irc, String trigger)
	{
		if (ChoobDecoderTask.dbBroker != null)
			return;
		ChoobDecoderTask.dbBroker = dbBroker;
		ChoobDecoderTask.modules = modules;
		ChoobDecoderTask.irc = irc;
		ChoobDecoderTask.trigger = trigger;
		aliasPattern = Pattern.compile("^" + trigger + "([a-zA-Z0-9_]+)$");
		commandPattern = Pattern.compile("^" + trigger + "([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)$");
	}

	/** Creates a new instance of ChoobThread */
	ChoobDecoderTask(IRCEvent event)
	{
		super(null);
		this.event = event;
	}

	public synchronized void run()
	{
		List<ChoobTask> tasks = new LinkedList<ChoobTask>();

		// Process event calls first
		tasks.addAll(modules.plugin.getPlugMan().eventTasks(event));

		// Then filters
		if (event instanceof FilterEvent)
		{
			// FilterEvents are messages
			Message mes = (Message) event;
			tasks.addAll(modules.plugin.getPlugMan().filterTasks(mes));

			// For now, only FilterEvents will be logged...
			modules.history.addLog( (Message) event );
		}

		// Now if it's a message, deal with that too
		if (event instanceof CommandEvent)
		{
			// CommandEvents are messages
			Message mes = (Message) event;
			Matcher ma;

			// First, try and pick up aliased commands.
			String matchAgainst = mes.getMessage();
			if (matchAgainst.indexOf(' ') >= 0)
				matchAgainst = matchAgainst.substring(0, matchAgainst.indexOf(' '));

			ma = aliasPattern.matcher(matchAgainst);

			if ( ma.matches() == true )
			{
				Connection dbConnection = dbBroker.getConnection();
				try
				{
					PreparedStatement aliasesSmt = dbConnection.prepareStatement("SELECT `Converted` FROM `Aliases` WHERE `Name` = ?;");
					aliasesSmt.setString(1, ma.group(1).toLowerCase());

					ResultSet aliasesResults = aliasesSmt.executeQuery();
					if ( aliasesResults.first() )
						matchAgainst = trigger + aliasesResults.getString("Converted");
				}
				catch (SQLException e)
				{
					System.out.println("SQL exception looking up an alias: " + e);
				}
				finally
				{
					dbBroker.freeConnection( dbConnection );
				}
			}

			// Now, continue as if nothing has happened..


			// The .* in this pattern is required, java wants the entire string to match.
			ma = commandPattern.matcher(matchAgainst);

			if( ma.matches() == true )
			{
				// Namespace alias code would go here

				String pluginName  = ma.group(1);
				String commandName = ma.group(2);

				System.out.println("Looking for plugin " + pluginName + " and command " + commandName);

				ChoobTask task = modules.plugin.getPlugMan().commandTask(pluginName, commandName, mes);
				if (task != null)
					tasks.add(task);
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
