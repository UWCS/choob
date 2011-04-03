package uk.co.uwcs.choob;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import uk.co.uwcs.choob.support.ConnectionBroker;

public class BrokerUtil implements Closeable {
	public BrokerUtil(final ConnectionBroker broker) {
		this.broker = broker;
	}

	private final ConnectionBroker broker;

	public void sql(String sql) throws SQLException {
		final Connection conn = broker.getConnection();
		try {
			Statement stat = conn.createStatement();
			try {
				stat.execute(sql);
			} finally {
				stat.close();
			}
		} finally {
			broker.freeConnection(conn);
		}
	}

	public void table(String name, String fields) throws SQLException {
		sql("create table " + name + " (" + fields + ")");
	}

	@Override
	public void close() throws IOException {
		try {
			broker.destroy();
		} catch (SQLException e) {
			throw new IOException(e);
		}
	}
}
