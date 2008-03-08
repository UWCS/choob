import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.ChannelMessage;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.PrivateEvent;

public class Shutup
{
	public String[] info()
	{
		return new String[] {
			"Plugin to allow people to manipulate the protected channels in the bot.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	// LastMessage tracks the last few events for a single channel. It stores
	// the timestamp for each message, and allows the retrieval of the 
	// "difference" - the time between the most recent and oldest event.
	final class LastMessage
	{
		static final int EVENTS_TO_WATCH = 4;
		long lastmes[] = new long[EVENTS_TO_WATCH];
		
		int stor = 0;
		
		public LastMessage()
		{
			// Store times in the past, that are 600s apart (FIXME: why?).
			for (int i = 0; i < EVENTS_TO_WATCH; i++)
				lastmes[i] = 600000/*ms*/ * i;
			save();
		}
		
		// Returns the difference between the oldest and newest events.
		public long difference()
		{
			return lastmes[(stor - 1 + EVENTS_TO_WATCH) % EVENTS_TO_WATCH] - lastmes[stor % EVENTS_TO_WATCH];
		}
		
		// Stores the current time in the list, as the most recent event.
		public void save()
		{
			stor %= EVENTS_TO_WATCH;
			lastmes[stor++] = (new java.util.Date()).getTime();
		}
	}
	
	Modules mods;
	IRCInterface irc;
	
	public Shutup(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}
	
	// Stores the information about the last events for each channel separately.
	static Map<String,LastMessage>lastMessage = Collections.synchronizedMap(new HashMap<String,LastMessage>());
	
	// Minimum time difference between oldest and newest events to trigger shut-up mode.
	static final long MIN_TIME_DIFFERENCE = 6000;
	
	public String[] helpCommandAdd = {
		"Make the bot shut up in the current channel.",
	};
	public void commandAdd(Message mes)
	{
		final String channel = mes.getContext();
		
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
	public void commandRemove(Message mes)
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
		{
			irc.sendContextReply(mes, "But I can already speak!");
		}
	}
	
	public String[] helpCommandCheck = {
		"Check if the bot is shut up in the current channel.",
	};
	public void commandCheck(Message mes)
	{
		if (mods.pc.isProtected(mes.getContext()))
			irc.sendContextReply(mes, "Can't speak!");
		else
			irc.sendContextReply(mes, "Can speak!");
	}
	
	public void interval(Object strChannel)
	{
		String channel = (String)strChannel;
		mods.pc.removeProtected(channel);
	}
	
	public void onMessage(ChannelMessage mes)
	{
		String channel = mes.getContext();
		if (channel == null)
			return;
		
		// Skip non-command lines.
		if (!mes.getFlags().containsKey("command"))
			return;
		
		LastMessage la = lastMessage.get(channel);
		
		if (la == null)
		{
			lastMessage.put(channel, new LastMessage());
		}
		else
		{
			la.save();
			long totalEventDelay = la.difference();
			if (totalEventDelay < MIN_TIME_DIFFERENCE)
			{
				if (!mods.pc.isProtected(channel))
				{
					irc.sendMessage(channel, "Shutting up for a bit!");
					mods.pc.addProtected(channel);
				}
				mods.interval.callBack(channel, 25000);
			}
		}
	}
}
