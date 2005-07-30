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

    /**
     * Checks for whether the call to this class came from a scripted Beanshell class
     * (so, a plugin) and then checks to see whether that plugin has permission via
     * a database select.
     * @param permission
     */
    public void checkPermission(java.security.Permission permission) {
        Class[] callStackClasses = getClassContext();

        int c;

        for( c = 0; c < callStackClasses.length; c++ ) {
            if( c != 0 && callStackClasses[c] == ChoobSecurityManager.class ) return;
            // The above is there to stop circular security checks. Oh the agony.

            ClassLoader tempClassLoader = callStackClasses[c].getClassLoader();

            if( tempClassLoader != null && tempClassLoader.getClass() == DiscreteFilesClassLoader.class ) {
                Connection dbConnection = dbBroker.getConnection();
                System.out.println("Priviledged call from plugin " + callStackClasses[c] + ". Permission type " + permission.getClass().getName() + " needed: " + permission.getName() + " (Action: '" + permission.getActions() + "')");

                try {
                    PreparedStatement permissionsSmt = dbConnection.prepareStatement("SELECT Permission, Action, Type FROM UserPlugins, UserPluginPermissions WHERE UserPlugins.UserID = UserPluginPermissions.UserID AND UserPlugins.PluginName = ?");

                    permissionsSmt.setString(1,callStackClasses[c].getName());

                    ResultSet permissionsResults = permissionsSmt.executeQuery();

                    if( permission.getName().compareTo("accessDeclaredMembers") == 0 ) return;
                    if( permission.getName().compareTo("suppressAccessChecks") == 0 ) return;

                    if( permissionsResults.first() ) {
                        do {
                            // The following three cases are hacks, till I can find a better way
                            // to do permissions. Maybe some form of system that'll list all the permissions
                            // one would need for an operation?
                            if( permissionsResults.getString("Type").compareTo("*") == 0 ) return;

                            if(( permissionsResults.getString("Type").compareTo(permission.getClass().getName()) == 0 )
                            && (permissionsResults.getString("Permission").compareTo("*") == 0)) {
                                return;
                            }

                            if( ( permissionsResults.getString("Permission").compareTo(permission.getName()) == 0 )
                            && ( permissionsResults.getString("Action").compareTo(permission.getActions()) == 0 )
                            && ( permissionsResults.getString("Type").compareTo(permission.getClass().getName()) == 0 )) {
                                return;
                            }
                        }
                        while( permissionsResults.next() );
                    }

                    System.out.println("Access denied for plugin " + callStackClasses[c] + " on permission (" + permission.getClass().toString() + "," + permission.getName() + "," + permission.getActions() + ")\n");
                    System.out.flush();
                    throw new SecurityException("Access denied for plugin " + callStackClasses[c] + " on permission (" + permission.getClass().toString() + "," + permission.getName() + "," + permission.getActions() + ")");

                }
                catch( SQLException e ) {
                    throw new SecurityException("Could not resolve permission from database: " + e);
                }
                finally {
                    dbBroker.freeConnection(dbConnection);
                }
            }
        }
    }

}
