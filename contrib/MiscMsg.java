import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.*;
import java.text.*;

public class MiscMsg
{
	private IRCInterface irc;
	private Modules mods;
	public MiscMsg(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	private static boolean hascoin = true;

	public String[] helpCommandCT = {
		"Replies indicating whether the bot suspects your connection is up the spout."
	};
	public void commandCT( Message mes )
	{
		randomReply(mes, new String[] { "Yes, your connection is working fine.", "No, your connection seems really broken." });
	}

	public String[] helpCommandTime = {
		"Replies with the current time."
	};
	public void commandTime( Message mes )
	{
		irc.sendContextReply(mes, new SimpleDateFormat("'The time is 'hh:mm:ss'.'").format(new Date()));
	}

	public String[] helpCommandDate = {
		"Replies with the current date."
	};
	public void commandDate( Message mes )
	{
		irc.sendContextReply(mes, new SimpleDateFormat("'The date is 'd MMM yyyy'.'").format(new Date()));
	}

	public String[] helpCommandRandom = {
		"Get with a random number.",
		"[<Max>]",
		"<Max> is the optional maximum to return"
	};
	public void commandRandom( Message mes )
	{
		double max = 1;
		try
		{
			max = Double.parseDouble(mods.util.getParamString(mes));
		}
		catch (NumberFormatException e) {
			irc.sendContextReply(mes, "Sorry, " + mods.util.getParamString(mes) + " is not a valid number!");
			return;
		}
		irc.sendContextReply(mes, "Random number between 0 and " + max + " is " + new Random().nextDouble()*max + ".");
	}

	private void randomReply(Message mes, String[] replies )
	{
		irc.sendContextReply(mes, replies[(new Random()).nextInt(replies.length)]);
	}

	public String[] helpCommandFlipACoin = {
		"Flip a coin and find the result.",
		"[<Reply> or <Reply> [ or <Reply> ... ]]",
		"<Reply> is some reply to write on one of the sides of the coin (coins can have more than 2 sides!)"
	};
	public void commandFlipACoin( Message mes )
	{
		if (!hascoin)
		{
			irc.sendContextReply(mes, "I've lost my coin. :-(");
			return;
		}

		String params = mods.util.getParamString(mes);
		Random rand = new Random();

		if (params.length() == 0)
		{
			// http://sln.fi.edu/fellows/fellow7/mar99/probability/gold_coin_flip.shtml
			if (rand.nextDouble()==0)
			{
				irc.sendContextReply(mes, "Shit, I flicked it too hard, it's gone into orbit.");
				hascoin = false;
			}
			// Wikipedia++ http://en.wikipedia.org/wiki/Coin_tossing#Physics_of_coin_flipping
			else if (rand.nextInt(6000) == 0)
				irc.sendContextReply(mes, "Edge!");
			else
				irc.sendContextReply(mes, (rand.nextBoolean() ? "Heads" : "Tails" ) + "!");

			return;
		}

		if (params.indexOf(',')==-1 && params.indexOf(" or ")==-1)
		{
			irc.sendContextReply(mes, "Answer to \"" + params + "\" is " + (rand.nextBoolean() ? "yes" : "no" ) + ".");
			return;
		}

		// Cut off a question mark, if any.
		if (params.charAt(params.length()-1) == '?')
			params = params.substring(0,params.length()-1);

		// Did someone say.. horribly broken parser?

		ArrayList<String> replies = new ArrayList();
		String[] tokens = params.split("\\s+(?:or|,)\\s+");

		for (int i=0; i<tokens.length; i++)
		{
			replies.add(tokens[i]);
		}
		irc.sendContextReply(mes, "Answer to \"" + params + "\" is " + replies.get(rand.nextInt(replies.size())) + ".");
	}

	public String[] helpCommand8Ball = {
		"Ask the magical 8 ball to sort out your life.",
		"<Question>",
		"<Question> is some a question for the 8 ball the think over."
	};
	public void command8Ball( Message mes )
	{
		// http://r.wesley.edwards.net/writes/JavaScript/magic8ball.js
		randomReply(mes, new String[] {"Signs point to yes.", "Yes.", "Reply hazy, try again.", "Without a doubt.", "My sources say no.", "As I see it, yes.", "You may rely on it.", "Concentrate and ask again.", "Outlook not so good.", "It is decidedly so.", "Better not tell you now.", "Very doubtful.", "Yes - definitely.", "It is certain.", "Cannot predict now.", "Most likely.", "Ask again later.", "My reply is no.", "Outlook good.", "Don't count on it." });
	}

}
