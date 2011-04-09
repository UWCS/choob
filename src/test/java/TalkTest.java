import org.junit.Before;
import org.junit.Test;

import uk.co.uwcs.choob.AbstractPluginTest;
import uk.co.uwcs.choob.support.ChoobException;

public class TalkTest extends AbstractPluginTest {

	@Before
	public void loadPlugin() throws ChoobException {
		b.addPlugin("Talk");
	}

	@Test
	public void testTalk() {
		assertGetsResposne("#chan hi", "~talk.say hi");
	}

	@Test
	public void testReply() {
		assertGetsResposne("#chan user: hi", "~talk.reply hi");
	}
}
