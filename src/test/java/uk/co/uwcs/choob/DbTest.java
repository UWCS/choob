package uk.co.uwcs.choob;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DbTest extends AbstractPluginTest {

	@Test
	public void testDb() {
		final PersistedObj obj = new PersistedObj();
		obj.name = "john";
		b.getMods().odb.save(obj);
		assertEquals("john", b.getMods().odb.retrieve(PersistedObj.class, "WHERE id=" + obj.id).get(0).name);
	}
}
