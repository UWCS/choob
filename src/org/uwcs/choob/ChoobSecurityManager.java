/*
 * ChoobSecurityManager.java
 *
 * Created on June 25, 2005, 3:28 PM
 */

package org.uwcs.choob;

import bsh.classpath.*;
import java.lang.*;
import org.uwcs.choob.support.*;
import java.sql.*;
import java.security.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Security manager for the bot, controls access to anything requiring/checking
 * for a permission.
 * @author sadiq
 */
public class ChoobSecurityManager extends SecurityManager
{
	DbConnectionBroker dbBroker;
	Map pluginMap;

	/**
	 * Creates a new instance of ChoobSecurityManager
	 * @param dbBroker Database connection pool/broker.
	 */
	public ChoobSecurityManager(DbConnectionBroker dbBroker)
	{
		this.dbBroker = dbBroker;
		this.pluginMap = new HashMap();
	}

	/**
	 * Force plugin permissions to be reloaded at some later point.
	 */
	public void invalidatePluginPermissions(String pluginName)
	{
		synchronized(pluginMap) {
			pluginMap.remove(pluginName);
		}
	}

	private PermissionCollection getPluginPermissions(String pluginName)
	{
		PermissionCollection perms;
		synchronized(pluginMap) {
			perms = (PermissionCollection)pluginMap.get(pluginName);
		}
		if (perms == null)
			updatePluginPermissions(pluginName);
		synchronized(pluginMap) {
			perms = (PermissionCollection)pluginMap.get(pluginName);
		}
		return perms;
	}
	
	private void updatePluginPermissions(String plugin) {
		System.out.println("Loading permissions for " + plugin + ".");
		Connection dbConnection = dbBroker.getConnection();

		Permissions permissions = new Permissions();

		try {
			PreparedStatement permissionsSmt = dbConnection.prepareStatement("SELECT Type, Permission, Action FROM UserPlugins, UserPluginPermissions WHERE UserPlugins.UserID = UserPluginPermissions.UserID AND UserPlugins.PluginName = ?");

			permissionsSmt.setString(1, plugin);

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
						clas = this.getClass().getClassLoader().loadClass( className );
					}
					catch (ClassNotFoundException e)
					{
						System.out.println("Permission class not found: " + className);
						continue; // XXX I guess this is OK?
					}

					if (!Permission.class.isAssignableFrom(clas))
					{
						System.out.println("Class " + className + " is not a Permission!");
						continue; // XXX
					}

					Constructor con;
					try
					{
						con = clas.getDeclaredConstructor(String.class, String.class);
					}
					catch (NoSuchMethodException e)
					{
						System.out.println("Permission class had no valid constructor: " + className);
						continue; // XXX I guess this is OK?
					}

					Permission perm;
					try
					{
						perm = (Permission)con.newInstance(permissionName, actions);
					}
					catch (IllegalAccessException e)
					{
						System.out.println("Permission class constructor for " + className + " failed: " + e.getMessage());
						e.printStackTrace();
						continue; // XXX
					}
					catch (InstantiationException e)
					{
						System.out.println("Permission class constructor for " + className + " failed: " + e.getMessage());
						e.printStackTrace();
						continue; // XXX
					}
					catch (InvocationTargetException e)
					{
						System.out.println("Permission class constructor for " + className + " failed: " + e.getMessage());
						e.printStackTrace();
						continue; // XXX
					}

					System.out.println("Adding new permission for " + plugin + ": " + perm);

					permissions.add(perm);
				} while ( permissionsResults.next() );
			}
		}
		catch ( SQLException e )
		{
			System.out.println("Could not load DB permissions for " + plugin + " (assuming none): " + e.getMessage());
			e.printStackTrace();
		}
		dbBroker.freeConnection(dbConnection);

		System.out.println("All permissions for " + plugin + " done.");
		synchronized(pluginMap) {
			pluginMap.put(plugin, permissions);
		}
	}


	/**
	 * Checks for whether the call to this class came from a scripted Beanshell class
	 * (so, a plugin) and then checks to see whether that plugin has permission via
	 * a database select.
	 * @param permission
	 */
	public void checkPermission(Permission permission) {
		Class[] callStackClasses = getClassContext();

		int c = 0;

		/*
		 * Attempted fix for Beanshell sillyness:
		 * It seems any method invocation on Beanshell's side passes through a
		 * BSHMethod. Hence we just check for its presence, prior to the
		 * plugin, in the call stack. Then we can deny access to
		 * suppressAccessChecks. Yay.
		 */
		boolean isMethod = false;
		for( c = 0; c < callStackClasses.length; c++ ) {

			if ( callStackClasses[c].equals(bsh.BshMethod.class) ) {
				//System.out.println("Is a Beanshell interpreted method!");
				isMethod = true;
			}

			if( c != 0 && callStackClasses[c] == ChoobSecurityManager.class ) return;
			// The above is there to stop circular security checks. Oh the agony.

			ClassLoader tempClassLoader = callStackClasses[c].getClassLoader();

			if( tempClassLoader != null && tempClassLoader.getClass() == DiscreteFilesClassLoader.class ) {
				//System.out.println("Priviledged call from plugin " + callStackClasses[c] + ". Permission type: " + permission);

				// If it's not a method, it's a part of Beanshell. It can do
				// what it wants.
				if ( !isMethod )
					return;
//					if( permission.getName().compareTo("suppressAccessChecks") == 0 ) return;

				if( permission.getName().compareTo("accessDeclaredMembers") == 0 ) return;

				PermissionCollection perms = getPluginPermissions(callStackClasses[c].getName());

				if (perms != null && perms.implies(permission))
					return;

				System.out.println("Access denied for plugin " + callStackClasses[c] + " on permission (" + permission.toString() + ")\n");
				if (!(permission instanceof java.io.FilePermission)) // Lots of these!
					for(int d=0; d<c; d++)
						System.out.println("Call Stack: " + callStackClasses[d]);
				System.out.flush();
				throw new SecurityException("Access denied for plugin " + callStackClasses[c] + " on permission (" + permission.toString() + ")");

			}
		}
	}

	// With bsh.Capabilities.haveAccessibility(), and suppressAccessChecks
	// defaulting to allowed, this method can be called from a plugin.
	// I'd argue that this is kinda bad. :)
	//   -- bucko
	private static void test() {
		System.out.println("This is a security test!");
	}

}
