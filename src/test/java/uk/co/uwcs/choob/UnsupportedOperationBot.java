package uk.co.uwcs.choob;

import org.jibble.pircbot.User;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.events.Event;

class UnsupportedOperationBot implements Bot {
	@Override
	public Modules getMods() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getName() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getNick() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getTrigger() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getTriggerRegex() {
		throw new UnsupportedOperationException();
	}

	@Override
	public long getMessageDelay() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setExitCode(int i) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void onPluginReLoaded(String pluginName) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void onPluginUnLoaded(String pluginName) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void onSyntheticMessage(Event mes) {
		throw new UnsupportedOperationException();

	}

	@Override
	public String[] getChannels() {
		throw new UnsupportedOperationException();
	}

	@Override
	public User[] getUsers(String channel) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void ban(String channel, String hostmask) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void unBan(String channel, String hostmask) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void kick(String channel, String nick) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void kick(String channel, String nick, String reason) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void op(String channel, String nick) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void deOp(String channel, String nick) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void voice(String channel, String nick) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void partChannel(String channel) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void quitServer(String message) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void sendAction(String nick, String message) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void sendMessage(String target, String data) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void sendRawLineViaQueue(String line) {
		throw new UnsupportedOperationException();
	}
}