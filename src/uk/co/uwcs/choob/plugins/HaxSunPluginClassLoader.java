package uk.co.uwcs.choob.plugins;

import java.io.*;
import java.net.*;
import java.security.*;

public final class HaxSunPluginClassLoader extends ClassLoader {
	private String path;

	private ProtectionDomain domain;

	public HaxSunPluginClassLoader(String pluginName, String path,
			ProtectionDomain domain) {
		super();
		this.path = path;
		this.domain = domain;
		// super.definePackage("plugins", "", "", "", "", "", "", null);
		definePackage("plugins." + pluginName, "", "", "", "", "", "", null);
	}

	public Class<?> findClass(final String name) throws ClassNotFoundException {
		try {
			return AccessController
					.doPrivileged(new PrivilegedExceptionAction<Class<?>>() {
						public Class<?> run() throws ClassNotFoundException {
							try {
								String fileName = path
										+ name.replace('.', File.separatorChar)
										+ ".class";
								File classFile = new File(fileName);
								if (!classFile.isFile()) {
									throw new ClassNotFoundException(
											"Class file " + fileName
													+ " is not a file.");
								}

								URL classURL = classFile.toURI().toURL();
								URLConnection classConn = classURL
										.openConnection();
								int size = classConn.getContentLength();
								if (size == -1) {
									// XXX
									throw new ClassNotFoundException(
											"Class "
													+ name
													+ " has unknown content length; not loaded.");
								}

								InputStream classStream = new FileInputStream(
										classFile);
								byte[] classData = new byte[size];
								int read = 0;
								int avail;
								while ((avail = classStream.available()) > 0) {
									avail = classStream.read(classData, read,
											avail);
									read += avail;
								}
								if (read != size)
									throw new ClassNotFoundException(
											"Class "
													+ name
													+ " has was not fully read; not loaded.");

								Class<?> theClass = defineClass(name, classData,
										0, classData.length, domain);
								return theClass;
							} catch (IOException e) {
								System.err.println("Could not open class URL: "
										+ e);
								throw new ClassNotFoundException("Class "
										+ name + " not found!", e);
							} catch (ClassFormatError e) {
								System.err.println("Could not open class URL: "
										+ e);
								throw new ClassNotFoundException("Class "
										+ name + " not valid.", e);
							} catch (NoClassDefFoundError e) {
								System.err.println("n class URL: " + e);
								throw new ClassNotFoundException("Class "
										+ name + " contained no class.", e);
							}
						}
					});
		} catch (PrivilegedActionException e) {
			throw (ClassNotFoundException) e.getException();
		}
	}
}
