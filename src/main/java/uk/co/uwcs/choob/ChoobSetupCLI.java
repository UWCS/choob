package uk.co.uwcs.choob;

import java.util.HashMap;
import java.util.Map;

/**
 * Set up the database to get Choob running.
 *
 * @author benji
 */
public class ChoobSetupCLI
{

	private static final ParamHandler dbServer = new ParamHandler("The database server to use.", "Database server must be set.");
	private static final ParamHandler dbUser = new ParamHandler("The username to use when connecting to the database server.", "dbUser must be set.");
	private static final ParamHandler database = new ParamHandler("The database name.", "database must be set.");
	private static final ParamHandler dbPass = new ParamHandler("The password to use when connecting to the database server.", "dbPass must be set.");
	private static final ParamHandler botName = new ParamHandler("The irc nick of your bot.", "botName must be set.");
	private static final ParamHandler ircServer = new ParamHandler("The irc server to connect to.", "ircServer must be set.");
	private static final ParamHandler ircChannel = new ParamHandler("The irc channel(s) to connect to (comma separate)", "ircChannel must be set.");
	private static final ParamHandler rootUser = new ParamHandler("The irc user to have full permissions on the bot", "rootUser must be set.");
	private static final Map<String, ParamHandler> params = new HashMap<String, ParamHandler>()
	{


		{
			put("dbServer", dbServer);
			put("dbUser", dbUser);
			put("database", database);
			put("dbPass", dbPass);
			put("botName", botName);
			put("ircServer", ircServer);
			put("ircChannel", ircChannel);
			put("rootUser", rootUser);
		}
	};

	private static void readParams(final String[] args) throws InvalidUsageException
	{
		if (args.length != params.size())
		{
			throw new InvalidUsageException();
		}
		for (final String arg : args)
		{
			final String[] parts = arg.split("=");
			if (parts.length != 2)
			{
				throw new InvalidUsageException("All parameters should be in the form Key=Value");
			}
			final ParamHandler ph;
			if ((ph = params.get(parts[0])) != null)
			{
				ph.setValue(parts[1]);
			} else
			{
				throw new InvalidUsageException("Unknown parameter " + parts[0]);
			}
		}
	}

	private static void printUsageAndExit(final InvalidUsageException e)
	{
		System.err.println("*** WARNING: Running ChoobSetup will overwrite your bot config and database. ***");

		System.err.println(e.getMessage());
		System.err.println();
		System.err.print("Usage: $ ChoobSetup ");
		for (final String key : params.keySet())
		{
			System.err.print(key + "=<value> ");
		}
		System.err.println();
		System.err.println("Where...");
		for (final String key : params.keySet())
		{
			System.err.println(key + " is " + params.get(key).getHelp());
		}
		System.exit(-1);
	}

	public static void main(final String[] args)
	{
		try
		{
			readParams(args);
			final ChoobSetup cs = new ChoobSetup
				(
					dbServer.getValue(),
					dbUser.getValue(),
					dbPass.getValue(),
					database.getValue(),
					botName.getValue(),
					ircServer.getValue(),
					ircChannel.getValue(),
					rootUser.getValue()
				);
			cs.setupChoob(new ChoobSetup.ChoobSetupStatus()
			{
				@Override public void onStatus(final int percent, final String message)
				{
					System.out.println(message);
				}
			});
		} catch (final InvalidUsageException e)
		{
			printUsageAndExit(e);
		} catch (final ChoobSetup.MissingFilesException e)
		{
			System.err.println(e.getMessage());
			System.exit(2);
		} catch (final ChoobSetup.ChoobSetupException e)
		{
			System.err.println(e.getMessage());
			e.printStackTrace();
			System.exit(3);
		}
	}
}

class InvalidUsageException extends Exception
{

	public InvalidUsageException()
	{
		super();
	}

	public InvalidUsageException(final String message)
	{
		super(message);
	}
}

class ParamHandler
{

	private final String help;
	private final String unsetMessage;
	private String value;

	public ParamHandler(final String help, final String unsetMessage)
	{
		this.help = help;
		this.unsetMessage = unsetMessage;
	}

	public String getHelp()
	{
		return help;
	}

	public String getValue() throws InvalidUsageException
	{
		if (this.value != null)
		{
			return this.value;
		}
		throw new InvalidUsageException(unsetMessage);
	}

	public void setValue(final String value)
	{
		this.value = value;
	}
}