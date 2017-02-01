package uk.co.uwcs.choob;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

public class IntegrationTest {
	static LocalMySQL db;

	@Test
	public void test() throws IOException {
		final File conf = new File(db.base, "conf");
		conf.createNewFile();
		try (final PrintWriter pw = new PrintWriter(conf)) {
			pw.println("dbServer=localhost:" + db.port);
			pw.println("dbPass=choob");
		}

		new Choob(conf.getAbsolutePath());

	}

	@BeforeClass
	public static void before() throws IOException, InterruptedException, SQLException {
		db = new LocalMySQL("/template/minimal.sql");
	}

	@AfterClass
	public static void after() throws IOException {
		db.close();
	}
}
