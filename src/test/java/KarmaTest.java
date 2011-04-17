import org.jibble.pircbot.Colors;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import uk.co.uwcs.choob.AbstractPluginTest;
import uk.co.uwcs.choob.support.ChoobException;

public class KarmaTest extends AbstractPluginTest {

	@Before
	public void loadPlugin() throws ChoobException {
		b.addPlugin("Karma");
	}

	@Test
	public void testFoo() {
		assertGetsResposne("#chan user: Given more karma to \"foo" + Colors.NORMAL + "\". New karma is 1.", "foo++");
		assertGetsResposne("#chan user: Given more karma to \"foo" + Colors.NORMAL + "\". New karma is 2.", "a foo++");
		assertGetsResposne("#chan user: Given less karma to \"foo" + Colors.NORMAL + "\". New karma is 1.", "a foo--");
		assertGetsResposne("#chan user: Given less karma to \"foo" + Colors.NORMAL + "\". New karma is 0.", "foo-- b");
		assertGetsResposne("#chan user: Given less karma to \"foo" + Colors.NORMAL + "\". New karma is -1.", "foo--");
	}

	@Test
	@Ignore("#386 Reasons aren't available immediately")
	public void testReasons() {
		assertGetsResposne("#chan user: Given more karma to \"foo" + Colors.NORMAL + "\" and understood your reason. New karma is 1.", "foo++ for bar");
		assertGetsResposne("#chan user: Given less karma to \"foo" + Colors.NORMAL + "\" and understood your reason. New karma is 0.", "foo-- (baz)");
		assertGetsResposne("#chan user: Given less karma to \"foo" + Colors.NORMAL + "\" and understood your reason. New karma is 0.", "~karma.reasonup foo");
		assertGetsResposne("#chan user: Given less karma to \"foo" + Colors.NORMAL + "\" and understood your reason. New karma is 0.", "~karma.reasondown foo");
	}
}
