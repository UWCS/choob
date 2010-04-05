package uk.co.uwcs.choob.plugins;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;

public final class HaxSunPluginClassLoader extends ClassLoader
{
	private final String path;
	private final ProtectionDomain domain;

	public HaxSunPluginClassLoader( final String pluginName, final String path, final ProtectionDomain domain )
	{
		super();
		this.path = path;
		this.domain = domain;
//		super.definePackage("plugins", "", "", "", "", "", "", null);
		try
		{
			definePackage("plugins." + pluginName, "", "", "", "", "", "", null);
		}
		catch (IllegalArgumentException e)
		{
			e.printStackTrace();
			System.err.println();
			System.err.println("Couldn't create plugins package.  " +
					"This is probably because your debugger is interfering.  Your plugin probably hasn't been reloaded.");
			System.err.println();
		}
	}

	@Override
	public Class<?> findClass(final String name) throws ClassNotFoundException {
		try {
			return AccessController
					.doPrivileged(new PrivilegedExceptionAction<Class<?>>() {
						@Override public Class<?> run() throws ClassNotFoundException {
							try {
								final String fileName = path
										+ name.replace('.', File.separatorChar)
										+ ".class";
								final File classFile = new File(fileName);
								if (!classFile.isFile())
								{
									throw new ClassNotFoundException("Class file " + fileName + " is not a file.");
								}

								final URL classURL = classFile.toURI().toURL();
								final URLConnection classConn = classURL.openConnection();
								final int size = classConn.getContentLength();
								if (size == -1)
								{
									// XXX
									throw new ClassNotFoundException("Class " + name + " has unknown content length; not loaded.");
								}

								final InputStream classStream = new FileInputStream(classFile);
								final byte[] classData = new byte[size];
								int read = 0;
								int avail;
								while((avail = classStream.available()) > 0)
								{
									avail = classStream.read(classData, read, avail);
									read += avail;
								}
								if (read != size)
									throw new ClassNotFoundException("Class " + name + " has was not fully read; not loaded.");

								final Class<?> theClass = defineClass(name, classData,
										0, classData.length, domain);
								return theClass;
							}
							catch (final IOException e)
							{
								System.err.println("Could not open class URL: " + e);
								throw new ClassNotFoundException("Class " + name + " not found!", e);
							}
							catch (final ClassFormatError e)
							{
								System.err.println("Could not open class URL: " + e);
								throw new ClassNotFoundException("Class " + name + " not valid.", e);
							}
							catch (final NoClassDefFoundError e)
							{
								System.err.println("n class URL: " + e);
								throw new ClassNotFoundException("Class " + name + " contained no class.", e);
							}
						}
				}
			);
		} catch (final PrivilegedActionException e) {
			throw (ClassNotFoundException)e.getException();
		}
	}
}
