package uk.co.uwcs.choob;

import static org.junit.Assert.assertEquals;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.DbConnectionBroker;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.Interval;
import uk.co.uwcs.choob.support.events.ChannelMessage;

public class TalkTest {
	public TalkTest() throws SQLException {
		broker = new DbConnectionBroker("org.sqlite.JDBC", "jdbc:sqlite:test" + System.nanoTime() + ".db",
				null, null, 0, 10, new PrintWriter(System.err), 60);
	}

	final DbConnectionBroker broker;

	void sql(String sql) throws SQLException {
		final Connection conn = broker.getConnection();
		try {
			Statement stat = conn.createStatement();
			try {
				stat.execute(sql);
			} finally {
				stat.close();
			}
		} finally {
			broker.freeConnection(conn);
		}
	}

	static class Mutable<T> {
		T t;
	}

	@Test
	public void testTalk() throws SQLException, InterruptedException, ChoobException {
		final Mutable<Modules> moduleRef = new Mutable<Modules>();
		final BlockingQueue<String> queue = new ArrayBlockingQueue<String>(5);
		final Bot b = new UnsupportedOperationBot() {
			@Override
			public String getTriggerRegex() {
				return "~";
			}

			@Override
			public Modules getMods() {
				return moduleRef.t;
			}

			@Override
			public String getName() {
				return "meh";
			}

			@Override
			public void sendMessage(String target, String data) {
				queue.add(target + " " + data);
			}

			@Override
			public void onPluginReLoaded(String pluginName) {
			}
		};

		table("UserNodes", "NodeID, NodeName, NodeClass");
		table("History",  "LineID, Type, Nick, Hostmask, Channel, Text, Time, Random");

		System.setProperty("choobDebuggerHack", "false");

		Choob.setupSecurity();
		ChoobThreadManager ctm = new ChoobThreadManager();
		final IRCInterface irc = new IRCInterface(b);
		ChoobPluginManagerState state = new ChoobPluginManagerState(irc);
		Modules mods = new Modules(broker, new ArrayList<Interval>(), b, irc, state, ctm);
		moduleRef.t = mods;
		ctm.setMods(mods);
		irc.grabMods();
		ChoobDecoderTaskData cdtd = new ChoobDecoderTaskData(mods, irc, ctm);

		mods.plugin.addPluginWithoutAddingToDb("Talk", "choob-plugin:/Talk.java");

		Choob.spinThread(ctm, mods, cdtd, new ChannelMessage(
				"onMessage", System.currentTimeMillis(),
				0, "~talk.say hi", "user", "bleh", "whee", "#chan", "#chan"), false);

		assertEquals("#chan hi", queue.poll(5, TimeUnit.SECONDS));
		ctm.shutdown();
		ctm.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
	}

	private void table(String name, String fields) throws SQLException {
		try {
			sql("drop table " + name);
		} catch (SQLException ignored) {
		}

		sql("create table " + name + " (" + fields + ")");
	}
}
