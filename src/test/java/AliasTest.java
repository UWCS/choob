import org.jibble.pircbot.Colors;
import org.junit.Before;
import org.junit.Test;

import uk.co.uwcs.choob.AbstractPluginTest;
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

	@Test
	public void testAliasSearch() {
		assertGetsResposne("#chan user: Aliases matching /say/: \"say" + Colors.NORMAL + "\". (1 result)",
				"~alias.list /say/");
	}

	@Test
	public void testAliasSearchRegex() {
		assertGetsResposne("#chan user: Aliases matching /^say$/: \"say" + Colors.NORMAL + "\". (1 result)",
				"~alias.list /^say$/");
	}
}
