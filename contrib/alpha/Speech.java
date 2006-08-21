import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;
import com.sun.speech.freetts.audio.JavaClipAudioPlayer;


import org.jibble.pircbot.Colors;

/**
 * Choob talky talky plugin
 *
 * Depends on http://freetts.sourceforge.net/ being installed correctly, and in the bot's classpath.
 *
 * @author Faux
 */

public class Speech
{
	final String voiceName = "kevin16";
	Voice ourVoice;

	public String[] info()
	{
		return new String[] {
			"Plugin which allows users to make the bot speak.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private Modules mods;
	private IRCInterface irc;
	public Speech(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;

		VoiceManager voiceManager = VoiceManager.getInstance();
		ourVoice = voiceManager.getVoice(voiceName);

		if (ourVoice == null)
			System.err.println("Cannot find a voice named "	+ voiceName + ".  Please specify a different voice.");
		else
			ourVoice.allocate();
	}

	void speak(String text)
	{
		if (ourVoice == null)
			throw new IllegalArgumentException("No voice! :'(");
		ourVoice.speak(text);
	}

	public String[] helpCommandShout = {
		"Give a yell to deafen countries.",
		"<Text>",
		"<Text> is the text to yell"
	};
	public void commandShout( Message mes )
	{
		speak(mods.util.getParamString(mes) + "!");
		irc.sendContextReply(mes, Colors.BOLD + mods.util.getParamString(mes));
	}

	public String[] helpCommandSay = {
		"Get the bot to say something.",
		"<Text>",
		"<Text> is the text to say"
	};
	public void commandSay( Message mes )
	{
		speak(mods.util.getParamString(mes));
		irc.sendContextMessage(mes, mods.util.getParamString(mes));
	}

	public String[] helpCommandReply = {
		"Get the bot to reply to you.",
		"<Text>",
		"<Text> is the text with which to reply"
	};
	public void commandReply( Message mes )
	{
		speak(mes.getNick() + ", " + mods.util.getParamString(mes));
		irc.sendContextReply(mes, mods.util.getParamString(mes));
	}
}
