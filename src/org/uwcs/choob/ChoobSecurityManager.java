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

/**
 * Security manager for the bot, controls access to anything requiring/checking
 * for a permission.
 * @author sadiq
 */
public class ChoobSecurityManager extends SecurityManager {
    DbConnectionBroker dbBroker;

    /**
     * Creates a new instance of ChoobSecurityManager
     * @param dbBroker Database connection pool/broker.
     */
    public ChoobSecurityManager(DbConnectionBroker dbBroker) {
        this.dbBroker = dbBroker;
    }

    public PermissionCollection getPluginPermissions(String plugin) {
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

                    System.out.println("Adding new permission " + className + " of name \""
                            + permissionName + "\" and actions \"" + actions + "\" --> " + perm);

                    permissions.add(perm);
                } while ( permissionsResults.next() );
            }
            dbBroker.freeConnection(dbConnection);
            return permissions;
        }
        catch ( SQLException e )
        {
            dbBroker.freeConnection(dbConnection);
            System.out.println("Could not load DB permissions for " + plugin + " (assuming none): " + e.getMessage());
            e.printStackTrace();
            return permissions;
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

        int c;

        for( c = 0; c < callStackClasses.length; c++ ) {
            if( c != 0 && callStackClasses[c] == ChoobSecurityManager.class ) return;
            // The above is there to stop circular security checks. Oh the agony.

            ClassLoader tempClassLoader = callStackClasses[c].getClassLoader();

            if( tempClassLoader != null && tempClassLoader.getClass() == DiscreteFilesClassLoader.class ) {
                //                System.out.println("Priviledged call from plugin " + callStackClasses[c] + ". Permission type " + permission.getClass().getName() + " needed: " + permission.getName() + " (Action: '" + permission.getActions() + "')");

                if( permission.getName().compareTo("accessDeclaredMembers") == 0 ) return;

                if( permission.getName().compareTo("suppressAccessChecks") == 0 ) return;

                PermissionCollection perms = getPluginPermissions(callStackClasses[c].getName());

                if (perms.implies(permission))
                    return;

                System.out.println("Access denied for plugin " + callStackClasses[c] + " on permission (" + permission.toString() + ")\n");
                System.out.flush();
                throw new SecurityException("Access denied for plugin " + callStackClasses[c] + " on permission (" + permission.toString() + ")");

            }
        }
    }

}
