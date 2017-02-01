package uk.co.uwcs.choob;

import io.github.harha.ircd.server.IRCServer;
import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.PircBot;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import static org.junit.Assert.assertEquals;
import static uk.co.uwcs.choob.TestTemps.newThread;

public class IntegrationTest
{
	static LocalMySQL db;
	static IRCServer irc;

	@Test
	public void test() throws IOException, IrcException, InterruptedException
	{
		final File conf = new File(db.base, "conf");
		conf.createNewFile();
		try (final PrintWriter pw = new PrintWriter(conf)) {
			pw.println("dbServer=localhost:" + db.port);
			pw.println("dbPass=choob");
			pw.println("server=localhost");
			pw.println("port=" + irc.getPort());

		}

		final DriverBot driver = new DriverBot();

		driver.changeNick("driver");
		driver.connect("localhost", irc.getPort());
		driver.joinChannel("#bots");

		final Thread bot = newThread(() -> new Choob(conf.getAbsolutePath()));
		bot.start();

		driver.await("join"); // us
		driver.await("join"); // them

		driver.sendMessage("#bots", "~admin.quit");
		assertEquals("PircBot: Command admin.quit not found, the plugin doesn't exist or isn't loaded.",
			driver.await("msg")[4]); // them
	}

	@BeforeClass
	public static void before() throws IOException, InterruptedException, SQLException
	{
		db = new LocalMySQL("/integration.sql");
		irc = new IRCServer(0);
		newThread(irc::run).start();
		newThread(irc::runUntilClosed).start();
	}

	@AfterClass
	public static void after() throws IOException
	{
		irc.getSocket().close();
		db.close();
	}

	private static class DriverBot extends PircBot
	{
		final BlockingDeque<Object[]> events = new LinkedBlockingDeque<>();

		@Override
		protected void onJoin(String channel, String sender, String login, String hostname)
		{
			events.add(new Object[]{"join", channel, sender, login, hostname});
		}

		@Override
		protected void onPrivateMessage(String sender, String login, String hostname, String message)
		{
			events.add(new Object[]{"privmsg", sender, login, hostname, message});
		}

		@Override
		protected void onMessage(String channel, String sender, String login, String hostname, String message)
		{
			events.add(new Object[]{"msg", sender, login, hostname, message});
		}

		public Object[] await(String type) throws InterruptedException
		{
			while (true) {
				final Object[] event = events.take();
				if (event[0].equals(type)) {
					return event;
				}
			}
		}
	}
}
