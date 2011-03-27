package uk.co.uwcs.choob;

import static junit.framework.Assert.assertTrue;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.DbConnectionBroker;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.Interval;
import uk.co.uwcs.choob.support.events.ChannelMessage;

final class MinimalBot extends UnsupportedOperationBot implements Closeable {
	private final BlockingQueue<String> queue = new ArrayBlockingQueue<String>(500);
	private final Modules mods;
	private final ChoobDecoderTaskData cdtd;
	private final ChoobThreadManager ctm;
	private final DbConnectionBroker broker;

	MinimalBot(DbConnectionBroker broker) {
		this.broker = broker;
		Choob.setupSecurity();
		ctm = new ChoobThreadManager();
		final IRCInterface irc = new IRCInterface(this);
		ChoobPluginManagerState state = new ChoobPluginManagerState(irc);
		mods = new Modules(broker, new ArrayList<Interval>(), this, irc, state, ctm);
		ctm.setMods(mods);
		irc.grabMods();
		cdtd = new ChoobDecoderTaskData(mods, irc, ctm);
	}

	@Override
	public String getTriggerRegex() {
		return "~";
	}

	@Override
	public Modules getMods() {
		return mods;
	}

	@Override
	public String getName() {
		return "meh";
	}

	/** DO NOT CALL THIS FROM TESTS */
	@Override
	public void sendMessage(String target, String data) {
		queue.add(target + " " + data);
	}

	@Override
	public void onPluginReLoaded(String pluginName) {
	}

	public String sentMessage() {
		try {
			final String polled = queue.poll(5, TimeUnit.SECONDS);
			if (null == polled)
				throw new RuntimeException("Expecting message to arrive, received nothing");
			return polled;
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public void spinChannelMessage(String message) {
 		Choob.spinThread(ctm, mods, cdtd, new ChannelMessage(
	 				"onMessage", System.currentTimeMillis(),
	 				0, message, "user", "bleh", "whee", "#chan", "#chan"), false);
	}

	@Override
	public void close() throws IOException {
		ctm.shutdown();
		try {
			assertTrue(ctm.awaitTermination(5, TimeUnit.SECONDS));
			broker.destroy();
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	public void addPlugin(String name) throws ChoobException {
		getMods().plugin.addPluginWithoutAddingToDb(name, "choob-plugin:/" + name + ".java");
	}

}