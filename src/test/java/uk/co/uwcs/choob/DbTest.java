package uk.co.uwcs.choob;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import uk.co.uwcs.choob.modules.ObjectDbModule;

public class DbTest extends AbstractPluginTest {
	ObjectDbModule odb;

	@Before
	public void setUp() {
		odb = b.getMods().odb;
	}

	@Test
	public void testDb() {
		final PersistedObj obj = john();
		assertEquals("john", odb.retrieve(PersistedObj.class, "WHERE id=" + obj.id).get(0).name);
	}

	@Test
	public void testDelete() {
		final PersistedObj obj = john();
		john();
		assertEquals(2, odb.retrieve(PersistedObj.class, null).size());
		odb.delete(obj);
		assertEquals(1, odb.retrieve(PersistedObj.class, null).size());
	}

	private PersistedObj john() {
		final PersistedObj obj = new PersistedObj();
		obj.name = "john";
		odb.save(obj);
		return obj;
	}
}
