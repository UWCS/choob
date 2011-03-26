package uk.co.uwcs.choob.modules;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

class ChoobURLStreamHandler extends URLStreamHandler {
	@Override
	protected URLConnection openConnection(URL u) throws IOException {
		final String path = u.getPath();

		{
			final URL resource = ChoobURLStreamHandler.class.getResource(path);
			if (null != resource)
				return resource.openConnection();
		}

		for (File sourcePath : new File[] {
				new File("main/plugins-alpha"),
				new File("main/plugins"),
		}) {
			final File f = new File(sourcePath, u.getPath());
			if (f.isFile())
				return new URL("file:///" + f.getAbsolutePath()).openConnection();
		}

		throw new FileNotFoundException("Couldn't resolve '" + path + "' from the classpath or source directories");
	}
}
