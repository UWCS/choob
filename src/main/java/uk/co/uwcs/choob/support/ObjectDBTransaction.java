package uk.co.uwcs.choob.support;

import java.security.AccessController;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.DOMWriter;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.mozilla.javascript.Function;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.modules.ObjectDbModule;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

/**
 * Wraps up the database in an ObjectDB-friendly way, which can be used to
 * perform various operations, such as adding, replacing and deleting items
 * in a single transaction (so either all, or none, of the operations occur).
 *
 * Plugins (with the necessary permissions) and core code can use it thus:
 *
 * <pre>
 *   mods.odb.runTransaction(
 *       new ObjectDBTransaction() {
 *           public void run() {
 *               // ObjectDB operations here, e.g.
 *               //   delete(o);
 *               //   save(o);
 *           }
 *       });
 * </pre>
 *
 */
public class ObjectDBTransaction // Needs to be non-final
{
	private Map<Object, SessionFactory> sessionFactories;

	private static interface WithSession<T> {
		T use(Session sess);
	}

	private Session sessionFor(ObjectDBObject clazz) {
		final Object ident = clazz.getIdentity();
		// XXX HAAAAAAAAAAACK
		if (ident instanceof Class) {
			Thread.currentThread().setContextClassLoader(((Class<?>)ident).getClassLoader());
		}

		synchronized (sessionFactories) {
			{
				final SessionFactory sess = sessionFactories.get(ident);
				if (null != sess)
					return sess.openSession(dbConn);
			}

			final String packageName = clazz.getPackageName();
			final String simpleName = clazz.getSimpleName();
			final Iterable<String> fields = Iterables.filter(Arrays.asList(clazz.getFields()),
					new Predicate<String>() {
						@Override
						public boolean apply(String input) {
							return !input.equals("id");
						}
					});

			final Configuration cfg = new Configuration()
				.setProperty("hibernate.dialect", mods.odb.getDialect())
				.addDocument(configFor(packageName, simpleName, fields,
						clazz.getNameField(), clazz.getNameValue(), clazz.getTypeOverloads()));
			new SchemaExport(cfg, dbConn).execute(false, true, false, false);
			SessionFactory sess = cfg.buildSessionFactory();
			sessionFactories.put(ident, sess);
			return sess.openSession(dbConn);
		}
	}

	private Connection dbConn;
	private Modules mods;

	public final void setMods(Modules mods)
	{
		this.mods = mods;
	}

	public final void setConn(Connection dbConn, Map<Object, SessionFactory> factories)
	{
		this.dbConn = dbConn;
		this.sessionFactories = factories;
	}

	/**
	 * Starts the transaction, during which <i>all</i> operations will either
	 * succeed or fail.
	 *
	 * The transaction is completed when {@link #commit} is called, or canceled
	 * if {@link #rollback} is called. {@link #finish} should always be called
	 * after {@link #commit} or {@link #rollback} is called, and is the mirror
	 * function to this one.
	 *
	 * @throws ObjectDBDeadlockError If the database has detected a deadlock,
	 *                               this exception is thrown.
	 * @throws ObjectDBError All other SQL-related exceptions are wrapped as
	 *                       ObjectDBError.
	 */
	public final void begin()
	{
		try
		{
			dbConn.setAutoCommit(false);
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
	}

	/**
	 * Commits the transaction started by {@link #begin}, attempting to
	 * apply all operations to the database. It is possible that this will
	 * fail if, for example, the data being modified by this transaction has
	 * already been modified by another successful transaction.
	 * {@link #finish} must still be called afterwards.
	 *
	 * @throws ObjectDBDeadlockError If the database has detected a deadlock,
	 *                               this exception is thrown.
	 * @throws ObjectDBError All other SQL-related exceptions are wrapped as
	 *                       ObjectDBError.
	 */
	public final void commit()
	{
		try
		{
			dbConn.commit();
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
	}

	/**
	 * Cancels the transaction started by {@link #begin}, causing all
	 * modifications made since then to be discarded.
	 * {@link #finish} must still be called afterwards.
	 *
	 * @throws ObjectDBDeadlockError If the database has detected a deadlock,
	 *                               this exception is thrown.
	 * @throws ObjectDBError All other SQL-related exceptions are wrapped as
	 *                       ObjectDBError.
	 */
	public final void rollback()
	{
		try
		{
			dbConn.rollback();
		}
		catch (SQLException e)
		{
			throw sqlErr(e);
		}
	}

	/**
	 * Finishes off the transaction started by {@link #begin}. If
	 * {@link #commit} has not been called, an implicit {@link #rollback}
	 * call is made first.
	 *
	 * @throws ObjectDBDeadlockError If the database has detected a deadlock,
	 *                               this exception is thrown.
	 * @throws ObjectDBError All other SQL-related exceptions are wrapped as
	 *                       ObjectDBError.
	 */
	public final void finish()
	{
		try
		{
			rollback(); // If not committed yet, must roll back changes...
		}
		finally
		{
			try
			{
				dbConn.setAutoCommit(true);
			}
			catch (SQLException e)
			{
				// I'd hate to see this happen...
				throw sqlErr(e);
			}
		}
	}

	private final ChoobError sqlErr(SQLException e)
	{
		// Deadlock hack. XXX MySQL specific code.
		if (e.getErrorCode() == 1213)
		{
			throw new ObjectDBDeadlockError();
		}
		System.err.println("Ack! SQL Exception: " + e);
		e.printStackTrace();
		return new ObjectDBError("An SQL exception occurred while processing this operation.", e);
	}

	private static String getTableName(final String fullClassName) {
		return "_objectdb_" + fullClassName.toLowerCase().replaceAll("\\.", "_");
	}

	/**
	 * Loads any number of stored ObjectDB objects.
	 *
	 * @param storedClass The class object (decendant of {@link Class} for Java,
	 *                    {@link Function} for JavaScript) representing the
	 *                    type of object desired to be retrieved.
	 * @param clause The testricting part of the query, specifying which objects
	 *               are desired. FIXME: link to docs on format.
	 * @return {@link List} of objects, typed according to the caller.
	 */
	public final <T> List<T> retrieve(Class<T> storedClass, String clause)
	{
		return retrieve(newClassWrapper(storedClass), clause);
	}

	/**
	 * Loads any number of stored ObjectDB objects.
	 *
	 * @param storedClass The class object (decendant of {@link Class} for Java,
	 *		    {@link Function} for JavaScript) representing the
	 *		    type of object desired to be retrieved.
	 * @param clause The testricting part of the query, specifying which objects
	 *	       are desired. FIXME: link to docs on format.
	 * @return {@link List} of objects, typed according to the caller.
	 */
	@SuppressWarnings("unchecked")
	public final List retrieve(Object storedClass, String clause)
	{
		return retrieve(newClassWrapper(storedClass), clause);
	}


	/**
	 * Loads any number of stored ObjectDB objects.
	 *
	 * @param storedClass The {@link ObjectDBClass} indicating the type of
	 *                    object desired to be retrieved.
	 * @param clause The testricting part of the query, specifying which objects
	 *               are desired. FIXME: link to docs on format.
	 * @return {@link List} of objects, typed according to the caller.
	 */
	public final <T> List<T> retrieve(final ObjectDBClass<T> storedClass, String whereClause)
	{
		final String clause;

		if (whereClause == null || whereClause.isEmpty())
			clause = "WHERE 1=1";
		else
			clause = whereClause;

		final ObjectDBObject ow = fakeObject(storedClass);

		return withHibernate(ow, new WithSession<List<T>>() {
			@SuppressWarnings("unchecked")
			@Override
			public List<T> use(Session sess) {
				final String query = "from " + storedClass.getName() + " " + clause;
				return sess.createQuery(query).list();
			}
		});
	}

	private <T> ObjectDBObject fakeObject(final ObjectDBClass<T> storedClass) {
		try {
			return newObjectWrapper(storedClass.newInstance());
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Deletes an object from the ObjectDB.
	 *
	 * @param strObj The object to be deleted.
	 */
	public final void delete(final Object strObj)
	{
		withHibernate(newObjectWrapper(strObj), new WithSession<Void>() {
			@Override
			public Void use(Session sess) {
				sess.delete(strObj);
				return null;
			}
		});
	}

	/**
	 * Updates the saved data for an ObjectDB object.
	 *
	 * @param strObj The object who's saved data is to be updated.
	 */
	public final void update(final Object strObj)
	{
		withHibernate(newObjectWrapper(strObj), new WithSession<Void>() {
			@Override
			public Void use(Session sess) {
				sess.update(strObj);
				return null;
			}
		});
	}

	/**
	 * Method to be override in code that wishes to use the transaction support.
	 *
	 * This method is run when passed to {@link ObjectDbModule#runTransaction},
	 * inside calls to {@link #begin} and {@link #commit}. If this method throws
	 * an exception, {@link #rollback} is called instead.
	 */
	public void run()
	{
		throw new ObjectDBError("This transaction has no run() method...");
	}

	/**
	 * Saves a new ObjectDB object.
	 *
	 * @param strObj The object to be saved.
	 */
	public final void save(final Object strObj)
	{
		withHibernate(newObjectWrapper(strObj), new WithSession<Void>() {
			@Override
			public Void use(Session sess) {
				sess.persist(strObj);
				return null;
			}
		});
	}

	private <T> T withHibernate(ObjectDBObject clazz, WithSession<T> lambda) {
		checkPermission(clazz.getClassName());
		final Session sess = sessionFor(clazz);
		try {
			final Transaction tran = sess.beginTransaction();
			try {
				final T res = lambda.use(sess);
				tran.commit();
				return res;
			} finally {
				if (tran.isActive()) {
					tran.rollback();
				}
			}
		} finally {
			sess.disconnect();
		}
	}

	//packageName + "." + simpleName
	/** You are kidding, right? */
	private static org.w3c.dom.Document configFor(String packageName, String simpleName, Iterable<String> fields,
			String nameField, String nameValue, Map<String, String> typeOverloads) {
		final Document doc = DocumentHelper.createDocument();
		final Element mapping = doc.addElement("hibernate-mapping");
		if (!packageName.equals(""))
			mapping.addAttribute("package", packageName);

		final Element eClass =
			mapping
				.addAttribute("default-access", "field")
			.addElement("class")
				.addAttribute(nameField, nameValue)
				.addAttribute("table", getTableName(fullName(packageName, simpleName)));

		eClass
			.addElement("id")
				.addAttribute("name", "id")
				.addAttribute("type", "java.lang.Integer")
			.addElement("generator").addAttribute("class", "native");

		for (String name : fields) {
			final Element el = eClass.addElement("property");
			el.addAttribute("name", name);
			final String overload = typeOverloads.get(name);
			if (null != overload)
				el.addAttribute("type", overload);
		}
		try {
			return new DOMWriter().write(doc);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static String fullName(String packageName, String simpleName) {
		if (packageName.equals(""))
			return simpleName;
		return packageName + "." + simpleName;
	}

	private final Map<String,Object> permCache = new HashMap<String,Object>(); // Doesn't need sync.
	private final void checkPermission(String objClass)
	{
		String plugin = mods.security.getPluginName(0);
		String clsName = objClass.toLowerCase();
		if (plugin != null)
		{
			String plugName = "plugins." + plugin.toLowerCase() + ".";
			if ( clsName.startsWith(plugName) )
				return;
		}
		Object cache = permCache.get(clsName);
		if (cache == null)
		{
			AccessController.checkPermission(new ChoobPermission("objectdb."+clsName));
			permCache.put(clsName, new Object());
		}
		// Non-null cache ==> we passed this check before.
	}

	private final ObjectDBClass newClassWrapper(Object obj)
	{
		// Create the correct wrapper here.
		if (obj instanceof Class) {
			return newClassWrapper((Class)obj);
		}

		if (obj instanceof Function) {
			return new ObjectDBClassJSWrapper(obj);
		}
		return null;
	}

	private final <T> ObjectDBClass<T> newClassWrapper(Class<T> obj)
	{
		return new ObjectDBClassJavaWrapper<T>(obj);
	}

	private final ObjectDBObject newObjectWrapper(Object obj)
	{
		// Create the correct wrapper here.
		try {
			if (obj instanceof org.mozilla.javascript.NativeObject) {
				return new ObjectDBObjectJSWrapper(obj);
			}
			return new ObjectDBObjectJavaWrapper(obj);
		} catch (ChoobException e) {
		} catch (NoSuchFieldException e) {
			// Do nothing and let it fail?
		}
		return null;
	}
}
