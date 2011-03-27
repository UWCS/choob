package uk.co.uwcs.choob;

import static junit.framework.Assert.assertEquals;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;

import uk.co.uwcs.choob.support.DbConnectionBroker;

public abstract class AbstractPluginTest {
	public MinimalBot b;
	public BrokerUtil db;

	@Before
	public void setup() throws SQLException {
		final DbConnectionBroker broker = new DbConnectionBroker("org.sqlite.JDBC",
				"jdbc:sqlite:test" + System.nanoTime() + ".db",
				null, null, 0, 10, new PrintWriter(System.err), 60);
		db = new BrokerUtil(broker);

		db.table("UserNodes", "NodeID, NodeName, NodeClass");
		db.table("History",  "LineID, Type, Nick, Hostmask, Channel, Text, Time, Random");

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
