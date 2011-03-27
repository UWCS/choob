package uk.co.uwcs.choob;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.DbConnectionBroker;

public class TalkTest {
	MinimalBot b;
	BrokerUtil db;

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

	@Test
	public void testTalk() throws InterruptedException, ChoobException {
		b.addPlugin("Talk");
		b.spinChannelMessage("~talk.say hi");
		assertEquals("#chan hi", b.sentMessage());
	}

	@Test
	public void testReply() throws InterruptedException, ChoobException {
		b.addPlugin("Talk");
		b.spinChannelMessage("~talk.reply hi");
		assertEquals("#chan user: hi", b.sentMessage());
	}
}
