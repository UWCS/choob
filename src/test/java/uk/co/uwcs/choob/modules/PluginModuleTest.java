package uk.co.uwcs.choob.modules;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static uk.co.uwcs.choob.modules.PluginModule.createUrl;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URLConnection;

import org.junit.Test;

import uk.co.uwcs.choob.support.ChoobException;

import com.google.common.io.ByteStreams;

public class PluginModuleTest {

	@Test
	public void testChoobReal() throws MalformedURLException, IOException, ChoobException {
		assertConnection(createUrl("choob-plugin:/Admin.java").openConnection());
	}

	@Test(expected = FileNotFoundException.class)
	public void testChoobNotFound() throws MalformedURLException, IOException, ChoobException {
		assertConnection(createUrl("choob-plugin:/ThisFileDoesntExist.java").openConnection());
	}

	@Test
	public void testFakedResource() throws MalformedURLException, IOException, ChoobException {
		final String url = "choob-plugin:/" + PluginModule.class.getName().replaceAll("\\.", "/") + ".class";
		assertConnection(createUrl(url).openConnection());
	}

	public void testProtocolOf() throws MalformedURLException {
		assertEquals("pony", PluginModule.protocolOf("pony:badger"));
		assertEquals("pony", PluginModule.protocolOf("pony://badger"));
		assertEquals("pon-y", PluginModule.protocolOf("pon-y:badger:23::"));
	}

	private static void assertConnection(URLConnection conn) throws IOException {
		assertNotNull(conn);
		conn.connect();
		final InputStream is = conn.getInputStream();
		try {
			ByteStreams.toByteArray(is);
		} finally {
			is.close();
		}
	}
}
