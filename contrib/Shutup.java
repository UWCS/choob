import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;

public class Shutup
{
	final class LastMessage
	{
		static final int COMMANDS_TO_WATCH=4;			// How many commands to track?
		long lastmes[]=new long[COMMANDS_TO_WATCH];

		int stor=0;

		public LastMessage()
		{
			for (int i=0; i<COMMANDS_TO_WATCH; i++)
				lastmes[i]=600000*i;
			save();
		}

		public long difference()
		{
			return lastmes[(stor-1+COMMANDS_TO_WATCH)%COMMANDS_TO_WATCH]-lastmes[stor%COMMANDS_TO_WATCH];
		}

		public void save()
		{
			stor%=COMMANDS_TO_WATCH;
			lastmes[stor++]=(new java.util.Date()).getTime();
		}
	}

	Modules mods;
	IRCInterface irc;

	public Shutup(Modules mods, IRCInterface irc)
	{
		this.mods=mods;
		this.irc=irc;
	}

	static Map<String,LastMessage>lastMessage = Collections.synchronizedMap(new HashMap<String,LastMessage>()); // Channel, LastMessage

	static final long MIN_TIME_DIFFERENCE=6000;			// Minimum time difference?

	public String[] helpCommandAdd = {
		"Make the bot shut up in the current channel.",
	};
	public void commandAdd( Message mes, Modules mods, IRCInterface irc )
	{
		final String channel=mes.getContext();

		if (mes instanceof PrivateEvent)
		{
			irc.sendContextReply(mes, "You can't shut me up in private!");
			return;
		}

		if (!mods.pc.isProtected(channel))
		{
			irc.sendContextReply(mes, "Okay, shutting up in " + channel + ".");
			mods.pc.addProtected(channel);
		}
	}

	public String[] helpCommandRemove = {
		"Make the bot wake up in the current channel.",
	};
	public void commandRemove( Message mes, Modules mods, IRCInterface irc )
	{
		if (mes instanceof PrivateEvent)
		{
			irc.sendContextReply(mes, "You can't wake me up in private!");
			return;
		}
		if (mods.pc.isProtected(mes.getContext()))
		{
			mods.pc.removeProtected(mes.getContext());
			irc.sendContextReply(mes, "Yay, I'm free to speak in '" + mes.getContext() + "' again!");
		}
		else
			irc.sendContextReply(mes, "I'm not shut-up!");
	}

	public String[] helpCommandCheck = {
		"Check if the bot is shut up in the current channel.",
	};
	public void commandCheck( Message mes, Modules mods, IRCInterface irc )
	{
		if (mods.pc.isProtected(mes.getContext()))
			irc.sendContextReply(mes, "Yes, shut-up.");
		else
			irc.sendContextReply(mes, "Nope, not shut-up.");
	}

	public void interval(Object strChannel)
	{
		String channel=(String)strChannel;
		mods.pc.removeProtected(channel);
	}

	public void onMessage( ChannelMessage mes ) throws ChoobException
	{
		String channel=mes.getContext();
		if (channel==null)
			return;

		Matcher matcher = Pattern.compile(irc.getTriggerRegex()).matcher(mes.getMessage());
		if (!matcher.find())
			return;

		LastMessage la=lastMessage.get(channel);

		if (la==null)
			lastMessage.put(channel, new LastMessage());
		else
		{
			la.save();
			long laa=la.difference();
			System.out.println(laa);
			if (laa<MIN_TIME_DIFFERENCE)
			{
				if (!mods.pc.isProtected(channel))
					irc.sendMessage(channel, "Shutting up for a bit!");
				mods.pc.addProtected(channel);
				mods.interval.callBack(channel, 25000);
				return;
			}
		}
	}
}
