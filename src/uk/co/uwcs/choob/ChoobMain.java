/*
 * ChoobMain.java
 *
 * Created on June 1, 2005, 2:20 AM
 */

package uk.co.uwcs.choob;

/**
 * Main class in the Choob project, simply creates a Choob instance.
 */
public final class ChoobMain
{
	public static void main(final String[] args)
	{
		try
		{
			new Choob();
		}
		catch (final Throwable t)
		{
			System.err.println("Fatal error in Choob, exiting.");
			t.printStackTrace();
			System.exit(1);
		}

	}
}