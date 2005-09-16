package org.uwcs.choob.plugins;

import java.io.*;
import java.net.*;
import java.security.*;

public class HaxSunPluginClassLoader extends ClassLoader
{
	private String pluginName;
	private String path;
	private ProtectionDomain domain;
	private Package pack;

	public HaxSunPluginClassLoader( String pluginName, String path, ProtectionDomain domain )
	{
		super();
		this.pluginName = pluginName;
		this.path = path;
		this.domain = domain;
//		super.definePackage("plugins", "", "", "", "", "", "", null);
		pack = definePackage("plugins." + pluginName, "", "", "", "", "", "", null);
		System.out.println("Constructed");
	}

	protected Package getPackage(String name)
	{
		System.out.println("Asked for package: " + name);
		System.out.println("super gives: " + super.getPackage(name));
		if (name.equals("plugins." + pluginName))
			return pack;
		System.out.println("Delegating to super.");
		return super.getPackage(name);
	}

	protected Package definePackage(String name, String specTitle, String specVersion, String specVendor, String implTitle, String implVersion, String implVendor, URL sealBase)
	{
		System.out.println("definePackage(" +name+", "+specTitle+", "+specVersion+", "+specVendor+", "+implTitle+", "+implVersion+", "+implVendor+", "+sealBase);
		return super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase);
	}

	public Class findClass(final String name) throws ClassNotFoundException
	{
		try
		{
			return (Class)AccessController.doPrivileged(new PrivilegedExceptionAction()
				{
					public Object run() throws ClassNotFoundException
						{
							try
							{
								String fileName = path + name.replace('.', File.separatorChar) + ".class";
								System.out.println("Attempting to load class from file " + fileName);
								File classFile = new File(fileName);
								if (!classFile.isFile())
								{
									throw new ClassNotFoundException("Class file " + fileName + " is not a file.");
								}

								URL classURL = classFile.toURI().toURL();
								URLConnection classConn = classURL.openConnection();
								int size = classConn.getContentLength();
								if (size == -1)
								{
									// XXX
									throw new ClassNotFoundException("Class " + name + " has unknown content length; not loaded.");
								}

								InputStream classStream = new FileInputStream(classFile);
								byte[] classData = new byte[size];
								int read = 0;
								int avail;
								while((avail = classStream.available()) > 0)
								{
									avail = classStream.read(classData, read, avail);
									read += avail;
								}
								if (read != size)
									throw new ClassNotFoundException("Class " + name + " has was not fully read; not loaded.");

								System.out.println("Defining class now...");
								Class theClass = defineClass(name, classData, 0, classData.length, domain);
								System.out.println("Defined. Package is: " + theClass.getPackage());
								return theClass;
							}
							catch (IOException e)
							{
								System.err.println("Could not open class URL: " + e);
								throw new ClassNotFoundException("Class " + name + " not found!", e);
							}
							catch (ClassFormatError e)
							{
								System.err.println("Could not open class URL: " + e);
								throw new ClassNotFoundException("Class " + name + " not valid.", e);
							}
							catch (NoClassDefFoundError e)
							{
								System.err.println("n class URL: " + e);
								throw new ClassNotFoundException("Class " + name + " contained no class.", e);
							}
						}
				}
			);
		} catch (PrivilegedActionException e) {
			throw (ClassNotFoundException)e.getException();
		}
	}
}
