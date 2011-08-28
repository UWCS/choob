/*
 * ChoobSecurityManager.java
 *
 * Created on June 25, 2005, 3:28 PM
 */

package uk.co.uwcs.choob.modules;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PropertyPermission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.uwcs.choob.support.ChoobAuthError;
import uk.co.uwcs.choob.support.ChoobError;
import uk.co.uwcs.choob.support.ChoobEventExpired;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.ChoobFakeProtectionDomain;
import uk.co.uwcs.choob.support.ChoobGeneralAuthError;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.ChoobNoSuchPluginException;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.ChoobPluginAuthError;
import uk.co.uwcs.choob.support.ChoobProtectionDomain;
import uk.co.uwcs.choob.support.ChoobSpecialStackPermission;
import uk.co.uwcs.choob.support.ChoobUserAuthError;
import uk.co.uwcs.choob.support.DbConnectionBroker;
import uk.co.uwcs.choob.support.UserNode;
import uk.co.uwcs.choob.support.events.IRCEvent;
import uk.co.uwcs.choob.support.events.MessageEvent;
import uk.co.uwcs.choob.support.events.UserEvent;

/**
 * Security manager for plugins, access control to anything requiring/checking
 * for a permission.
 *
 * Also manages user, groups etc.
 *
 * @author	bucko
 */
public final class SecurityModule extends SecurityManager // For getClassContext(). Heh.
{
	private static final Logger logger = LoggerFactory.getLogger(SecurityModule.class);

	private final DbConnectionBroker dbBroker;
	private final Map<Integer,PermissionCollection> nodeMap;
	private final Map<Integer,List<Integer>> nodeTree;
	private final ArrayList<Map<String, Integer>> nodeIDCache;
	private final Modules mods;
	private final int anonID;

	/**
	 * Creates a new instance of SecurityModule
	 * @param dbBroker Database connection pool/broker.
	 */
	SecurityModule(final DbConnectionBroker dbBroker, final Modules mods)
	{
		this.dbBroker = dbBroker;
		this.mods = mods;

		this.nodeDbLock = new Object();

		this.nodeMap = new HashMap<Integer,PermissionCollection>();
		this.nodeTree = new HashMap<Integer,List<Integer>>();

		this.nodeIDCache = new ArrayList<Map<String, Integer>>();
		for (int i = 0; i < 4; i++) {
			nodeIDCache.add(new HashMap<String, Integer>());
		}

		this.anonID = getNodeIDFromNodeName("anonymous", 3);
	}

	/* =====================
	 * SecurityManager stuff
	 * =====================
	 */

	/**
	 * Returns an AccessControlContext which implies all permissions, but
	 * retains the plugin stack so that getPluginNames() will work.
	 */
	public AccessControlContext getPluginContext( )
	{
		return new AccessControlContext(new ProtectionDomain[] {
			getContextProtectionDomain()
		});
	}

	public ProtectionDomain getContextProtectionDomain()
	{
		return new ChoobFakeProtectionDomain(getPluginNames());
	}

	public ProtectionDomain getProtectionDomain(final String pluginName)
	{
		return new ChoobProtectionDomain(this, pluginName);
	}

	public List<String> getPluginNames()
	{
		return getPluginNames(null);
	}

	public List<String> getPluginNames(final String debugKey)
	{
		final List<String> pluginStack = new ArrayList<String>();
		// XXX HAX XXX HAX XXX HAX XXX HAX XXX
		// ^^ If this doesn't persuade you that this is a hack, nothing will...
		final ChoobSpecialStackPermission perm = new ChoobSpecialStackPermission(pluginStack);
		AccessController.checkPermission(perm);
		perm.patch();
		return pluginStack;
	}

	/**
	 * Gets the name of the nth plugin in the stack (0th = plugin #1).
	 * @param skip
	 * @return null if there is no plugin that far back. Otherwise the plugin
	 *         name.
	 */
	public String getPluginName(final int skip) {
		final List<String> names = getPluginNames();
		if (skip >= names.size()) {
			return null;
		}
		return names.get(skip);
	}

	/**
	 * Force plugin permissions to be reloaded at some later point.
	 */
	private void invalidateNodePermissions(final int nodeID)
	{
		synchronized(nodeMap) {
			nodeMap.remove(nodeID);
		}
	}

	/**
	 * Force plugin tree to be reloaded at some later point.
	 */
	private void invalidateNodeTree(final int nodeID)
	{
		synchronized(nodeMap) {
			nodeTree.remove(nodeID);
		}
	}

	private PermissionCollection getNodePermissions(final int nodeID)
	{
		PermissionCollection perms;
		synchronized(nodeMap)
		{
			perms = nodeMap.get(nodeID);
			if (perms == null)
			{
				updateNodePermissions(nodeID);
				perms = nodeMap.get(nodeID);
			}
		}
		return perms;
	}

	/**
	 * Update permissions set for the given node ID. Code viciously hacked out
	 * of ChoobSecurityManager.
	 */
	private void updateNodePermissions(final int nodeID) {
		Connection dbConnection = null;

		final Permissions permissions = new Permissions();

		PreparedStatement permissionsSmt = null;
		try {
			dbConnection=dbBroker.getConnection();
			permissionsSmt = dbConnection.prepareStatement("SELECT Type, Permission, Action FROM UserNodePermissions WHERE UserNodePermissions.NodeID = ?");

			permissionsSmt.setInt(1, nodeID);

			final ResultSet permissionsResults = permissionsSmt.executeQuery();

			if ( permissionsResults.first() ) {
				do
				{
					final String className = permissionsResults.getString(1);
					final String permissionName = permissionsResults.getString(2);
					final String actions = permissionsResults.getString(3);

					Class <?> clas;
					try
					{
						clas = Class.forName( className );
					}
					catch (final ClassNotFoundException e)
					{
						logger.error("Permission class not found: " + className);
						continue; // XXX I guess this is OK?
					}

					// Perhaps more strict checking here?
					// TODO - is this check enough to be secure?
					if (!Permission.class.isAssignableFrom(clas))
					{
						logger.error("Class " + className + " is not a Permission!");
						continue; // XXX
					}

					Constructor<?> con;
					try
					{
						con = clas.getDeclaredConstructor(String.class, String.class);
					}
					catch (final NoSuchMethodException e)
					{
						logger.error("Permission class had no valid constructor: " + className);
						continue; // XXX I guess this is OK?
					}

					Permission perm;
					try
					{
						perm = (Permission)con.newInstance(permissionName, actions);
					}
					catch (final IllegalAccessException e)
					{
						logger.error("Permission class constructor for " + className + " failed: ", e);
						continue; // XXX
					}
					catch (final InstantiationException e)
					{
						logger.error("Permission class constructor for " + className + " failed: ", e);
						continue; // XXX
					}
					catch (final InvocationTargetException e)
					{
						logger.error("Permission class constructor for " + className + " failed: ", e);
						continue; // XXX
					}

					permissions.add(perm);
				} while ( permissionsResults.next() );
			}
		}
		catch ( final SQLException e )
		{
			logger.error("Could not load DB permissions for user node " + nodeID + " (probably now incomplete permissions): ", e);
		}
		finally
		{
			try
			{
				dbCleanupSel(permissionsSmt, dbConnection);
			}
			catch (final ChoobError e) {}
		}

		synchronized(nodeMap) {
			nodeMap.put(nodeID, permissions);
		}
	}

	/**
	 * Check if the numbered user node has a permission
	 * @param permission
	 * @param userNode
	 */
	private boolean hasPerm(final Permission permission, final int userNode)
	{
		return hasPerm(permission, userNode, false);
	}

	private boolean hasPerm(final Permission permission, final int userNode, final boolean includeThis)
	{
		final Iterator<Integer> allNodes = getAllNodes(userNode, includeThis);

		if ( ! allNodes.hasNext() )
		{
			logger.info("User node " + userNode + " has no subnodes!");
			return false;
		}

		int nodeID;
		while( allNodes.hasNext() )
		{
			nodeID = allNodes.next();
			final PermissionCollection perms = getNodePermissions( nodeID );
			// Be careful to avoid invalid groups and stuff.
			if (perms != null && perms.implies(permission))
				return true;
		}

		return false;
	}

	/**
	 * Get all nodes linked to the passed node.
	 */
	private Iterator<Integer> getAllNodes(final int nodeID, final boolean addThis)
	{
		synchronized(nodeTree)
		{
			Connection dbCon;
			try
			{
				dbCon=dbBroker.getConnection();
			}
			catch (final SQLException e)
			{
				logger.error("Couldn't get a connection for getAllNodes()", e);
				return new ArrayList<Integer>().iterator(); // XXX
			}
			final List <Integer>list = new ArrayList<Integer>();
			if (addThis)
				list.add(nodeID);
			try
			{
				getAllNodesRecursive(dbCon, list, nodeID, 0);
				if (anonID != -1)
				{
					list.add(anonID);
					getAllNodesRecursive(dbCon, list, anonID, 0);
				}
			}
			finally
			{
				dbBroker.freeConnection(dbCon);
			}
			return list.iterator();
		}
	}

	private void getAllNodesRecursive(final Connection dbConn, final List<Integer> list, final int nodeID, final int recurseDepth)
	{
		if (recurseDepth >= 5)
		{
			logger.error("Ack! Recursion depth exceeded when trying to process user node " + nodeID);
			return;
		}

		List<Integer> things = nodeTree.get(nodeID);
		if (things == null)
		{
			things = new ArrayList<Integer>();
			nodeTree.put(nodeID, things);
			PreparedStatement stat = null;
			try
			{
				stat = dbConn.prepareStatement("SELECT GroupID FROM GroupMembers WHERE MemberID = ?");
				stat.setInt(1, nodeID);

				final ResultSet results = stat.executeQuery();

				if ( results.first() )
				{
					do
					{
						final int newNode = results.getInt(1);
						if ( !list.contains( newNode ) )
						{
							list.add( newNode );
						}
						things.add( newNode );
					} while ( results.next() );
				}
			}
			catch (final SQLException e)
			{
				sqlErr("getting user nodes", e);
			}
			finally
			{
				try
				{
					if (stat != null)
						stat.close();
				}
				catch (final SQLException e)
				{
					sqlErr("cleaning up user node statment", e);
				}
			}
		}

		for(final int newNode: things)
		{
			if (!list.contains(newNode))
				list.add(newNode);
			getAllNodesRecursive(dbConn, list, newNode, recurseDepth + 1);
		}
	}

	/**
	 * Get the node ID that corresponds to a node
	 */
	private int getNodeIDFromNode(final UserNode node)
	{
		return getNodeIDFromNodeName(node.getName(), node.getType());
	}

	/**
	 * Get the node ID that corresponds to a node name
	 */
	private int getNodeIDFromUserName(final UserEvent userEvent)
	{
		if (userEvent instanceof IRCEvent)
			checkEvent((IRCEvent)userEvent);
		return getNodeIDFromNodeName(userEvent.getNick(), 0);
	}

	/**
	 * Get the node ID that corresponds to a plugin
	 */
	private int getNodeIDFromPluginName(final String pluginName)
	{
		return getNodeIDFromNodeName(pluginName, 2);
	}

	/**
	 * Get the node ID that corresponds to a node name
	 */
	private int getNodeIDFromNodeName(final String nodeName, final int nodeType)
	{
		// Check the cache.
		final Integer id = nodeIDCache.get(nodeType).get(nodeName.toLowerCase());
		if (id != null) {
			return id.intValue();
		}

		Connection dbConn = null;
		PreparedStatement stat = null;
		try
		{
			dbConn = dbBroker.getConnection();
			stat = dbConn.prepareStatement("SELECT NodeID FROM UserNodes WHERE NodeName = ? AND NodeClass = ?");
			stat.setString(1, nodeName);
			stat.setInt(2, nodeType);
			final ResultSet results = stat.executeQuery();
			if ( results.next() )
			{
				final int idGot = results.getInt(1);
				nodeIDCache.get(nodeType).put(nodeName.toLowerCase(), idGot);
				return idGot;
			}
			logger.error("Ack! Node name " + nodeName + "(" + nodeType + ") not found!");
		}
		catch (final SQLException e)
		{
			sqlErr("getting a node ID", e);
		}
		finally
		{
			dbCleanupSel(stat, dbConn);
		}
		return -1;
	}

	/**
	 * Get the node ID that corresponds to a node name
	 */
	private UserNode getNodeFromNodeID(final int nodeID)
	{
		Connection dbConn = null;
		PreparedStatement stat = null;
		try
		{
			dbConn = dbBroker.getConnection();
			stat = dbConn.prepareStatement("SELECT NodeName, NodeClass FROM UserNodes WHERE NodeID = ?");
			stat.setInt(1, nodeID);
			final ResultSet results = stat.executeQuery();
			if ( results.first() )
			{
				return new UserNode(results.getString(1), results.getInt(2));
			}
			logger.error("Ack! Node " + nodeID + " not found!");
		}
		catch (final SQLException e)
		{
			logger.error("Ack! SQL exception when getting node from node ID " + nodeID + ": " + e);
		}
		finally
		{
			dbCleanupSel(stat, dbConn);
		}
		return null;
	}

	/**
	 * Get the last insert ID
	 */
	private int getLastInsertID(final Connection dbConn) throws SQLException
	{
		PreparedStatement stat = null;
		try
		{
			stat = dbConn.prepareStatement("SELECT LAST_INSERT_ID()");
			final ResultSet results = stat.executeQuery();
			if ( results.first() )
				return results.getInt(1);
			throw new SQLException("Ack! LAST_INSERT_ID() returned no results!");
		}
		finally
		{
			if (stat != null)
				stat.close();
		}
	}


	/* ================================
	 * PLUGIN PERMISSION CHECK ROUTINES
	 * ================================
	 */

	public String renderPermission(final Permission permission)
	{
		if (permission instanceof AllPermission)
			return "ALL";

		String output;
		final String className = permission.getClass().getSimpleName();
		if (className.endsWith("Permission"))
			output = className.substring(0, className.length() - 10);
		else
			output = className;

		final String name = permission.getName();
		final String actions = permission.getActions();
		if (name != null)
		{
			output += " with name \"" + name + "\"";
			if (actions != null)
				output += " and actions \"" + actions + "\"";
		}
		else if (actions != null)
			output += " and actions \"" + actions + "\"";

		return output;
	}

	/**
	 * Check if the given nickname has some form of authentication token
	 * @param userEvent The event to validate and check the permission on.
	 * @throws ChoobAuthError If the nick has no authentication
	 */
	public void checkAuth(final UserEvent userEvent) throws ChoobAuthError {
		if (!hasAuth(userEvent.getNick())) {
			throw new ChoobGeneralAuthError();
		}
	}

	/**
	 * Check if the given nickname has some form of authentication token.
	 * @param nick The nickname to validate.
	 * @throws ChoobAuthError If the nick has no authentication.
	 */
	public void checkAuth(final String nick) throws ChoobAuthError {
		if (!hasAuth(nick)) {
			throw new ChoobGeneralAuthError();
		}
	}

	/**
	 * Check if the given nickname has some form of authentication.
	 * @param userEvent The event to validate and check the permission on
	 * @return Whether the nick is authorised.
	 */
	public boolean hasAuth(final UserEvent userEvent) {
		if (userEvent instanceof IRCEvent) {
			checkEvent((IRCEvent)userEvent);
		}
		return hasAuth(userEvent.getNick());
	}

	/**
	 * Check if the given nickname has some form of authentication.
	 * @param nick The nickname to validate.
	 * @return Whether the nick is authorised.
	 */
	public boolean hasAuth(final String nick) {
		try {

			// Attempt to confirm which authentication module we are using
			String authPlugin = (String)mods.plugin.callAPI("AuthSelector", "GetAuthMethod");
			if (authPlugin == null) {
				authPlugin = "unknown";
			}
			authPlugin.toLowerCase();
			if (authPlugin.equals("nickserv") || authPlugin.equals("unknown")) {
				if (hasNS(nick)) {
					return true;
				}
			}

			if (authPlugin.equals("quakenet") || authPlugin.equals("unknown")){
				if (hasQ(nick)) {
					return true;
				}
			}

			// Unsupported setting - should not occur.
			return false;
		} catch (final ChoobNoSuchPluginException e) {
			// No options module, attempt all forms of auth until one works
			if (hasNS(nick)) {
				return true;
			}
			if (hasQ(nick)) {
				return true;
			}

			// No successful auth
			return false;
		} catch (final ChoobException e) {
			// Oh. Bugger
			logger.error("Authentication broken:", e);
			return false;
		}
	}

	/**
	 * Check if the given nickname is authed with NickServ (if it is loaded).
	 * @param nick The nickname to validate.
	 * @return Whethe the nick is authorised
	 */
	private boolean hasNS(final String nick) {
		try
		{

			//return (Boolean)mods.plugin.callAPI("NickServ", "Check", nickName, false);
			return (Boolean)mods.plugin.callAPI("NickServ", "Check", nick);
		}
		catch (final ChoobNoSuchPluginException e)
		{
			// XXX Should this throw an exception?:
			//if (!allowNoNS)
			//	throw new ChoobAuthError("The NickServ plugin is not loaded! Holy mother of God save us all!");
			return true;
		}
		catch (final ChoobException e)
		{
			// OMFG!
			logger.error("Error calling NickServ check! Details:", e);
			return false;
		}
	}

	/**
	 * Check if the nickname given is authed with Q (if QuakenetAuth is loaded)
	 * @param nick The nick to check.
	 * @return Auth status.
	 */
	private boolean hasQ(final String nick) {
		try {

			// Get the account name being used by the current nick
			final String account = (String)mods.plugin.callAPI("QuakenetAuth", "Account", nick);
			if (account == null) {
				return false;
			}

			// User has a current Q auth.
			return true;
		} catch (final ChoobNoSuchPluginException e) {
			// XXX Ohnoes, no quakenet stuffs!
			return false;
		} catch (final ChoobException e) {
			// Aieeeeee!
			logger.error("Error getting QuakenetAuth account! Details:", e);
			return false;
		}
	}
	/**
	 * Check if the given nickName has permission and is authed
	 * @param permission The permission to check.
	 * @param userEvent The event to validate and check the permission on.
	 * @throws ChoobAuthError If the nick is not authorised.
	 */
	public void checkNickPerm(final Permission permission, final UserEvent userEvent) throws ChoobAuthError	{
		checkAuth(userEvent);

		if (!hasNickPerm(permission, userEvent))
			throw new ChoobUserAuthError(permission);
	}

	/**
	 * Check if the given nickName has permission and is authed
	 * @param permission The permission to check.
	 * @param userEvent The event to validate and check the permission on.
	 * @return Whether the nick is authorised.
	 */
	public boolean hasNickPerm(final Permission permission, final UserEvent userEvent) {
		// XXX: Check for synthetic userEvent here.

		if (!hasAuth(userEvent)) {
			return false;
		}
		String authPlugin = "unknown";
		try {
			authPlugin = (String)mods.plugin.callAPI("AuthSelector", "GetAuthMethod");
			if (authPlugin == null) {
				authPlugin = "unknown";
			}
			authPlugin.toLowerCase();
		} catch (final ChoobNoSuchCallException e) {
			// No idea what auth method to use... do them all
			logger.error("No authentication method specified. Trying everything.");
		}

		if (authPlugin.equals("nickserv") || authPlugin.equals("unknown")) {
			if (hasNSPerm(permission, userEvent)) {
				return true;
			}
		}

		if (authPlugin.equals("quakenet") || authPlugin.equals("unknown")) {
			try {
				final String account = (String)mods.plugin.callAPI("QuakenetAuth", "Account", userEvent.getNick());
				if (hasQPerm(permission, account)) {
					return true;
				}
			} catch (final ChoobNoSuchCallException e) {
				// Oh dear. We can't do Q auth.
				logger.error("Can not perform quakenet authentication. Please load QuakenetAuth plugin.");
			}
		}

		// No valid authentication method found.
		return false;
	}

	/**
	 * Check if the given userName has permission.
	 * @deprecated Please use checkNickPerm ensure compatability with non-nickserv authentication methods.
	 * @param permission The permission to check.
	 * @param userEvent The event to validate and check the permission on.
	 */
	@Deprecated
	public void checkPerm(final Permission permission, final UserEvent userEvent) throws ChoobUserAuthError
	{
		checkNickPerm(permission, userEvent);
	}

	/**
	 * Check if the given userName has permission. Better to use checkNickPerm.
	 * @deprecated Please use hasNickPerm to ensure compatability with non-nickserv authentication methods.
	 * @param permission The permission to check.
	 * @param userEvent The event to validate and check the permission on.
	 */
	@Deprecated
	public boolean hasPerm(final Permission permission, final UserEvent userEvent) {
		return hasNickPerm(permission, userEvent);
	}

	/**
	 *
	 * @param permission
	 * @param userEvent
	 * @throws ChoobUserAuthError
	 */
	public void checkNSPerm(final Permission permission, final UserEvent userEvent) throws ChoobUserAuthError {
		if (!hasNSPerm(permission, userEvent)) {
			throw new ChoobUserAuthError(permission);
		}
	}

	/**
	 * Confirm if a nick has permission under the NS style (i.e. a nick is only authed, but has no account name)
	 * @param permission Permission to check.
	 * @param userEvent Event to validate and check permission on.
	 * @return Status of validation
	 */
	private boolean hasNSPerm(final Permission permission, final UserEvent userEvent) {
		// XXX: Check for synthetic userEvent here.

		final int userNode = getNodeIDFromUserName(userEvent);

		logger.info("Checking permission on user " + userEvent.getNick() + "(" + userNode + ")" + ": " + permission);

		if (userNode == -1)
			if (anonID != -1)
				return hasPerm(permission, anonID, true);

		return hasPerm(permission, userNode);

	}

	public void checkQPerm(final Permission permission, final String account) throws ChoobUserAuthError {
		if (!hasQPerm(permission, account)) {
			throw new ChoobUserAuthError(permission);
		}
	}
	/**
	 * Check if a given Q account has permission. Use checkNickPerm
	 * @param permission The permission to check
	 * @param account The Q account to use as a username in the check.
	 * @return Permissio check result.
	 */
	private boolean hasQPerm(final Permission permission, final String account) {
		final int userNode = getNodeIDFromNodeName(account, 0);
		if (userNode == -1) {
			if (anonID != -1) {
				return hasPerm(permission, anonID, true);
			}
		}

		return hasPerm(permission, userNode);
	}

	/**
	 * Check if the previous plugin on the call stack has a permission.
	 * @param permission Permission to query
	 * @throws ChoobPluginAuthError if the permission has not been granted.
	 */
	public void checkPluginPerm(final Permission permission) throws ChoobPluginAuthError
	{
		final String plugin = getPluginName(0);
		checkPluginPerm(permission, plugin);
	}

	/**
	 * Check if the previous plugin on the call stack has a permission
	 * @param permission Permission to query
	 */
	public boolean hasPluginPerm(final Permission permission)
	{
		return hasPluginPerm(permission, getPluginName(0));
	}

	/**
	 * Check if the previous plugin on the call stack has a permission.
	 * @param permission Permission to query
	 * @param skip Number of plugins to skip
	 * @throws ChoobPluginAuthError if the permission has not been granted.
	 */
	public void checkPluginPerm(final Permission permission, final int skip) throws ChoobPluginAuthError
	{
		final String plugin = getPluginName(skip);
		checkPluginPerm(permission, plugin);
	}

	/**
	 * Check if the skip'th plugin on the call stack has a permission
	 * @param permission Permission to query
	 * @param skip Number of plugins to skip
	 */
	public boolean hasPluginPerm(final Permission permission, final int skip)
	{
		return hasPluginPerm(permission, getPluginName(skip));
	}

	public String getCallerPluginName()
	{
		return getPluginName(1);
	}

	/**
	 * Check if the previous plugin on the call stack has a permission.
	 * @param permission Permission to query
	 * @param plugin Plugin to query
	 * @throws ChoobPluginAuthError if the permission has not been granted.
	 */
	public void checkPluginPerm(final Permission permission, final String plugin) throws ChoobPluginAuthError
	{
		if (!hasPluginPerm(permission, plugin))
			throw new ChoobPluginAuthError(plugin, permission);
	}

	/**
	 * Check if the passed plugin has a permission
	 * @param permission Permission to query
	 * @param plugin Plugin to query
	 */
	public boolean hasPluginPerm(final Permission permission, final String plugin)
	{
		if (plugin == null)
			return true; // XXX should this be true?

		// All plugins should be allowed to read the following properties:
		//   line.separator
		if (permission instanceof PropertyPermission)
		{
			final PropertyPermission propPerm = (PropertyPermission)permission;
			if (propPerm.getActions().equals("read"))
			{
				if (propPerm.getName().equals("line.separator"))
					return true;
			}
		}

		// Should prevent circular checks...
		final boolean rv = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
			@Override public Boolean run() {
				final int nodeID = getNodeIDFromPluginName( plugin );

				// No such user!
				if (nodeID == -1)
					return false;

				// Now just check on this node!
				// Include the plugin.
				return hasPerm( permission, nodeID, true );
			}
		}).booleanValue();

		if (!rv) {
			logger.info("Plugin " + plugin + " lacks permission " + permission + ".");
		}
		return rv;
	}

	/* ===============================
	 * PLUGIN USER MANAGEMENT ROUTINES
	 * ===============================
	 */

	private final Object nodeDbLock;

	/**
	 * Convenience method
	 */
	private void dbCleanupSel(final Statement stat, final Connection dbConn)
	{
		try
		{
			if (stat != null)
				stat.close();
		}
		catch (final SQLException e)
		{
			sqlErr("closing SQL statement", e);
		}
		finally
		{
			dbBroker.freeConnection(dbConn);
		}
	}

	private void dbCleanup(final Statement stat, final Connection dbConn)
	{
		try
		{
			if (stat != null)
				stat.close();
		}
		catch (final SQLException e)
		{
			sqlErr("closing SQL statement", e);
		}
		finally
		{
			try
			{
				dbConn.rollback(); // If success, this does nothing
				dbConn.setAutoCommit(true);
				dbBroker.freeConnection(dbConn);
			}
			catch (final SQLException e)
			{
				// XXX WTF to do here?
				sqlErr("cleaning up SQL connection", e);
			}
		}
	}

	/**
	 * Convenience method
	 */
	private void sqlErr(final String task, final SQLException e)
	{
		logger.error("ACK! SQL error when " + task + ": ", e);
		throw new ChoobError("An SQL error occurred when " + task + ". Please ask the bot administrator to check the logs.", e);
	}

	/**
	 * Add a plugin->user binding to the database
	 * @deprecated Perhaps?
	 */
	// TODO: Should this simply add plugin.pluginName to user.userName?
	@Deprecated
	public void bindPlugin(final String pluginName, final UserEvent userEvent) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("plugin.bind"));

		final int userID = getNodeIDFromUserName(userEvent);
		if ( userID == -1 )
		{
			throw new ChoobException("User " + userEvent.getNick() + " does not exist!");
		}

		Connection dbConn = null;
		PreparedStatement stat = null;
		synchronized(nodeDbLock)
		{
			try
			{
				dbConn = dbBroker.getConnection();
				// Bind plugin
				stat = dbConn.prepareStatement("REPLACE INTO UserPlugins (UserID, PluginName) VALUES (?, ?)");
				stat.setInt(1, userID);
				stat.setString(2, pluginName);
				if (stat.executeUpdate() == 0)
					logger.error("Ack! No rows updated in plugin bind!");

				// Done!
				dbConn.commit();
			}
			catch (final SQLException e)
			{
				sqlErr("binding plugin " + pluginName, e);
			}
			finally
			{
				dbCleanupSel(stat, dbConn);
			}
		}
	}
	// Must check (system) user.add

	/**
	 * Add a user to the database
	 */
	public void addUser(final String userName) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("user.add"));

		Connection dbConn = null;
		PreparedStatement stat = null;
		synchronized(nodeDbLock)
		{
			try
			{
				dbConn = dbBroker.getConnection();
				dbConn.setAutoCommit(false);
				// First, make sure no user exists...
				stat = dbConn.prepareStatement("SELECT NodeID FROM UserNodes WHERE NodeName = ? AND NodeClass = 0");
				stat.setString(1, userName);
				ResultSet results = stat.executeQuery();
				if ( results.first() )
				{
					throw new ChoobException ("User " + userName + " already exists!");
				}
				stat.close();

				// Add user and group
				stat = dbConn.prepareStatement("INSERT INTO UserNodes (NodeName, NodeClass) VALUES (?, ?)");
				stat.setString(1, userName);
				stat.setInt(2, 0);
				if (stat.executeUpdate() == 0)
					logger.error("Ack! No rows updated in user insert!");
				final int userID = getLastInsertID(dbConn);
				stat.close();

				// Note: group may already exist.
				int groupID = 0;
				stat = dbConn.prepareStatement("SELECT NodeID FROM UserNodes WHERE NodeName = ? AND NodeClass = 1");
				stat.setString(1, userName);
				results = stat.executeQuery();
				if (results.first())
				{
					groupID = results.getInt(1);
					stat.close();
				}
				else
				{
					stat.close();
					stat = dbConn.prepareStatement("INSERT INTO UserNodes (NodeName, NodeClass) VALUES (?, ?)");
					stat.setString(1, userName);
					stat.setInt(2, 1);
					if (stat.executeUpdate() == 0)
						logger.error("Ack! No rows updated in user group insert!");
					groupID = getLastInsertID(dbConn);
					stat.close();
				}

				// Now bind them.
				stat = dbConn.prepareStatement("INSERT INTO GroupMembers (GroupID, MemberID) VALUES (?, ?)");
				stat.setInt(1, groupID);
				stat.setInt(2, userID);
				if (stat.executeUpdate() == 0)
					logger.error("Ack! No rows updated in user group member insert!");

				// Done!
				dbConn.commit();
			}
			catch (final SQLException e)
			{
				sqlErr("adding user " + userName, e);
			}
			finally
			{
				dbCleanup(stat, dbConn); // If success, this does nothing
			}
		}
	}

	/**
	 * Links a user name to a root user name.
	 */
	public void linkUser(final String root, final String leaf) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("user.link"));

		Connection dbConn = null;
		PreparedStatement stat = null;
		synchronized(nodeDbLock)
		{
			try
			{
				dbConn = dbBroker.getConnection();
				dbConn.setAutoCommit(false);
				// First, make sure no user exists...
				stat = dbConn.prepareStatement("SELECT NodeID FROM UserNodes WHERE NodeName = ? AND NodeClass = 0");
				stat.setString(1, leaf);
				ResultSet results = stat.executeQuery();
				if ( results.first() )
					throw new ChoobException ("User " + leaf + " already exists!");
				stat.close();

				// Now make sure the root does exist...
				// As user
				stat = dbConn.prepareStatement("SELECT NodeID FROM UserNodes WHERE NodeName = ? AND NodeClass = 0");
				stat.setString(1, root);
				results = stat.executeQuery();
				if ( !results.first() )
					throw new ChoobException ("User " + root + " does not exist!");
				final int rootUserID = results.getInt(1);
				stat.close();

				// As group
				stat = dbConn.prepareStatement("SELECT NodeID FROM UserNodes WHERE NodeName = ? AND NodeClass = 1");
				stat.setString(1, root);
				results = stat.executeQuery();
				if ( !results.first() )
					throw new ChoobException ("User " + root + " is a leaf user. You can't link to it!");
				final int rootID = results.getInt(1);
				stat.close();

				// And that they're linked.
				stat = dbConn.prepareStatement("SELECT GroupID FROM GroupMembers WHERE GroupID = ? AND MemberID = ?");
				stat.setInt(1, rootID);
				stat.setInt(2, rootUserID);
				results = stat.executeQuery();
				if ( !results.first() )
					throw new ChoobException ("User " + root + " is a leaf user. You can't link to it!");
				stat.close();

				// Add user.
				stat = dbConn.prepareStatement("INSERT INTO UserNodes (NodeName, NodeClass) VALUES (?, ?)");
				stat.setString(1, leaf);
				stat.setInt(2, 0);
				if (stat.executeUpdate() == 0)
					logger.error("Ack! No rows updated in user insert!");
				final int userID = getLastInsertID(dbConn);
				stat.close();

				// Now bind it.
				stat = dbConn.prepareStatement("INSERT INTO GroupMembers (GroupID, MemberID) VALUES (?, ?)");
				stat.setInt(1, rootID);
				stat.setInt(2, userID);
				if (stat.executeUpdate() == 0)
					logger.error("Ack! No rows updated in user group member insert!");

				// Done!
				dbConn.commit();
			}
			catch (final SQLException e)
			{
				sqlErr("linking user " + leaf + " to root " + root, e);
			}
			finally
			{
				dbCleanup(stat, dbConn); // If success, this does nothing
			}
		}
	}

	/**
	 * Get the "root" username for a given user.
	 * @return the root username, or null
	 */
	public String getRootUser(final String userName)
	{
		Connection dbConn = null;
		PreparedStatement stat = null;
		try
		{
			dbConn = dbBroker.getConnection();
			// First, make sure no user exists...
			stat = dbConn.prepareStatement("SELECT NodeID FROM UserNodes WHERE NodeName = ? AND NodeClass = 0");
			stat.setString(1, userName);
			ResultSet results = stat.executeQuery();
			if ( !results.first() )
				//throw new ChoobException ("User " + userName + " does not exist!");
				return null;
				//return userName;
			final int userID = results.getInt(1);
			stat.close();

			stat = dbConn.prepareStatement("SELECT GroupID FROM GroupMembers WHERE MemberID = ?");
			stat.setInt(1, userID);
			results = stat.executeQuery();
			if ( !results.first() )
				throw new ChoobError ("Consistency error: User " + userName + " is in no group!");
			final int groupID = results.getInt(1);
			if ( results.next() )
				throw new ChoobError ("Consistency error: User " + userName + " is in more than one group!");
			stat.close();

			// Now make sure the root does exist...
			stat = dbConn.prepareStatement("SELECT NodeName FROM UserNodes WHERE NodeID = ?");
			stat.setInt(1, groupID);
			results = stat.executeQuery();
			if ( !results.first() )
				throw new ChoobError ("Consistency error: Group " + groupID + " does not exist!");
			final String groupName = results.getString(1);
			stat.close();

			return groupName;
		}
		catch (final SQLException e)
		{
			sqlErr("fetching root user name for " + userName, e);
		}
		finally
		{
			dbCleanupSel(stat, dbConn); // If success, this does nothing
		}
		return null; // Impossible to get here anyway...
	}

	/**
	 * Removes a user name (but not its groups).
	 */
	public void delUser(final String userName) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("user.del"));

		Connection dbConn = null;
		PreparedStatement stat = null;
		synchronized(nodeDbLock)
		{
			try
			{
				dbConn = dbBroker.getConnection();
				dbConn.setAutoCommit(false);
				// Make sure the user exists...
				stat = dbConn.prepareStatement("SELECT NodeID FROM UserNodes WHERE NodeName = ? AND NodeClass = 0");
				stat.setString(1, userName);
				final ResultSet results = stat.executeQuery();
				if ( !results.first() )
					throw new ChoobException ("User " + userName + " does not exist!");
				final int userID = results.getInt(1);
				stat.close();

				// First, unbind the user.
				stat = dbConn.prepareStatement("DELETE FROM GroupMembers WHERE MemberID = ?");
				stat.setInt(1, userID);
				if (stat.executeUpdate() == 0)
					logger.error("Ack! No rows updated in user member delete!");

				// Delete user.
				stat = dbConn.prepareStatement("DELETE FROM UserNodes WHERE NodeID = ?");
				stat.setInt(1, userID);
				if (stat.executeUpdate() == 0)
					logger.error("Ack! No rows updated in user delete!");
				stat.close();

				// Done!
				dbConn.commit();
			}
			catch (final SQLException e)
			{
				sqlErr("deleting user " + userName, e);
			}
			finally
			{
				dbCleanup(stat, dbConn); // If success, this does nothing
			}
		}
	}

	/**
	 * Add a user to the database
	 */
	public void addGroup(final String groupName) throws ChoobException
	{
		final UserNode group = new UserNode(groupName);

		if (group.getType() == 2) // plugins can poke their own groups!
		{
			final String pluginName = getPluginName(0);

			if (null != pluginName && group.getRootName().compareToIgnoreCase(pluginName) != 0)
				AccessController.checkPermission(new ChoobPermission("group.add."+groupName));
		}
		else
		{
			AccessController.checkPermission(new ChoobPermission("group.add."+groupName));
		}

		// OK, we're allowed to add.
		Connection dbConn = null;
		PreparedStatement stat = null;
		synchronized(nodeDbLock)
		{
			try
			{
				dbConn = dbBroker.getConnection();
				dbConn.setAutoCommit(false);
				stat = dbConn.prepareStatement("SELECT NodeID FROM UserNodes WHERE NodeName = ? AND NodeClass = ?");
				stat.setString(1, group.getName());
				stat.setInt(2, group.getType());
				final ResultSet results = stat.executeQuery();
				if ( results.next() )
				{
					throw new ChoobException("Group " + groupName + " already exists!");
				}
				stat.close();

				stat = dbConn.prepareStatement("INSERT INTO UserNodes (NodeName, NodeClass) VALUES (?, ?)");
				stat.setString(1, group.getName());
				stat.setInt(2, group.getType());
				if (stat.executeUpdate() == 0)
					logger.error("Ack! No rows updated in group " + groupName + " insert!");
				dbConn.commit();
			}
			catch (final SQLException e)
			{
				sqlErr("adding group " + groupName, e);
			}
			finally {
				dbCleanup(stat, dbConn); // If success, this does nothing
			}
		}
	}

	public void addUserToGroup(final String parentName, final String childName) throws ChoobException
	{
		final UserNode parent = new UserNode(parentName);
		final UserNode child = new UserNode(childName, true);
		addNodeToNode(parent, child);
	}

	public void addGroupToGroup(final String parentName, final String childName) throws ChoobException
	{
		final UserNode parent = new UserNode(parentName);
		final UserNode child = new UserNode(childName);
		addNodeToNode(parent, child);
	}

	public void addNodeToNode(final UserNode parent, final UserNode child) throws ChoobException
	{
		if (parent.getType() == 2) // plugins can poke their own groups!
		{
			final String pluginName = getPluginName(0);
			if (!(parent.getRootName().compareToIgnoreCase(pluginName)==0))
				AccessController.checkPermission(new ChoobPermission("group.members."+parent));
		}
		else
		{
			AccessController.checkPermission(new ChoobPermission("group.members."+parent));
		}

		// OK, we're allowed to add.
		final int parentID = getNodeIDFromNode(parent);
		final int childID = getNodeIDFromNode(child);
		if (parentID == -1)
			throw new ChoobException("Group " + parent + " does not exist!");
		if (childID == -1)
			throw new ChoobException("Group " + child + " does not exist!");

		Connection dbConn = null;
		PreparedStatement stat = null;
		synchronized(nodeDbLock)
		{
			try
			{
				dbConn = dbBroker.getConnection();
				dbConn.setAutoCommit(false);
				stat = dbConn.prepareStatement("SELECT MemberID FROM GroupMembers WHERE GroupID = ? AND MemberID = ?");
				stat.setInt(1, parentID);
				stat.setInt(2, childID);
				final ResultSet results = stat.executeQuery();
				if ( results.first() )
				{
					throw new ChoobException("Group " + parent + " already had member " + child);
				}
				stat.close();

				stat = dbConn.prepareStatement("INSERT INTO GroupMembers (GroupID, MemberID) VALUES (?, ?)");
				stat.setInt(1, parentID);
				stat.setInt(2, childID);
				if ( stat.executeUpdate() == 0 )
					logger.error("Ack! Group member add did nothing: " + parent + ", member " + child);

				dbConn.commit();
			}
			catch (final SQLException e)
			{
				sqlErr("adding " + child + " to group " + parent, e);
			}
			finally
			{
				dbCleanup(stat, dbConn); // If success, this does nothing
			}
		}
		invalidateNodeTree(childID);
	}

	public void removeUserFromGroup(final String parentName, final String childName) throws ChoobException
	{
		final UserNode parent = new UserNode(parentName);
		final UserNode child = new UserNode(childName, true);
		removeNodeFromNode(parent, child);
	}

	public void removeGroupFromGroup(final String parentName, final String childName) throws ChoobException
	{
		final UserNode parent = new UserNode(parentName);
		final UserNode child = new UserNode(childName);
		removeNodeFromNode(parent, child);
	}

	public void removeNodeFromNode(final UserNode parent, final UserNode child) throws ChoobException
	{
		if (parent.getType() == 2) // plugins can poke their own groups!
		{
			final String pluginName = getPluginName(0);
			if (!(parent.getRootName().compareToIgnoreCase(pluginName)==0))
				AccessController.checkPermission(new ChoobPermission("group.members."+parent));
		}
		else
		{
			AccessController.checkPermission(new ChoobPermission("group.members."+parent));
		}

		// OK, we're allowed to add.
		final int parentID = getNodeIDFromNode(parent);
		final int childID = getNodeIDFromNode(child);
		if (parentID == -1)
			throw new ChoobException("Group " + parent + " does not exist!");
		if (childID == -1)
			throw new ChoobException("Group " + child + " does not exist!");

		Connection dbConn = null;
		PreparedStatement stat = null;
		synchronized(nodeDbLock)
		{
			try
			{
				dbConn = dbBroker.getConnection();
				stat = dbConn.prepareStatement("SELECT MemberID FROM GroupMembers WHERE GroupID = ? AND MemberID = ?");
				stat.setInt(1, parentID);
				stat.setInt(2, childID);
				final ResultSet results = stat.executeQuery();
				if ( ! results.first() )
				{
					throw new ChoobException("Group " + parent + " did not have member " + child);
				}
				stat.close();

				stat = dbConn.prepareStatement("DELETE FROM GroupMembers WHERE GroupID = ? AND  MemberID = ?");
				stat.setInt(1, parentID);
				stat.setInt(2, childID);
				if ( stat.executeUpdate() == 0 )
					logger.error("Ack! Group member remove did nothing: " + parent + ", member " + child);
			}
			catch (final SQLException e)
			{
				sqlErr("removing " + child + " from " + parent, e);
			}
			finally
			{
				dbCleanupSel(stat, dbConn); // If success, this does nothing
			}
		}
		invalidateNodeTree(childID);
	}

	public void grantPermission(final String groupName, final Permission permission) throws ChoobException
	{
		final UserNode group = new UserNode(groupName);
		if (group.getType() == 2) // plugins can add their own permissions (kinda)
		{
			final String pluginName = getPluginName(0);
			if (null == pluginName || group.getRootName().compareToIgnoreCase(pluginName) != 0)
				AccessController.checkPermission(new ChoobPermission("group.grant."+group));
			// OK, that's all fine, BUT:
			if (!hasPluginPerm(permission))
			{
				logger.error("Plugin " + pluginName + " tried to grant permission " + permission + " it didn't have!");
				throw new ChoobException("A plugin may only grant permssions which it is entitled to.");
			}
		}
		else
		{
			AccessController.checkPermission(new ChoobPermission("group.grant."+group));
		}


		// OK, we're allowed to add.
		final int groupID = getNodeIDFromNode(group);
		if (groupID == -1)
			throw new ChoobException("Group " + group + " does not exist!");

		if (hasPerm(permission, groupID, true))
			throw new ChoobException("Group " + group + " already has permission " + permission + "!");

		Connection dbConn = null;
		PreparedStatement stat = null;
		synchronized(nodeDbLock)
		{
			try
			{
				dbConn = dbBroker.getConnection();

				if (permission instanceof AllPermission)
				{
					stat = dbConn.prepareStatement("DELETE FROM UserNodePermissions WHERE NodeID = ? AND Type = ?");
					stat.setInt(1, groupID);
					stat.setString(2, permission.getClass().getName());
				}
				else
				{
					stat = dbConn.prepareStatement("DELETE FROM UserNodePermissions WHERE NodeID = ? AND Type = ? AND Permission = ? AND Action = ?");
					stat.setInt(1, groupID);
					stat.setString(2, permission.getClass().getName());
					stat.setString(3, permission.getName());
					stat.setString(4, permission.getActions());
				}
				if ( stat.executeUpdate() == 0 )
				{
					// Don't care if this failed.
				}

				stat = dbConn.prepareStatement("INSERT INTO UserNodePermissions (NodeID, Type, Permission, Action) VALUES (?, ?, ?, ?)");
				stat.setInt(1, groupID);
				stat.setString(2, permission.getClass().getName());
				if (permission instanceof AllPermission)
				{
					stat.setString(3, "");
					stat.setString(4, "");
				}
				else
				{
					stat.setString(3, permission.getName());
					stat.setString(4, permission.getActions());
				}
				if ( stat.executeUpdate() == 0 )
					logger.error("Ack! Permission add did nothing: " + group + " " + permission);

				invalidateNodePermissions(groupID);
			}
			catch (final SQLException e)
			{
				sqlErr("adding permission " + permission + " to group " + group, e);
			}
			finally
			{
				dbCleanupSel(stat, dbConn); // If success, this does nothing
			}
		}
	}

	/**
	 * Attempt to work out from whence a group's permissions come.
	 */
	public String[] findPermission(final String groupName, final Permission permission) throws ChoobException
	{
		final UserNode group = new UserNode(groupName);
		final int groupID = getNodeIDFromNode(group);
		if (groupID == -1)
			throw new ChoobException("Group " + group + " does not exist!");

		final List<String> foundPerms = new LinkedList<String>();

		final Iterator<Integer> allNodes = getAllNodes(groupID, true);

		if ( ! allNodes.hasNext() )
		{
			return new String[0];
		}

		int nodeID;
		while( allNodes.hasNext() )
		{
			nodeID = allNodes.next();
			final PermissionCollection perms = getNodePermissions( nodeID );
			// Be careful to avoid invalid groups and stuff.
			if (perms != null && perms.implies(permission))
			{
				// Which element?
				final Enumeration<Permission> allPerms = perms.elements();
				while( allPerms.hasMoreElements() )
				{
					final Permission perm = allPerms.nextElement();
					if (perm.implies(permission))
					{
						foundPerms.add(getNodeFromNodeID(nodeID).toString() + perm);
					}
				}
			}
		}
		final String[] retVal = new String[foundPerms.size()];
		return foundPerms.toArray(retVal);
	}

	/**
	 * Get a list of permissions for a given group.
	 */
	public String[] getPermissions(final String groupName) throws ChoobException
	{
		final UserNode group = new UserNode(groupName);
		final int groupID = getNodeIDFromNode(group);
		if (groupID == -1)
			throw new ChoobException("Group " + group + " does not exist!");

		final List<String> foundPerms = new LinkedList<String>();

		final PermissionCollection perms = getNodePermissions( groupID );
		// Be careful to avoid invalid groups and stuff.
		if (perms != null)
		{
			// Which element?
			final Enumeration<Permission> allPerms = perms.elements();
			while( allPerms.hasMoreElements() )
			{
				final Permission perm = allPerms.nextElement();
				foundPerms.add(perm.toString());
			}
		}
		final String[] retVal = new String[foundPerms.size()];
		return foundPerms.toArray(retVal);
	}

	public void revokePermission(final String groupName, final Permission permission) throws ChoobException
	{
		final UserNode group = new UserNode(groupName);
		if (group.getType() == 2) // plugins can revoke their own permissions
		{
			final String pluginName = getPluginName(0);
			if (!(group.getRootName().compareToIgnoreCase(pluginName)==0))
				AccessController.checkPermission(new ChoobPermission("group.revoke."+group));
		}
		else
		{
			AccessController.checkPermission(new ChoobPermission("group.revoke."+group));
		}


		// OK, we're allowed to add.
		final int groupID = getNodeIDFromNode(group);
		if (groupID == -1)
			throw new ChoobException("Group " + group + " does not exist!");

		if (!hasPerm(permission, groupID, true))
			throw new ChoobException("Group " + group + " does not have permission " + permission + "!");

		Connection dbConn = null;
		PreparedStatement stat = null;
		synchronized(nodeDbLock)
		{
			try
			{
				dbConn = dbBroker.getConnection();
				if (permission instanceof AllPermission)
				{
					stat = dbConn.prepareStatement("DELETE FROM UserNodePermissions WHERE NodeID = ? AND Type = ?");
					stat.setInt(1, groupID);
					stat.setString(2, permission.getClass().getName());
				}
				else
				{
					stat = dbConn.prepareStatement("DELETE FROM UserNodePermissions WHERE NodeID = ? AND Type = ? AND Permission = ? AND Action = ?");
					stat.setInt(1, groupID);
					stat.setString(2, permission.getClass().getName());
					stat.setString(3, permission.getName());
					stat.setString(4, permission.getActions());
				}
				if ( stat.executeUpdate() == 0 )
				{
					// This is an ERROR here, not a warning
					throw new ChoobException("The given permission wasn't explicily assigned in the form you attempted to revoke. Try using the find permission command to locate it.");
				}

				invalidateNodePermissions(groupID);
			}
			catch (final SQLException e)
			{
				sqlErr("revoking permission " + permission + " from group " + group, e);
			}
			finally
			{
				dbCleanupSel(stat, dbConn); // If success, this does nothing
			}
		}
	}

	private void checkEvent(final IRCEvent e) throws ChoobEventExpired
	// This should probably accept a generic form of all events.
	{
		final Map<String,String> mesFlags = e.getFlags();
		if (mesFlags.containsKey("_securityOK"))
		{
			final String sok = mesFlags.get("_securityOK");
			if (sok.equals("true"))
				return;
		}

		if (e instanceof MessageEvent)
			throw new ChoobEventExpired("Security exception: " + e.getClass().getName() + " from " + new java.util.Date(e.getMillis()).toString() + " ('" + ((MessageEvent)e).getMessage() + "') failed security." );
		else
			throw new ChoobEventExpired("Security exception: " + e.getClass().getName() + " from " + new java.util.Date(e.getMillis()).toString() + " failed security." );
	}


	/**
	 * Get the auth name of a user for confirming that they are who they say they are
	 * @param nick The nick of the user to check for the account name of.
	 * @return The authenticated nick
	 */
	public String getUserAuthName(final String nick) {
		String authPlugin = "unknown";
		try {
			authPlugin = (String)mods.plugin.callAPI("AuthSelector", "GetAuthMethod");
			if (authPlugin == null) {
				authPlugin = "unknown";
			}
			authPlugin.toLowerCase();
		} catch (final ChoobNoSuchCallException e) {
			// No idea what auth method to use... assume it's their nickname then
			return nick;
		}

		// If quakenet then perform check
		if (authPlugin.equals("quakenet")) {
			try {
				final String authName = (String)mods.plugin.callAPI("QuakenetAuth", "Account", nick);
				if (authName != null) {
					return authName;
				} else {
					return nick;
				}
			} catch (final ChoobNoSuchCallException e) {
				// Oh, bugger, just give the nickname as it will fail at the has auth stage
				return nick;
			}
		}

		// Otherwise assume it is their nickname
		return nick;
	}
}
