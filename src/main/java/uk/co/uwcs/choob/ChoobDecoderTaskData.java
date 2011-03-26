package uk.co.uwcs.choob;

import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;

public class ChoobDecoderTaskData {
	public final ChoobThreadManager ctm;
	public final Modules modules;
	public final IRCInterface irc;
	public Pattern triggerPattern;

	void updatePatterns() {
		triggerPattern = Pattern.compile("^(?:" + irc.getTriggerRegex() + ")", Pattern.CASE_INSENSITIVE);
	}

	public ChoobDecoderTaskData(final Modules mods, final IRCInterface ircinter, final ChoobThreadManager ctm) {
		this.modules = mods;
		this.irc = ircinter;
		this.ctm = ctm;
		updatePatterns();
	}
}