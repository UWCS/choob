import org.junit.Before;
import org.junit.Test;

import uk.co.uwcs.choob.AbstractPluginTest;
import uk.co.uwcs.choob.support.ChoobException;


public class FeedsTest extends AbstractPluginTest {

	@Before
	public void loadPlugin() throws ChoobException {
		b.addPluginJs("Feeds");
	}

	@Test
	public void testList() {
		assertGetsResposne("#chan user: No feeds are set up.", "~feeds.list");
	}
}
