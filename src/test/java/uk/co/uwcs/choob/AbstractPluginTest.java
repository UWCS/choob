package uk.co.uwcs.choob;

import org.junit.After;
import org.junit.Before;
import org.slf4j.LoggerFactory;
import uk.co.uwcs.choob.support.DbConnectionBroker;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.Assert.assertEquals;

public abstract class AbstractPluginTest {
	public MinimalBot b;
	public BrokerUtil db;

	@Before
	public void setup() throws SQLException {
		// SQLite explodes if you pass a File#createTemporaryFile() in, no idea why
		new File(ChoobMain.DEFAULT_TEMP_LOCATION).mkdir();
		String tempFilePath = ChoobMain.DEFAULT_TEMP_LOCATION + "/test" + System.nanoTime() + ".db";
		new File(tempFilePath).deleteOnExit();

		final DbConnectionBroker broker = new DbConnectionBroker("org.sqlite.JDBC",
			"jdbc:sqlite:" + tempFilePath,
			null, null, 0, 10,
			new PrintWriter(new LogWriter(LoggerFactory.getLogger(DriverManager.class))),
			60);
		db = new BrokerUtil(broker);

		db.table("UserNodes", "NodeID, NodeName, NodeClass");
		db.table("History", "LineID, Type, Nick, Hostmask, Channel, Text, Time, Random");

		b = new MinimalBot(broker);

		System.setProperty("choobDebuggerHack", "false");
	}

	@After
	public void shutdown() throws IOException {
		b.close();
	}

	void assertGetsResposne(String expected, String command) {
		b.spinChannelMessage(command);
		assertEquals(expected, b.sentMessage());
	}

}
