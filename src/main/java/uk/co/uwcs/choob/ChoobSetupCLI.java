package uk.co.uwcs.choob;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Set up the database to get Choob running.
 *
 * @author benji
 */
public class ChoobSetupCLI
{

	private static final ParamHandler dbServer = new ParamHandler("The database server to use.",
			"Database server must be set.", "localhost");

	private static final ParamHandler dbUser = new ParamHandler(
			"The username to use when connecting to the database server.", "dbUser must be set.", "choob");

	private static final ParamHandler database = new ParamHandler("The database name.",
			"database must be set.", "choob");

	private static final ParamHandler dbPass = new ParamHandler(
			"The password to use when connecting to the database server.", "dbPass must be set.", "choob");

	private static final ParamHandler botName = new ParamHandler("The irc nick of your bot.",
			"botName must be set.", Choob.randomName());

	private static final ParamHandler ircServer = new ParamHandler("The irc server to connect to.",
			"ircServer must be set.", "irc.uwcs.co.uk");

	private static final ParamHandler ircChannel = new ParamHandler(
			"The irc channel(s) to connect to (comma separate)", "ircChannel must be set.", "#bots");

	private static final ParamHandler rootUser = new ParamHandler("The irc user to have full permissions on the bot",
			"rootUser must be set.", "BadgerBOT");

	private static final Map<String, ParamHandler> params = new LinkedHashMap<String, ParamHandler>()
	{
		{
			put("dbServer", dbServer);
			put("dbUser", dbUser);
			put("dbPass", dbPass);
			put("database", database);
			put("ircServer", ircServer);
			put("ircChannel", ircChannel);
			put("botNick", botName);
			put("rootNick", rootUser);
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

	private static void destroyDataWarning() {
		System.err.println("*** WARNING: Running ChoobSetup will overwrite your bot config and database. ***");
	}

	private static void printUsageAndExit(final InvalidUsageException e)
	{
		destroyDataWarning();

		final String msg = e.getMessage();
		if (null != msg)
			System.err.println(msg);
		System.err.println();
		System.err.print("Usage: $ ChoobSetup ");
		for (final String key : params.keySet())
		{
			System.err.print(key + "=<value> ");
		}
		System.err.println();
		System.err.println();
		System.err.println("Where...");
		for (final String key : params.keySet())
		{
			System.err.println(key + " is " + params.get(key).getHelp());
		}
		System.err.println();

		System.err.println("The user and database must exist.  i.e. you need to have run:");
		System.err.println("$ mysql -uroot -p");
		System.err.println("CREATE USER choob@localhost IDENTIFIED BY 'choob';");
		System.err.println("CREATE DATABASE choob;");
		System.err.println("GRANT ALL PRIVILEGES ON choob.* TO choob@localhost;");
		System.err.println();

		System.err.println("e.g.");
		System.err.print("$ ChoobSetup ");
		for (final Entry<String, ParamHandler> key : params.entrySet())
			System.err.print(key.getKey() + "=" + key.getValue().getExample() + " ");
		System.err.println();
		System.err.println();

		destroyDataWarning();

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
					System.out.println(message + ".");
				}
			});

			System.out.println("Completed sucessfully; bot should be ready to run.");
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
	private final String example;

	public ParamHandler(final String help, final String unsetMessage, String example)
	{
		this.help = help;
		this.unsetMessage = unsetMessage;
		this.example = example;
	}

	public String getHelp()
	{
		return help;
	}

	public String getExample() {
		return example;
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