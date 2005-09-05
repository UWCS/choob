/*
 * ChoobSecurityManager.java
 *
 * Created on June 25, 2005, 3:28 PM
 */

package org.uwcs.choob.modules;

import bsh.classpath.*;
import java.lang.*;
import org.uwcs.choob.support.*;
import java.sql.*;
import java.security.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Security manager for plugins, access control to anything requiring/checking
 * for a permission.
 *
 * Also manages user, groups etc.
 *
 * @author	bucko
 */
public class SecurityModule
//	extends SecurityManager
{
	DbConnectionBroker dbBroker;
	Map nodeMap;

	/**
	 * Creates a new instance of SecurityModule
	 * @param dbBroker Database connection pool/broker.
	 */
	public SecurityModule(DbConnectionBroker dbBroker)
	{
		// Make sure these classes is preloaded!
		// This avoids circular security checks. Oh, the horror!
		//Class throwAway = bsh.BshMethod.class;
		//throwAway = DiscreteFilesClassLoader.class;

		this.dbBroker = dbBroker;
		this.nodeMap = new HashMap();
		this.nodeDbLock = new Object();
	}

	/* =====================
	 * SecurityManager stuff
	 * =====================
	 */

	/**
	 * Get an attempted hax at the protection domain context...
	 */
	public AccessControlContext getContext( String pluginName )
	{
		LinkedList pluginStack = getPluginNames();
		if ( pluginName != null )
			pluginStack.add( pluginName );
		ProtectionDomain domain = new ChoobProtectionDomain(this, pluginName);
		return new AccessControlContext(new ProtectionDomain[] { domain });
	}

	public ProtectionDomain getProtectionDomain( String pluginName )
	{
		return new ChoobProtectionDomain(this, pluginName);
	}

	private LinkedList getPluginNames()
	{
		LinkedList pluginStack = new LinkedList();
		// XXX HAX XXX HAX XXX HAX XXX HAX XXX
		// ^^ If this doesn't persuade you that this is a hack, nothing will...
		AccessController.checkPermission(new ChoobSpecialStackPermission(pluginStack));
		return pluginStack;
	}

	/**
	 * Gets the name of the nth plugin in the stack (0th = plugin #1).
	 * @param skip
	 * @return null if there is no plugin that far back. Otherwise the plugin
	 *         name.
	 */
	public String getPluginName(int skip)
	{
		List names = getPluginNames();
		if (skip > names.size())
			return null;
		return (String)names.get(skip);
	}

	/**
	 * Get the node ID that owns a named plugin
	 */
	private int getNodeIDFromPluginName(String pluginName)
	{
		Connection dbConn = dbBroker.getConnection();
		try
		{
			PreparedStatement stat = dbConn.prepareStatement("SELECT UserID FROM UserPlugins WHERE PluginName = ?");
			stat.setString(1, pluginName);
			ResultSet results = stat.executeQuery();
			if ( results.first() )
			{
				return results.getInt(1);
			}
			System.err.println("Ack! Plugin name " + pluginName + " not found!");
		}
		catch (SQLException e)
		{
			System.err.println("Ack! SQL exception when getting node from plugin name " + pluginName + ": " + e);
		}
		finally
		{
			dbBroker.freeConnection(dbConn);
		}
		return -1;
	}

	/**
	 * Force plugin permissions to be reloaded at some later point.
	 */
	public void invalidateNodePermissions(int nodeID)
	{
		synchronized(nodeMap) {
			nodeMap.remove(nodeID);
		}
	}

	private PermissionCollection getNodePermissions(int nodeID)
	{
		PermissionCollection perms;
		synchronized(nodeMap)
		{
			perms = (PermissionCollection)nodeMap.get(nodeID);
		}
		if (perms == null)
			updateNodePermissions(nodeID);
		synchronized(nodeMap)
		{
			perms = (PermissionCollection)nodeMap.get(nodeID);
		}
		return perms;
	}

	/**
	 * Update permissions set for the given node ID.
	 * Code visciously hacked out of ChoobSecurityManager.
	 */
	private void updateNodePermissions(int nodeID) {
		System.out.println("Loading permissions for user node " + nodeID + ".");
		Connection dbConnection = dbBroker.getConnection();

		Permissions permissions = new Permissions();

		try {
			PreparedStatement permissionsSmt = dbConnection.prepareStatement("SELECT Type, Permission, Action FROM UserNodePermissions WHERE UserNodePermissions.NodeID = ?");

			permissionsSmt.setInt(1, nodeID);

			ResultSet permissionsResults = permissionsSmt.executeQuery();

			if ( permissionsResults.first() ) {
				do
				{
					String className = permissionsResults.getString(1);
					String permissionName = permissionsResults.getString(2);
					String actions = permissionsResults.getString(3);

					Class clas;
					try
					{
						clas = this.getClass().forName( className );
					}
					catch (ClassNotFoundException e)
					{
						System.err.println("Permission class not found: " + className);
						continue; // XXX I guess this is OK?
					}

					// Perhaps more strict checking here?
					// TODO - is this check enough to be secure?
					if (!Permission.class.isAssignableFrom(clas))
					{
						System.err.println("Class " + className + " is not a Permission!");
						continue; // XXX
					}
					else if (clas.getClassLoader() instanceof DiscreteFilesClassLoader)
					{
						System.err.println("Class " + className + " is an insecure Permission!");
						continue;
					}

					Constructor con;
					try
					{
						con = clas.getDeclaredConstructor(String.class, String.class);
					}
					catch (NoSuchMethodException e)
					{
						System.err.println("Permission class had no valid constructor: " + className);
						continue; // XXX I guess this is OK?
					}

					Permission perm;
					try
					{
						perm = (Permission)con.newInstance(permissionName, actions);
					}
					catch (IllegalAccessException e)
					{
						System.err.println("Permission class constructor for " + className + " failed: " + e.getMessage());
						e.printStackTrace();
						continue; // XXX
					}
					catch (InstantiationException e)
					{
						System.err.println("Permission class constructor for " + className + " failed: " + e.getMessage());
						e.printStackTrace();
						continue; // XXX
					}
					catch (InvocationTargetException e)
					{
						System.err.println("Permission class constructor for " + className + " failed: " + e.getMessage());
						e.printStackTrace();
						continue; // XXX
					}

					System.out.println("Adding new permission for " + nodeID + ": " + perm);

					permissions.add(perm);
				} while ( permissionsResults.next() );
			}
		}
		catch ( SQLException e )
		{
			System.err.println("Could not load DB permissions for user node " + nodeID + " (probably now incomplete permissions): " + e.getMessage());
			e.printStackTrace();
		}
		finally
		{
			dbBroker.freeConnection(dbConnection);
		}

		System.out.println("All permissions for " + nodeID + " done.");
		synchronized(nodeMap) {
			nodeMap.put(nodeID, permissions);
		}
	}

	/**
	 * Check if the numbered user node has a permission
	 * @param permission
	 * @param userNode
	 */
	private boolean hasPerm(Permission permission, int userNode)
	{
		return hasPerm(permission, userNode, false);
	}

	private boolean hasPerm(Permission permission, int userNode, boolean includeThis)
	{
		Iterator<Integer> allNodes = getAllNodes(userNode, includeThis);

		if ( ! allNodes.hasNext() )
		{
			System.out.println("User node " + userNode + " has no subnodes!");
			return false;
		}

		int nodeID;
		while( allNodes.hasNext() )
		{
			nodeID = allNodes.next();
			PermissionCollection perms = getNodePermissions( nodeID );
			// Be careful to avoid invalid groups and stuff.
			if (perms != null && perms.implies(permission))
				return true;
		}

		return false;
	}

	/**
	 * Get all nodes linked to the passed node.
	 */
	private Iterator<Integer> getAllNodes(int nodeID)
	{
		return getAllNodes(nodeID, false);
	}

	/**
	 * Get all nodes linked to the passed node.
	 */
	private Iterator<Integer> getAllNodes(int nodeID, boolean addThis)
	{
		Connection dbConn = dbBroker.getConnection();
		List list = new LinkedList<Integer>();
		if (addThis)
			list.add(nodeID);
		try
		{
			getAllNodesRecursive(dbConn, list, nodeID, 0);
		}
		finally
		{
			dbBroker.freeConnection(dbConn);
		}
		return list.listIterator();
	}

	private void getAllNodesRecursive(Connection dbConn, List list, int nodeID, int recurseDepth)
	{
		if (recurseDepth >= 5)
		{
			System.err.println("Ack! Recursion depth succeeded when trying to process user node " + nodeID);
			return;
		}
		try
		{
			PreparedStatement stat = dbConn.prepareStatement("SELECT GroupID FROM GroupMembers WHERE MemberID = ?");
			stat.setInt(1, nodeID);

			ResultSet results = stat.executeQuery();

			if ( results.first() )
			{
				do
				{
					int newNode = results.getInt(1);
					if ( !list.contains( newNode ) )
					{
						list.add( newNode );
					}
					getAllNodesRecursive(dbConn, list, newNode, recurseDepth + 1);
				} while ( results.next() );
			}
		}
		catch (SQLException e)
		{
			System.err.println("Ack! SQL exception when fetching groups for node " + nodeID + ": " + e);
		}
	}

	/**
	 * Get the node ID that corresponds to a node
	 */
	private int getNodeIDFromNode(UserNode node)
	{
		return getNodeIDFromNodeName(node.getName(), node.getType());
	}

	/**
	 * Get the node ID that corresponds to a node name
	 */
	private int getNodeIDFromUserName(String userName)
	{
		return getNodeIDFromNodeName(userName, 0);
	}

	/**
	 * Get the node ID that corresponds to a node name
	 */
	private int getNodeIDFromNodeName(String nodeName, int nodeType)
	{
		Connection dbConn = dbBroker.getConnection();
		try
		{
			PreparedStatement stat = dbConn.prepareStatement("SELECT NodeID FROM UserNodes WHERE NodeName = ? && NodeClass = ?");
			stat.setString(1, nodeName);
			stat.setInt(2, nodeType);
			ResultSet results = stat.executeQuery();
			if ( results.first() )
			{
				return results.getInt(1);
			}
			System.err.println("Ack! Node name " + nodeName + "(" + nodeType + ") not found!");
		}
		catch (SQLException e)
		{
			System.err.println("Ack! SQL exception when getting node from node name " + nodeName + "(" + nodeType + "): " + e);
		}
		finally
		{
			dbBroker.freeConnection(dbConn);
		}
		return -1;
	}

	/**
	 * Get the node ID that corresponds to a node name
	 */
	private UserNode getNodeFromNodeID(int nodeID)
	{
		Connection dbConn = dbBroker.getConnection();
		try
		{
			PreparedStatement stat = dbConn.prepareStatement("SELECT NodeName, NodeClass FROM UserNodes WHERE NodeID = ?");
			stat.setInt(1, nodeID);
			ResultSet results = stat.executeQuery();
			if ( results.first() )
			{
				dbBroker.freeConnection(dbConn);
				return new UserNode(results.getString(1), results.getInt(2));
			}
			System.err.println("Ack! Node " + nodeID + ") not found!");
		}
		catch (SQLException e)
		{
			System.err.println("Ack! SQL exception when getting node from node ID " + nodeID + ": " + e);
		}
		finally
		{
			dbBroker.freeConnection(dbConn);
		}
		return null;
	}

	/**
	 * Get the last insert ID
	 */
	private int getLastInsertID(Connection dbConn) throws SQLException
	{
		PreparedStatement stat = dbConn.prepareStatement("SELECT LAST_INSERT_ID()");
		ResultSet results = stat.executeQuery();
		if ( results.first() )
			return results.getInt(1);
		throw new SQLException("Ack! LAST_INSERT_ID() returned no results!");
	}

	/**
	 * Get the node ID that corresponds to a plugin
	 */
	private int getNodeFromPluginName(String pluginName)
	{
		Connection dbConn = dbBroker.getConnection();
		try
		{
			PreparedStatement stat = dbConn.prepareStatement("SELECT UserID FROM UserPlugins WHERE PluginName = ?");
			stat.setString(1, pluginName);
			ResultSet results = stat.executeQuery();
			if ( results.first() )
			{
				return results.getInt(1);
			}
			System.err.println("Ack! Plugin name " + pluginName + " not found!");
		}
		catch (SQLException e)
		{
			System.err.println("Ack! SQL exception when getting node from plugin name " + pluginName + ": " + e);
		}
		finally
		{
			dbBroker.freeConnection(dbConn);
		}
		return -1;
	}

	/* ================================
	 * PLUGIN PERMISSION CHECK ROUTINES
	 * ================================
	 */

	/**
	 * Check if the given userName has permission.
	 * @param permission
	 * @param userName
	 */
	public boolean hasPerm(Permission permission, String userName)
	{
		int userNode = getNodeIDFromUserName(userName);

		System.out.println(userName + " " + userNode + ": " + permission);

		if (userNode == -1)
			return false;

		return hasPerm(permission, userNode);
	}

	/**
	 * Check if the previous plugin on the call stack has a permission
	 * @param permission
	 */
	public boolean hasPluginPerm(Permission permission)
	{
		return hasPluginPerm(permission, getPluginName(1));
	}

	/**
	 * Check if the previous plugin on the call stack has a permission
	 * @param permission
	 * @param pluginName
	 */
	public boolean hasPluginPerm(final Permission permission, final String pluginName)
	{
		System.out.println("Checking permission on plugin " + pluginName + ": " + permission);
		if (pluginName == null)
			return true; // XXX should this be true?

		// Should prevent circular checks...
		return ((Boolean)AccessController.doPrivileged(new PrivilegedAction() {
			public Object run() {
				int nodeID = getNodeFromPluginName( pluginName );

				// No such user!
				if (nodeID == -1)
					return false;

				// Now just check on this node!
				return hasPerm( permission, nodeID );
			}
		})).booleanValue();
	}

	/* ===============================
	 * PLUGIN USER MANAGEMENT ROUTINES
	 * ===============================
	 */

	private Object nodeDbLock;

	/**
	 * Convenience method
	 */
	private void dbCleanup(Connection dbConn) throws ChoobException
	{
		try
		{
			dbConn.rollback(); // If success, this does nothing
			dbConn.setAutoCommit(true);
			dbBroker.freeConnection(dbConn);
		}
		catch (SQLException e)
		{
			// XXX WTF to do here?
			sqlErr("dealing with dealing with SQL error", e);
		}
	}

	/**
	 * Convenience method
	 */
	private void sqlErr(String task, SQLException e) throws ChoobException
	{
		System.err.println("ACK! SQL error when " + task + ": " + e);
		throw new ChoobException("An SQL error occurred when " + task + ". Please ask the bot administrator to check the logs.");
	}

	/**
	 * Add a plugin->user binding to the database
	 */
	// TODO: Should this simply add plugin.pluginName to user.userName?
	public void bindPlugin(String pluginName, String userName) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("plugin.bind"));

		int userID = getNodeIDFromUserName(userName);
		if ( userID == -1 )
		{
			throw new ChoobException("User " + userName + " does not exist!");
		}

		Connection dbConn = dbBroker.getConnection();
		synchronized(nodeDbLock)
		{
			try
			{
				// Bind plugin
				PreparedStatement stat = dbConn.prepareStatement("REPLACE INTO UserPlugins (UserID, PluginName) VALUES (?, ?)");
				stat.setInt(1, userID);
				stat.setString(2, pluginName);
				if (stat.executeUpdate() == 0)
					System.err.println("Ack! No rows updated in plugin bind!");

				// Done!
				dbConn.commit();
			}
			catch (SQLException e)
			{
				sqlErr("binding plugin " + pluginName, e);
			}
			finally
			{
				dbCleanup(dbConn);
			}
		}
	}
	// Must check (system) user.add

	/**
	 * Add a user to the database
	 */
	public void addUser(String userName) throws ChoobException
	{
		AccessController.checkPermission(new ChoobPermission("user.add"));

		Connection dbConn = dbBroker.getConnection();
		synchronized(nodeDbLock)
		{
			try
			{
				dbConn.setAutoCommit(false);
				// First, make sure no user exists...
				PreparedStatement stat = dbConn.prepareStatement("SELECT NodeID FROM UserNodes WHERE NodeName = ? AND (NodeClass = 0 OR NodeClass = 1)");
				stat.setString(1, userName);
				ResultSet results = stat.executeQuery();
				if ( results.first() )
				{
					throw new ChoobException ("User " + userName + " already exists!");
				}

				// Add user and group
				stat = dbConn.prepareStatement("INSERT INTO UserNodes (NodeName, NodeClass) VALUES (?, ?)");
				stat.setString(1, userName);
				stat.setInt(2, 0);
				if (stat.executeUpdate() == 0)
					System.err.println("Ack! No rows updated in user insert!");
				int userID = getLastInsertID(dbConn);
				stat = dbConn.prepareStatement("INSERT INTO UserNodes (NodeName, NodeClass) VALUES (?, ?)");
				stat.setString(1, userName);
				stat.setInt(2, 1);
				if (stat.executeUpdate() == 0)
					System.err.println("Ack! No rows updated in user group insert!");
				int groupID = getLastInsertID(dbConn);

				// Now bind them.
				stat = dbConn.prepareStatement("INSERT INTO GroupMembers (GroupID, MemberID) VALUES (?, ?)");
				stat.setInt(1, groupID);
				stat.setInt(2, userID);
				if (stat.executeUpdate() == 0)
					System.err.println("Ack! No rows updated in user group member insert!");

				// Done!
				dbConn.commit();
			}
			catch (SQLException e)
			{
				sqlErr("adding user " + userName, e);
			}
			finally
			{
				dbCleanup(dbConn); // If success, this does nothing
			}
		}
	}
	// Must check (system) user.add

	/**
	 * Add a user to the database
	 */
	public void addGroup(String groupName) throws ChoobException
	{
		UserNode group = new UserNode(groupName);

		if (group.getType() == 2) // plugins can poke their own groups!
		{
			String pluginName = getPluginName(0);
			if (!(group.getRootName().compareToIgnoreCase(pluginName)==0))
				AccessController.checkPermission(new ChoobPermission("group.add."+groupName));
		}
		else
		{
			AccessController.checkPermission(new ChoobPermission("group.add."+groupName));
		}

		// OK, we're allowed to add.
		Connection dbConn = dbBroker.getConnection();
		synchronized(nodeDbLock)
		{
			try
			{
				dbConn.setAutoCommit(false);
				PreparedStatement stat = dbConn.prepareStatement("SELECT NodeID FROM UserNodes WHERE NodeName = ? AND NodeClass = ?");
				stat.setString(1, group.getName());
				stat.setInt(2, group.getType());
				ResultSet results = stat.executeQuery();
				if ( results.first() )
				{
					throw new ChoobException("Group " + groupName + " already exists!");
				}

				stat = dbConn.prepareStatement("INSERT INTO UserNodes (NodeName, NodeClass) VALUES (?, ?)");
				stat.setString(1, group.getName());
				stat.setInt(2, group.getType());
				if (stat.executeUpdate() == 0)
					System.err.println("Ack! No rows updated in group " + groupName + " insert!");

				dbConn.commit();
			}
			catch (SQLException e)
			{
				sqlErr("adding group " + groupName, e);
			}
			finally {
				dbCleanup(dbConn); // If success, this does nothing
			}
		}
	}

	public void addUserToGroup(String parentName, String childName) throws ChoobException
	{
		UserNode parent = new UserNode(parentName);
		UserNode child = new UserNode(childName, true);
		addNodeToNode(parent, child);
	}

	public void addGroupToGroup(String parentName, String childName) throws ChoobException
	{
		UserNode parent = new UserNode(parentName);
		UserNode child = new UserNode(childName);
		addNodeToNode(parent, child);
	}

	public void addNodeToNode(UserNode parent, UserNode child) throws ChoobException
	{
		if (parent.getType() == 2) // plugins can poke their own groups!
		{
			String pluginName = getPluginName(0);
			if (!(parent.getRootName().compareToIgnoreCase(pluginName)==0))
				AccessController.checkPermission(new ChoobPermission("group.members."+parent));
		}
		else
		{
			AccessController.checkPermission(new ChoobPermission("group.members."+parent));
		}

		// OK, we're allowed to add.
		int parentID = getNodeIDFromNode(parent);
		int childID = getNodeIDFromNode(child);
		if (parentID == -1)
			throw new ChoobException("Group " + parent + " does not exist!");
		if (childID == -1)
			throw new ChoobException("Group " + child + " does not exist!");

		Connection dbConn = dbBroker.getConnection();
		synchronized(nodeDbLock)
		{
			try
			{
				dbConn.setAutoCommit(false);
				PreparedStatement stat = dbConn.prepareStatement("SELECT MemberID FROM GroupMembers WHERE GroupID = ? AND MemberID = ?");
				stat.setInt(1, parentID);
				stat.setInt(2, childID);
				ResultSet results = stat.executeQuery();
				if ( results.first() )
				{
					throw new ChoobException("Group " + parent + " already had member " + child);
				}

				stat = dbConn.prepareStatement("INSERT INTO GroupMembers (GroupID, MemberID) VALUES (?, ?)");
				stat.setInt(1, parentID);
				stat.setInt(2, childID);
				if ( stat.executeUpdate() == 0 )
					System.err.println("Ack! Group member add did nothing: " + parent + ", member " + child);

				dbConn.commit();
			}
			catch (SQLException e)
			{
				sqlErr("adding " + child + " to group " + parent, e);
			}
			finally
			{
				dbCleanup(dbConn); // If success, this does nothing
			}
		}
	}

	public void removeUserFromGroup(String parentName, String childName) throws ChoobException
	{
		UserNode parent = new UserNode(parentName);
		UserNode child = new UserNode(childName, true);
		removeNodeFromNode(parent, child);
	}

	public void removeGroupFromGroup(String parentName, String childName) throws ChoobException
	{
		UserNode parent = new UserNode(parentName);
		UserNode child = new UserNode(childName);
		removeNodeFromNode(parent, child);
	}

	public void removeNodeFromNode(UserNode parent, UserNode child) throws ChoobException
	{
		if (parent.getType() == 2) // plugins can poke their own groups!
		{
			String pluginName = getPluginName(0);
			if (!(parent.getRootName().compareToIgnoreCase(pluginName)==0))
				AccessController.checkPermission(new ChoobPermission("group.members."+parent));
		}
		else
		{
			AccessController.checkPermission(new ChoobPermission("group.members."+parent));
		}

		// OK, we're allowed to add.
		int parentID = getNodeIDFromNode(parent);
		int childID = getNodeIDFromNode(child);
		if (parentID == -1)
			throw new ChoobException("Group " + parent + " does not exist!");
		if (childID == -1)
			throw new ChoobException("Group " + child + " does not exist!");

		Connection dbConn = dbBroker.getConnection();
		synchronized(nodeDbLock)
		{
			try
			{
				PreparedStatement stat = dbConn.prepareStatement("SELECT MemberID FROM GroupMembers WHERE GroupID = ? AND MemberID = ?");
				stat.setInt(1, parentID);
				stat.setInt(2, childID);
				ResultSet results = stat.executeQuery();
				if ( ! results.first() )
				{
					throw new ChoobException("Group " + parent + " did not have member " + child);
				}

				stat = dbConn.prepareStatement("DELETE FROM GroupMembers WHERE GroupID = ? AND  MemberID = ?");
				stat.setInt(1, parentID);
				stat.setInt(2, childID);
				if ( stat.executeUpdate() == 0 )
					System.err.println("Ack! Group member remove did nothing: " + parent + ", member " + child);

				dbConn.commit();
			}
			catch (SQLException e)
			{
				sqlErr("removing " + child + " from " + parent, e);
			}
			finally
			{
				dbCleanup(dbConn); // If success, this does nothing
			}
		}
	}

	public void grantPermission(String groupName, Permission permission) throws ChoobException
	{
		UserNode group = new UserNode(groupName);
		if (group.getType() == 2) // plugins can add their own permissions (kinda)
		{
			String pluginName = getPluginName(0);
			if (!(group.getRootName().compareToIgnoreCase(pluginName)==0))
				AccessController.checkPermission(new ChoobPermission("group.grant."+group));
			// OK, that's all fine, BUT:
			if (!hasPluginPerm(permission))
			{
				System.err.println("Plugin " + pluginName + " tried to grant permission " + permission + " it didn't have!");
				throw new ChoobException("A plugin may only grant permssions which it is entitled to.");
			}
		}
		else
		{
			AccessController.checkPermission(new ChoobPermission("group.grant."+group));
		}


		// OK, we're allowed to add.
		int groupID = getNodeIDFromNode(group);
		if (groupID == -1)
			throw new ChoobException("Group " + group + " does not exist!");

		if (hasPerm(permission, groupID, true))
			throw new ChoobException("Group " + group + " already has permission " + permission + "!");

		Connection dbConn = dbBroker.getConnection();
		synchronized(nodeDbLock)
		{
			try
			{
				PreparedStatement stat = dbConn.prepareStatement("INSERT INTO UserNodePermissions (NodeID, Type, Permission, Action) VALUES (?, ?, ?, ?)");
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
					System.err.println("Ack! Permission add did nothing: " + group + " " + permission);

				dbConn.commit();

				invalidateNodePermissions(groupID);
			}
			catch (SQLException e)
			{
				sqlErr("adding permission " + permission + " to group " + group, e);
			}
			finally
			{
				dbCleanup(dbConn); // If success, this does nothing
			}
		}
	}

	/**
	 * Attempt to work out from whence a group's permissions come.
	 */
	public String[] findPermission(String groupName, Permission permission) throws ChoobException
	{
		UserNode group = new UserNode(groupName);
		int groupID = getNodeIDFromNode(group);
		if (groupID == -1)
			throw new ChoobException("Group " + group + " does not exist!");

		List<String> foundPerms = new LinkedList();

		Iterator<Integer> allNodes = getAllNodes(groupID, true);

		if ( ! allNodes.hasNext() )
		{
			return new String[0];
		}

		int nodeID;
		while( allNodes.hasNext() )
		{
			nodeID = allNodes.next();
			PermissionCollection perms = getNodePermissions( nodeID );
			// Be careful to avoid invalid groups and stuff.
			if (perms != null && perms.implies(permission))
			{
				// Which element?
				Enumeration<Permission> allPerms = perms.elements();
				while( allPerms.hasMoreElements() )
				{
					Permission perm = allPerms.nextElement();
					if (perm.implies(permission))
					{
						foundPerms.add(getNodeFromNodeID(nodeID).toString() + perm);
					}
				}
			}
		}
		String[] retVal = new String[foundPerms.size()];
		return (String[])foundPerms.toArray(retVal);
	}

	/**
	 * Attempt to work out from whence a group's permissions come.
	 */
	public String[] getPermissions(String groupName) throws ChoobException
	{
		UserNode group = new UserNode(groupName);
		int groupID = getNodeIDFromNode(group);
		if (groupID == -1)
			throw new ChoobException("Group " + group + " does not exist!");

		List<String> foundPerms = new LinkedList();

		PermissionCollection perms = getNodePermissions( groupID );
		// Be careful to avoid invalid groups and stuff.
		if (perms != null)
		{
			// Which element?
			Enumeration<Permission> allPerms = perms.elements();
			while( allPerms.hasMoreElements() )
			{
				Permission perm = allPerms.nextElement();
				foundPerms.add(perm.toString());
			}
		}
		String[] retVal = new String[foundPerms.size()];
		return (String[])foundPerms.toArray(retVal);
	}

	public void revokePermission(String groupName, Permission permission) throws ChoobException
	{
		UserNode group = new UserNode(groupName);
		if (group.getType() == 2) // plugins can revoke their own permissions
		{
			String pluginName = getPluginName(0);
			if (!(group.getRootName().compareToIgnoreCase(pluginName)==0))
				AccessController.checkPermission(new ChoobPermission("group.revoke."+group));
		}
		else
		{
			AccessController.checkPermission(new ChoobPermission("group.revoke."+group));
		}


		// OK, we're allowed to add.
		int groupID = getNodeIDFromNode(group);
		if (groupID == -1)
			throw new ChoobException("Group " + group + " does not exist!");

		if (!hasPerm(permission, groupID, true))
			throw new ChoobException("Group " + group + " does not have permission " + permission + "!");

		Connection dbConn = dbBroker.getConnection();
		synchronized(nodeDbLock)
		{
			try
			{
				PreparedStatement stat;
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

				dbConn.commit();

				invalidateNodePermissions(groupID);
			}
			catch (SQLException e)
			{
				sqlErr("revoking permission " + permission + " from group " + group, e);
			}
			finally
			{
				dbCleanup(dbConn); // If success, this does nothing
			}
		}
	}
}
