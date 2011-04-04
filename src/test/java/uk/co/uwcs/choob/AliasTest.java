package uk.co.uwcs.choob;

import org.junit.Before;
import org.junit.Test;

import uk.co.uwcs.choob.support.ChoobException;

public class AliasTest extends AbstractPluginTest {

	@Before
	public void loadPlugin() throws ChoobException {
		b.addPlugin("Talk");
		b.addPlugin("Alias");

		assertGetsResposne("#chan user: Aliased 'say' to 'talk.say'.", "~alias.alias say talk.say");
	}

	@Test
	public void testSay() {
		assertGetsResposne("#chan hi", "~say hi");
	}

	@Test
	public void testReAlias() {
		assertGetsResposne("#chan user: Aliased 'say' to 'talk.reply' (was 'talk.say').",
				"~alias.alias say talk.reply");
	}
}
