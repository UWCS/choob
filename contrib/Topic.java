import java.util.ArrayList;
import java.util.List;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.*;
import uk.co.uwcs.choob.support.events.ChannelInfo;

class TopicStr
{
	public int id;
	public String chan;
	public String newTopic;
	public String oldTopic;
}

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

	private static int MAXDIFFCHUNK = 100;
	private static int BLOCK = 10;
	private static int CONTEXT = 10;

	private IRCInterface irc;
	private Modules mods;
	public Topic(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	private synchronized TopicStr getTopic(String chan)
	{
		List<TopicStr> results = mods.odb.retrieve(TopicStr.class, "WHERE chan = \"" + mods.odb.escapeString(chan) + "\"");
		if (results.size() == 0)
		{
			TopicStr topic = new TopicStr();
			topic.chan = chan;
			return topic;
		}
		return results.get(0);
	}

	public static String getContextString(String into, int pos, int length)
	{
		String context;
		if (pos == 0)
			context = "at start";
		else if (pos + length == into.length())
			context = "at end";
		else if (pos >= CONTEXT)
			context = "after \"" + into.substring(pos - CONTEXT, pos) + "\"";
		else if (pos + length <= into.length() - CONTEXT)
			context = "before \"" + into.substring(pos + length, pos + length + CONTEXT) + "\"";
		else
			context = "after \"" + into.substring(0, pos) + "\"";

		return context;
	}

	public String[] helpCommandDiff = {
		"Explains the most recent change in topic in the current channel.",
		"[Channel]",
		"[Channel] is the channel to get the diff of. Only valid outside channels."
	};
	public synchronized void commandDiff( Message mes )
	{
		String which;

		if (!(mes instanceof ChannelEvent))
		{
			which = mods.util.getParamString(mes);
			if (which.length() < 2)
			{
				irc.sendContextReply(mes, "Which channel?");
				return;
			}
		}
		else
			which = mes.getContext();

		List<String> changes = new ArrayList<String>();

		// First, let's see if we can do this.
		TopicStr topic = getTopic(which);
		if (topic.newTopic == null)
		{
			irc.sendContextReply(mes, "Sorry, don't know the topic!");
			return;
		}
		else if (topic.oldTopic == null)
		{
			irc.sendContextReply(mes, "Sorry, haven't seen the topic change!");
			return;
		}
		String oldTopic = topic.oldTopic;
		String newTopic = topic.newTopic;

		System.out.println("New topic: " + newTopic);
		System.out.println("Old topic: " + oldTopic);

		int oPos = 0;
		int nPos = 0;
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

			System.out.println("Found difference! at (" + oPos + "," + nPos + ")");
			// Technique: Look for matches on the next n chars of each string
			// in the other.
			// Assume match blocks are always > 2*BLOCK chars. This means we can
			// search blocks of BLOCK chars.
			// Try newTopic in oldTopic first.
			int offset = 0;

			// Once we get a match of a in b, any further match will be the same.
			// These are set true to prefent repeat checks.
			boolean notDone1 = true, notDone2 = true;

			while(true)
			{
				boolean giveUp = true; // Remains true when exceeded both lengths.
				int nLength = -1; // Lengths of non-matches, when found.
				int oLength = -1;
				int bestLength = Integer.MAX_VALUE;
				if (offset + nPos + BLOCK < newTopic.length() && notDone1)
				{
					giveUp = false;
					System.out.println("nPos: " + nPos + ", offset: " + offset);
					String subStr = newTopic.substring(nPos + offset, nPos + offset + BLOCK);
					System.out.println("subStr: " + subStr);
					int found = oldTopic.indexOf(subStr, oPos);
					if (found != -1)
					{
						int tOffset = offset; // local value
						// Success!
						// Walk backwards until we find start of match.
						while(tOffset + nPos > 0 && found > oPos && tOffset > 0 && oldTopic.charAt(found - 1) == newTopic.charAt(tOffset + nPos - 1))
						{
							found--;
							tOffset--;
						}
						if (found - oPos + tOffset < bestLength)
						{
							oLength = found - oPos;
							nLength = tOffset;
							bestLength = oLength + nLength;
							notDone1 = false;
							System.out.println("Lengths: " + oLength + ", " + nLength);
							System.out.println("New string: " + newTopic.substring(nPos + nLength));
							System.out.println("Old string: " + oldTopic.substring(oPos + oLength));
						}
						else
						{
							System.out.println("Lengths: " + (found - oPos) + ", " + tOffset);
							System.out.println("New string: " + newTopic.substring(nPos + tOffset));
							System.out.println("Old string: " + oldTopic.substring(found));
							System.out.println("Ignoring: Too long! (bestLength: " + bestLength + ")");
						}
					}
				}
				if (offset + oPos + BLOCK < oldTopic.length() && notDone2)
				{
					giveUp = false;
					System.out.println("oPos: " + oPos + ", offset: " + offset);
					String subStr = oldTopic.substring(oPos + offset, oPos + offset + BLOCK);
					System.out.println("subStr: " + subStr);
					int found = newTopic.indexOf(subStr, nPos);
					if (found != -1)
					{
						int tOffset = offset; // local value
						// Success!
						// Walk backwards until we find start of match.
						while(tOffset + oPos > 0 && tOffset > 0 && found > nPos && oldTopic.charAt(tOffset + oPos - 1) == newTopic.charAt(found - 1))
						{
							found--;
							tOffset--;
						}
						if (found - nPos + tOffset < bestLength)
						{
							nLength = found - nPos;
							oLength = tOffset;
							bestLength = oLength + nLength;
							notDone2 = false;
							System.out.println("Lengths: " + oLength + ", " + nLength);
							System.out.println("New string: " + newTopic.substring(nPos + nLength));
							System.out.println("Old string: " + oldTopic.substring(oPos + oLength));
						}
						else
						{
							System.out.println("Lengths: " + tOffset + ", " + (found - nPos));
							System.out.println("New string: " + newTopic.substring(found));
							System.out.println("Old string: " + oldTopic.substring(oPos + tOffset));
							System.out.println("Ignoring: Too long! (bestLength: " + bestLength + ")");
						}
					}
				}

				if (offset + BLOCK > MAXDIFFCHUNK || (!notDone1 && !notDone2))
					giveUp = true;

				if (giveUp && oLength != -1)
				{
					// Ran out of viable string, but found a match.
					if (oLength > MAXDIFFCHUNK || nLength > MAXDIFFCHUNK)
					{
						// Found a match, but it's too big.
						System.out.println("Drop out 1.");
						irc.sendContextReply(mes, "Too many changes!");
						commandGet(mes);
						return;
					}

					// Grab substrings and make output if we find a match.
					if (oLength == 0)
					{
						changes.add("added \"" + newTopic.substring(nPos, nPos + nLength) + "\" " + getContextString(newTopic, nPos, nLength));
					}
					else if (nLength == 0)
					{
						changes.add("removed \"" + oldTopic.substring(oPos, oPos + oLength) + "\" " + getContextString(newTopic, nPos, nLength));
					}
					else
					{
						String oldBit = oldTopic.substring(oPos, oPos + oLength);
						String newBit = newTopic.substring(nPos, nPos + nLength);

						changes.add("\"" + oldBit + "\" changed to \"" + newBit + "\" " + getContextString(newTopic, nPos, nLength));
					}

					oPos = oPos + oLength;
					nPos = nPos + nLength;
					break;
				}
				else if (giveUp)
				{
					// No matches evah. Inference: Everything remaining
					String oldBit = oldTopic.substring(oPos);
					String newBit = newTopic.substring(nPos);

					if (oldBit.length() > MAXDIFFCHUNK || newBit.length() > MAXDIFFCHUNK)
					{
						// Remainder is too big!
						System.out.println("Drop out 2.");
						irc.sendContextReply(mes, "Too many changes!");
						commandGet(mes);
						return;
					}

					changes.add("\"" + oldBit + "\" changed to \"" + newBit + "\" at end");
					oPos = oldTopic.length();
					nPos = newTopic.length();
					break;
				}
				else
					offset += BLOCK;
			}
		}

		// Can't both be true...
		if (oPos != oldTopic.length())
		{
			changes.add("removed \"" + oldTopic.substring(oPos) + "\" at end");
		}
		else if (nPos != newTopic.length())
		{
			changes.add("added \"" + newTopic.substring(nPos) + "\" at end");
		}

		if (changes.size() == 0)
		{
			irc.sendContextReply(mes, "No changes!");
		}
		else
		{
			irc.sendContextReply(mes, "Topic diff: ");
			for(String diff: changes)
				irc.sendContextReply(mes, "   " + diff);
		}
	}

/*	public String[] helpCommandReset = {
		"Ask the bot to refresh the current topic in the current channel."
	};
	public synchronized void commandReset( Message mes )
	{
		if (!(mes instanceof ChannelEvent))
		{
			irc.sendContextReply(mes, "Sorry, can only do that in a channel!");
			return;
		}
		TopicStr topic = getTopic(mes.getContext());

		if (topic.id != 0)
			mods.odb.delete(topic);

		irc.sendRawLine("TOPIC " + mes.getContext());
		irc.sendContextReply(mes, "OK, asked for updated topic...");
	}*/

	public String[] helpCommandGet = {
		"Give the old and new topics in the current channel."
	};
	public synchronized void commandGet( Message mes )
	{
		if (!(mes instanceof ChannelEvent))
		{
			irc.sendContextReply(mes, "Sorry, can only do that in a channel!");
			return;
		}
		TopicStr topic = getTopic(mes.getContext());

		if (topic.id == 0)
		{
			irc.sendContextReply(mes, "No topic known!");
		}
		else
		{
			if (topic.oldTopic != null)
				irc.sendContextReply(mes, "Old topic: " + topic.oldTopic);
			irc.sendContextReply(mes, "Current topic: " + topic.newTopic);
		}
	}

	public synchronized void onChannelInfo( ChannelInfo info )
	{
		System.out.println("Channel info: " + info);
		String chan = info.getChannel();
		TopicStr topic = getTopic(chan);
		topic.oldTopic = topic.newTopic;
		topic.newTopic = info.getMessage();
		if (topic.id == 0)
			mods.odb.save(topic);
		else
			mods.odb.update(topic);
	}

	public synchronized void onTopic( ChannelTopic info )
	{
		System.out.println("Channel topic: " + info);
		String chan = info.getChannel();
		TopicStr topic = getTopic(chan);
		topic.oldTopic = topic.newTopic;
		topic.newTopic = info.getMessage();
		if (topic.id == 0)
			mods.odb.save(topic);
		else
			mods.odb.update(topic);
	}
}
