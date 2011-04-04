package uk.co.uwcs.choob.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import org.hibernate.SessionFactory;

public interface ConnectionBroker {

	/** Checkout a Connection. */
	public abstract Connection getConnection() throws SQLException;

	/** Free (check-back-in) a checked-out Connection. */
	public abstract void freeConnection(final Connection conn);

	/** Free all of the resources allocated by this DbConnectionBroker */
	public abstract void destroy() throws SQLException;

	public abstract Map<Object, SessionFactory> getFactories();

}