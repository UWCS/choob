package uk.co.uwcs.choob;

import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;

import org.junit.Test;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.DbConnectionBroker;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.Interval;
import uk.co.uwcs.choob.support.events.ChannelMessage;


public class TalkTest {
	@Test
	public void testTalk() throws SQLException {
		DbConnectionBroker broker =
			new DbConnectionBroker("org.sqlite.JDBC", "jdbc:sqlite:sample.db",
					null, null, 0, 10, new PrintWriter(System.err), 60);

		final Bot b = new UnsupportedOperationBot() {

		};

		Choob.setupSecurity();
		ChoobThreadManager ctm = new ChoobThreadManager();
		final IRCInterface irc = new IRCInterface(b);
		ChoobPluginManagerState state = new ChoobPluginManagerState(irc);
		Modules mods = new Modules(broker, new ArrayList<Interval>(), b, irc, state, ctm);
		ctm.setMods(mods);
		ChoobDecoderTaskData cdtd = new ChoobDecoderTaskData(mods, irc, ctm);

		Choob.spinThread(ctm, mods, cdtd, new ChannelMessage(
				"onMessage", System.currentTimeMillis(),
				0, "hi", "user", "bleh", "whee", "#chan", "#chan"), false);
	}
}
