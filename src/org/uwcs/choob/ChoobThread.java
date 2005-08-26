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
import java.util.*;
import java.util.regex.*;

/**
 * Worker thread. Waits on it's waitObject and then wakes, performs the operations
 * required on the line from IRC and then goes back to sleep.
 * @author sadiq
 */
public class ChoobThread extends Thread
{
	boolean running;
	Object waitObject;
	DbConnectionBroker dbBroker;
	Connection dbConnection;
	String trigger;
	Modules modules;
	int threadID;
	Map pluginMap;
	IRCInterface irc;
	List filterList;

	private anEvent tevent;
	private Message mes;

	/**
	 * Holds value of property busy.
	 */
	private boolean busy;

	/** Creates a new instance of ChoobThread */
	public ChoobThread(DbConnectionBroker dbBroker, Modules modules, Map pluginMap, List filterList, String trigger)
	{
		waitObject = new Object();

		this.dbBroker = dbBroker;

		this.modules = modules;

		this.trigger = trigger;

		threadID = (int)(Math.random() * 1000);

		this.pluginMap = pluginMap;

		this.filterList = filterList;
	}

	public void run()
	{
		running = true;

		while( running )
		{
			try
			{
				synchronized( waitObject )
				{

					busy = false;

					waitObject.wait();

					busy = true;

					if (tevent.getClass() == Message.class)
					{
						mes=(Message)tevent;
						tevent=null;

						System.out.println("Thread("+threadID+") handled line " + mes.getText());
						Pattern pa;
						Matcher ma;

						// First, try and pick up aliased commands.
						pa = Pattern.compile("^" + trigger + "([a-zA-Z0-9_-]+)(?!\\.)(.*)$");
						ma = pa.matcher(mes.getText());

						if ( ma.matches() == true )
						{
							dbConnection = dbBroker.getConnection();

							PreparedStatement aliasesSmt = dbConnection.prepareStatement("SELECT `Converted` FROM `Aliases` WHERE `Name` = ?;");
							aliasesSmt.setString(1, ma.group(1).toLowerCase());

							ResultSet aliasesResults = aliasesSmt.executeQuery();
							if ( aliasesResults.first() )
								mes.setText(trigger + aliasesResults.getString("Converted") + ma.group(2));

							dbBroker.freeConnection( dbConnection );
						}

						// Now, continue as if nothing has happened..


						// The .* in this pattern is required, java wants the entire string to match.
						pa = Pattern.compile("^" + trigger + "([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_-]+).*");
						ma = pa.matcher(mes.getText());

						if( ma.matches() == true )
						{

							// Namespace alias code would go here

							String pluginName  = ma.group(1);
							String commandName = ma.group(2);

							System.out.println("Looking for plugin " + pluginName + " and command " + commandName);

							if( pluginMap.get(pluginName) != null )
							{
								System.out.println("Map for " + pluginName + " is not null, calling.");
								Object tempPlugin = ((Object)pluginMap.get(pluginName));

								BeanshellPluginUtils.doCommand(tempPlugin, commandName, mes, modules, irc);
							}
							else
								System.out.println("Plugin not found.");
						}

						List matchedFilters = new ArrayList();

						synchronized( filterList )
						{
							Iterator tempIt = filterList.iterator();

							while( tempIt.hasNext() )
							{
								Filter tempFilter = (Filter)tempIt.next();

								Pattern filterPattern = Pattern.compile( tempFilter.getRegex() );

								Matcher filterMatcher = filterPattern.matcher(mes.getText());

								System.out.println("Testing line against " + tempFilter.getRegex());

								if( filterMatcher.matches() )
								{
									matchedFilters.add( tempFilter );
								}
							}
						}

						Iterator tempIt = matchedFilters.iterator();

						while( tempIt.hasNext() )
						{
							Filter tempFilter = (Filter)tempIt.next();

							BeanshellPluginUtils.doFilter(pluginMap.get( tempFilter.getPlugin() ), tempFilter.getName(), mes, modules, irc);
						}
					}
					else
					{
						// Oh, god, please.. no..

						System.out.println("Thread("+threadID+") handled an event.");
						Object plugins[] = pluginMap.values().toArray();
						for (int i=0; i<plugins.length; i++)
							try
							{
								BeanshellPluginUtils.doEvent(plugins[i], ((ChannelEvent)tevent).getMethodName(), (ChannelEvent)tevent, modules, irc);
							}
							catch (Exception e)
							{
								System.out.println("OH NOES");
								e.printStackTrace();
							}



					}


				}
			}
			catch( Exception e )
			{
				System.out.println("Exception: " + e);
				e.printStackTrace();
			}
			finally
			{
				busy = false;
			}
		}
	}

	public anEvent getEvent()
	{
		return this.tevent;
	}

	public void setEvent(anEvent tevent)
	{
		System.out.println("Event set for thread("+threadID+")");
		this.tevent = tevent;
	}

	/**
	 * Getter method for the thread's current IRCInterface object.
	 * @return Value of property irc.
	 */
	public IRCInterface getIRC()
	{
		return this.irc;
	}

	/**
	 * Setter method for the thread's current IRCInterface object.
	 * @param context New value of property irc.
	 */
	public void setIRC(IRCInterface irc)
	{
		this.irc = irc;
	}

	/**
	 * Checks whether the thread is busy or not.
	 * @return Value of property busy.
	 */
	public boolean isBusy()
	{
		return this.busy;
	}

	/**
	 * Getter for waitObject.
	 * @return Value of property waitObject.
	 */
	public Object getWaitObject()
	{
		return this.waitObject;
	}

	/**
	 * Setter for waitObject.
	 * @param waitObject New value of property waitObject.
	 */
	public void setWaitObject(Object waitObject)
	{
		this.waitObject = waitObject;
	}

	/**
	 * Stops the thread performing another processing loop. Does not immediately terminate
	 * thread execution.
	 */
	public void stopRunning()
	{
		running = false;
	}
}
