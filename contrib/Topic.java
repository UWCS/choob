import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.text.*;
import java.io.*;

public class Topic
{
	public String[] info()
	{
		return new String[] {
			"Plugin to watch for topic changes.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private IRCInterface irc;
	private Modules mods;
	public Topic(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	private Map<String,String> oldTopics = new HashMap<String,String>();
	private Map<String,String> topics = new HashMap<String,String>();

	public String[] helpCommandDiff = {
		"Explains the most recent change in topic."
	};
	public synchronized void commandDiff( Message mes )
	{
		if (!(mes instanceof ChannelEvent))
		{
			irc.sendContextReply(mes, "Sorry, can only do that in a channel!");
			return;
		}

		List<String> changes = new ArrayList<String>();

		// First, let's see if we can do this.
		String oldTopic = oldTopics.get(mes.getContext());
		String newTopic = topics.get(mes.getContext());
		if (newTopic == null)
		{
			irc.sendContextReply(mes, "Sorry, don't know the topic!");
			return;
		}
		else if (oldTopic == null)
		{
			irc.sendContextReply(mes, "Sorry, haven't seen the topic change!");
			return;
		}

		int oPos = 0;
		int nPow = 0;
		while(oPos < oldTopic.length() && nPos < newTopic.length())
		{
			if (oldTopic.charAt(oPos) == newTopic.charAt(nPos))
			{
				oPos++;
				nPos++;
				continue;
			}

			// OK, found a difference. It's either something gone or something
			// added. Note that both are kinda the same, but from different
			// perspecitves...
			// The other possibility is that something was changed. This is a
			// PITA to find. :)

			StringBuilder currentDiff = new StringBuilder();
			// Technique: Look for matches on the next n chars of each string
			// in the other.
			// Assume match blocks are always > 2*BLOCK chars. This means we can
			// search blocks of BLOCK chars.
			// Try newTopic in oldTopic first.
			int BLOCK = 5;
			int CONTEXT = 5;
			int offset = 0;
			while(true)
			{
				boolean giveUp = true; // Remains true when exceeded both lengths.
				int nLength = -1; // Lengths of non-matches, when found.
				int oLength = -1;
				if (offset + nPos < newTopic.length())
				{
					giveUp = false;
					String subStr = newTopic.substring(nPos + offset, BLOCK);
					int found = oldTopic.indexOf(subStr, oPos);
					if (found != -1)
					{
						// Success!
						// Walk backwards until we find start of match.
						while(oldTopic.charAt(found - 1) == newTopic.charAt(offset + nPos - 1))
						{
							found--;
							offset--;
						}
						oLength = found - oPos;
						nLength = offset;
					}
				}
				if (oLength == -1 && offset + oPos < oldTopic.length())
				{
					giveUp = false;
					String subStr = oldTopic.substring(oPos + offset, BLOCK);
					int found = newTopic.indexOf(subStr, nPos);
					if (found != -1)
					{
						// Success!
						// Walk backwards until we find start of match.
						while(oldTopic.charAt(offset + oPos - 1) == newTopic.charAt(found - 1))
						{
							found--;
							offset--;
						}
						nLength = found - nPos;
						oLength = offset;
					}
				}

				// Grab substrings and make output if we find a match.
				if (oLength != -1)
				{
					int oStart = Math.max(0, oPos - CONTEXT);
					int nStart = Math.max(0, nPos - CONTEXT);
					int oEnd = Math.min(oldTopic.length(), oPos + oLength + CONTEXT);
					int nEnd = Math.min(newTopic.length(), nPos + nLength + CONTEXT);
					String oldBit = oldTopic.substring(oStart, oEnd);
					String newBit = newTopic.substring(nStart, nEnd);

					changes.add("\"" + oldBit + "\" changed to \"" + newBit + "\"");
					oPos = oStart + oLength;
					nPos = nStart + nLength;
					break;
				}

				if (giveUp)
				{
					// No matches evah. Inference: Everything changed.
					int oStart = Math.max(0, oPos - CONTEXT);
					int nStart = Math.max(0, nPos - CONTEXT);
					String oldBit = oldTopic.substring(oStart);
					String newBit = newTopic.substring(nStart);
					changes.add("\"" + oldBit + "\" changed to \"" + newBit + "\"");
				}
				else
					offset += BLOCK;
			}
		}
		irc.sendContextReply(mes, "Topic diff: ");
		for(String diff: changes)
			irc.sendContextReply(mes, "   " + diff);
	}

	public String[] helpCommandReset = {
		"Re-retrieve the channel topic."
	};
	public synchronized void commandDiff( Message mes )
	{
		if (!(mes instanceof ChannelEvent))
		{
			irc.sendContextReply(mes, "Sorry, can only do that in a channel!");
			return;
		}
		oldTopics.remove(chan);
		topics.remove(chan);
		irc.sendRaw("TOPIC " + mes.getContext());
		irc.sendContextReply(mes, "OK, asked for updated topic...");
	}

	public synchronized void onChannelInfo( ChannelInfo info )
	{
		System.out.println("Channel info: " + info);
		String chan = info.getChannel();
		oldTopics.remove(chan);
		topics.put(chan, info.getMessage());
	}

	public synchronized void onTopic( ChannelTopic info )
	{
		System.out.println("Channel topic: " + info);
		String chan = info.getChannel();
		if (topics.get(chan) != null)
			oldTopics.put(chan, topics.get(chan));
		topics.put(chan, info.getMessage());
	}
}
