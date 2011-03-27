package uk.co.uwcs.choob;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import uk.co.uwcs.choob.support.DbConnectionBroker;

public class BrokerUtil {
	public BrokerUtil(final DbConnectionBroker broker) {
		this.broker = broker;
	}

	private final DbConnectionBroker broker;

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
		try {
			sql("drop table " + name);
		} catch (SQLException ignored) {
		}

		sql("create table " + name + " (" + fields + ")");
	}
}
