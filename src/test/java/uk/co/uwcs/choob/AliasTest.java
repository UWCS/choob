package uk.co.uwcs.choob;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import uk.co.uwcs.choob.support.ChoobException;

public class AliasTest extends AbstractPluginTest {

	@Before
	public void loadPlugin() throws ChoobException {
		b.addPlugin("Talk");
		b.addPlugin("Alias");
	}

	@Ignore
	@Test
	public void testSay() {
		b.spinChannelMessage("~alias.alias say talk.say");
		assertGetsResposne("#chan hi", "~say hi");
	}
}
