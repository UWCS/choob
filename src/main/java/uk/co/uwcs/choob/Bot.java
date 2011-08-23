package uk.co.uwcs.choob;

import org.jibble.pircbot.User;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.events.Event;

public interface Bot {

	Modules getMods();
	String getName();
	String getNick();
	String getTrigger();
	String getTriggerRegex();

	long getMessageDelay();
	void setExitCode(int i);

	void onPluginReLoaded(String pluginName);
	void onPluginUnLoaded(String pluginName);
	void onSyntheticMessage(Event mes);

	String[] getChannels();
	User[] getUsers(String channel);

	void ban(String channel, String hostmask);
	void unBan(String channel, String hostmask);

	void kick(String channel, String nick);
	void kick(String channel, String nick, String reason);

	void op(String channel, String nick);
	void deOp(String channel, String nick);
	void voice(String channel, String nick);

	void partChannel(String channel);
	void quitServer(String message);

	void sendAction(String nick, String message);
	void sendMessage(String target, String data);
	void sendRawLineViaQueue(String line);
}
