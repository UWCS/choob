/*
 * ChoobMain.java
 *
 * Created on June 1, 2005, 2:20 AM
 */

package uk.co.uwcs.choob;

import java.io.File;
import java.util.Arrays;

/**
 * Main class in the Choob project, simply creates a Choob instance.
 */
public final class ChoobMain
{
	public static void main(final String[] args)
	{
		try
		{
			if (0 == args.length) {
				new Choob();
			} else if ("setup".equals(args[0])) {
				ChoobSetupCLI.main(Arrays.copyOfRange(args, 1, args.length));
			} else {
				System.out.println("Usage: choob");
				System.out.println("Usage: choob [setup ...]");
			}
		}
		catch (final Throwable t)
		{
			System.err.println("Fatal error in Choob, exiting.");
			t.printStackTrace();
			System.exit(1);
		}

	}

	public static final String DEFAULT_TEMP_LOCATION = "tmp";
	public static final File TEMP_FOLDER;

	static {
		String temp = System.getProperty("choobTempDir");
		if (null == temp) {
			temp = DEFAULT_TEMP_LOCATION;
		}

		TEMP_FOLDER = new File(temp);
		if (DEFAULT_TEMP_LOCATION == temp) {
			TEMP_FOLDER.mkdirs();
		}
	}


}