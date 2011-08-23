import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.ChannelMessage;
import uk.co.uwcs.choob.support.events.Message;

public class Face
{
	Modules mods;
	private final IRCInterface irc;

	public Face(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public String[] info()
	{
		return new String[] { "Face Plugin", "The Choob Team", "choob@uwcs.co.uk",
				"$Rev: 1166 $$Date: 2009-06-11 10:27:00 +0100 (Thu, 11 June 2009) $" };
	}

	public final String filterReplaceRegex = "([^\\s]+)face";
	private final Pattern compiledFilterReplaceRegex = Pattern.compile(filterReplaceRegex);

	public void filterReplace(final Message mes)
	{
		if (!(mes instanceof ChannelMessage))
			return;

		final String message = mes.getMessage();

		try
		{
			// Run the filter regex with the trigger.
			Matcher matcher = Pattern.compile(irc.getTriggerRegex() + filterReplaceRegex).matcher(message);
			if (!matcher.find())
				return;

			final String nick = matcher.group(1);

			irc.sendContextMessage(mes, nick + ": Waa!");
		}
		catch (final Exception e)
		{
			e.printStackTrace();
		}
	}
}
