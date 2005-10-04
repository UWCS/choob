/*
 * Choob.java
 *
 * Created on June 1, 2005, 2:20 AM
 */

/**
 *
 * @author	sadiq
 */
package org.uwcs.choob;

import org.jibble.pircbot.*;

/**
 * Main class in the Choob project, simply creates a Choob instance and sets it
 * running.
 */
public class ChoobMain
{

	public static void main(String[] args)
	{

		// Now start our bot up.
		try
		{
			Choob bot = new Choob();

			bot.init();

			// Enable debugging output.
			bot.setVerbose(true);

			// Connect to the IRC server.
			bot.connect(bot.server);

			// Set mode +B (is a bot)
			bot.sendRawLineViaQueue("MODE " + bot.getName() + " +B");

			// Join the channels.
			for (int i=0; i<bot.channels.length; i++)
				bot.joinChannel(bot.channels[i]);

		}
		catch( Exception e )
		{
			e.printStackTrace();
			System.out.println("Fatal exception in setting up bot. Exiting.");
			System.exit(1);
		}

	}

}
