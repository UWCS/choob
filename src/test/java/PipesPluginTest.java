import static org.junit.Assert.assertTrue;

import org.junit.Test;

import uk.co.uwcs.choob.AbstractPluginTest;
import uk.co.uwcs.choob.support.ChoobException;


public class PipesPluginTest extends AbstractPluginTest {

	public PipesPluginTest() throws ChoobException {
		super("Talk", "Alias", "Pipes", "MiscUtils", "MiscMsg");
	}

	@Test
	public void testTalk() {
		assertGetsResposne("#chan user: hi", "~pipes.eval talk.say hi");
	}

	@Test
	public void testToSh() {
		assertGetsResposne("#chan user: hi", "~pipes.eval talk.say talk.say hi | pipes.eval");
	}

	@Test
	public void testInevitable() {
		sendAndIgnoreReply("~alias.alias alias alias.alias");
		sendAndIgnoreReply("~alias quoterin pipes.eval sed \"s/'/'\\\\\\\\''/g\"");
		sendAndIgnoreReply("~alias quoter pipes.eval quoterin | sed \"s/^/'/\" | sed \"s/$/'/\"");
		sendAndIgnoreReply("~alias sh pipes.eval");
		sendAndIgnoreReply("~alias getter pipes.eval alias.showalias $1 | sed 's/^talk.say //'");
		sendAndIgnoreReply("~alias flipacoin miscmsg.flipacoin");
		sendAndIgnoreReply("~alias echo talk.say");
		sendAndIgnoreReply("~alias expand pipes.eval pick // | sed 's/\\b(\\w{4,7})\\b/$$1 or $$1/g' " +
				"| sed 's/\\b(\\w{8,})\\b/$$1 or $$1 or $$1/g' " +
				"| sed 's/^ *or //' | sed 's/^\\w*$/nigger or nigger/'");

		sendAndIgnoreReply("~alias libniggeration pipes.eval echo echo " +
				"$(echo $* | quoter) $$(flipacoin $$(echo $* | sed 's/nigger//g' |  sed 's/\\p{Punct}//g'  " +
				"| sed 's/(?!<^)(?!$$) +/ or /g' | expand) | sed 's/My answer is (.*)\\./" +
				"| sed \"s뢚(?<=\\\\\\\\b|\\\\\\\\s|\\\\\\\\p{Punct})$$1(?=\\\\x24|\\\\\\\\b|\\\\\\\\s|\\\\\\\\p{Punct})뢚nigger뢚g\"/') " +
				"| sh");

		sendAndIgnoreReply("~alias inevitable pipes.eval export nig_$[chan]_out echo $$(pick '/^[^!].* /' " +
				"| sed s/^$$(getter nig_$[chan]_in | sed 's,/,\\\\/,g')$$/$$(getter nig_$[chan]_out " +
				"| sed 's,/,\\\\/,g')/ | sed \"s/'/\\\\\\\\'/g\" | xargs libniggeration) " +
				"| export nig_$[chan]_in echo $(pick '/^[^!].* /') | getter nig_$[chan]_out");
		send("omg ponies");
		send("~inevitable");
		final String msg = b.sentMessage();
		assertTrue("message should be 'omg nigger' or 'nigger ponies', not " + msg,
				msg.equals("#chan user: omg nigger") || msg.equals("#chan user: nigger ponies"));
	}
}
