package uk.co.uwcs.choob;

import static junit.framework.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.hibernate.SessionFactory;
import org.junit.After;
import org.junit.Before;

import uk.co.uwcs.choob.support.ConnectionBroker;

public abstract class AbstractPluginTest {
	public MinimalBot b;
	public BrokerUtil db;

	@Before
	public void setup() throws SQLException, ClassNotFoundException {
		// SQLite explodes if you pass a File#createTemporaryFile() in, no idea why
		new File(ChoobMain.DEFAULT_TEMP_LOCATION).mkdir();
		String tempFilePath = ChoobMain.DEFAULT_TEMP_LOCATION + "/test" + System.nanoTime() + ".db";
		new File(tempFilePath).deleteOnExit();

		Class.forName("org.sqlite.JDBC");
		final String url = "jdbc:sqlite:" + tempFilePath;

		final Map<Object, SessionFactory> sessionFactories = new WeakHashMap<Object, SessionFactory>();
		final ConnectionBroker broker = new ConnectionBroker() {
			final Set<Connection> open = Collections.newSetFromMap(new IdentityHashMap<Connection, Boolean>());

			@Override
			public Connection getConnection() throws SQLException {
				final Connection conn = DriverManager.getConnection(url);
				open.add(conn);
				return conn;
			}

			@Override
			public void freeConnection(Connection conn) {
				try {
					conn.close();
				} catch (SQLException e) {
					throw new RuntimeException("couldn't close", e);
				}
				if (!open.remove(conn))
					throw new RuntimeException("Attempt to remove a closed connection");
			}

			@Override
			public void destroy() throws SQLException {
				assertEquals(Collections.emptySet(), open);
			}

			@Override
			public Map<Object, SessionFactory> getFactories() {
				return sessionFactories;
			}
		};

		db = new BrokerUtil(broker);

		db.table("UserNodes", "NodeID, NodeName, NodeClass");
		db.table("GroupMembers", "GroupID, MemberID");
		db.table("UserNodePermissions", "NodeID, Type, Permission, Action");
		db.table("History",  "LineID, Type, Nick, Hostmask, Channel, Text, Time, Random");

		// see uk.co.uwcs.choob.support.UserNode.toString() for numbers
		db.sql("insert into UserNodes (NodeID, NodeName, NodeClass) values (0, 'root', 3)");
		db.sql("insert into UserNodes (NodeID, NodeName, NodeClass) values (1, 'anonymous', 3)");
		db.sql("insert into UserNodes (NodeID, NodeName, NodeClass) values (2, 'Alias', 2)");

		db.sql("insert into UserNodePermissions (NodeID, Type) values (0, 'java.security.AllPermission')");

		db.sql("insert into GroupMembers values (0,2)");

		b = new MinimalBot(broker);

		System.setProperty("choobDebuggerHack", "false");
	}

	@After
	public void shutdown() throws IOException {
		b.close();
		db.close();
	}

	public void assertGetsResposne(String expected, String command) {
		b.spinChannelMessage(command);
		assertEquals(expected, b.sentMessage());
	}

}
