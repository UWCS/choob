package uk.co.uwcs.choob;

import org.junit.Before;
import org.junit.Test;

import uk.co.uwcs.choob.support.ChoobException;

public class AliasTest extends AbstractPluginTest {

	@Before
	public void loadPlugin() throws ChoobException {
		b.addPlugin("Talk");
		b.addPlugin("Alias");
	}

	@Test
	public void testSay() {
		b.spinChannelMessage("~alias.alias say talk.say");
		assertGetsResposne("#chan hi", "~say hi");
	}

	@Test
	public void testDb() {
		final PersistedObj obj = new PersistedObj();
		obj.name = "john";
		b.getMods().odb.save(obj);
		System.out.println(obj.id);
	}
}
