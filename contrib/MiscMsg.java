import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.*;

public class MiscMsg
{
	private static boolean hascoin=true;
	public void commandCT( Message con, Modules mods, IRCInterface irc )
	{
		randomReply(con, irc, new String[] { "Yes, your connection is working fine.", "No, your connection seems really broken." });
	}

	private void randomReply( Message con, IRCInterface irc, final String[] s)
	{
		irc.sendContextReply(con, s[(new Random()).nextInt(s.length)]);
	}

	public void commandFlipACoin( Message mes, Modules mods, IRCInterface irc )
	{
		if (!hascoin)
		{
			irc.sendContextReply(mes, "I've lost my coin. :-(");
			return;
		}

		String s=mods.util.getParamString(mes);

		if (s.length()==0)
		{
			// http://sln.fi.edu/fellows/fellow7/mar99/probability/gold_coin_flip.shtml
			if ((new Random()).nextDouble()==0)
			{
				irc.sendContextReply(mes, "Shit, I flicked it too hard, it's gone into orbit.");
				hascoin=false;
			}
			// Wikipedia++ http://en.wikipedia.org/wiki/Coin_tossing#Physics_of_coin_flipping
			else if ((new Random()).nextInt(6000)==0)
				irc.sendContextReply(mes, "Edge!");
			else
				irc.sendContextReply(mes, ((new Random()).nextBoolean() ? "Heads" : "Tails" ) + "!");

			return;
		}

		if (s.indexOf(',')==-1 && s.indexOf(" or ")==-1)
		{
			irc.sendContextReply(mes, "Answer to \"" + s + "\" is " + ((new Random()).nextBoolean() ? "yes" : "no" ) + ".");
			return;
		}

		String t=new String(s);
		if (s.substring(s.length()-1).equals("?"))
			s=s.substring(0,s.length()-1);

		// Did someone say.. horribly broken parser?

		ArrayList<String> v=new ArrayList();
		String []tokens = s.split(" or ");

		for (int i=0; i<tokens.length; i++)
		{
			String []toks = tokens[i].split(",");
			for (int j=0; j<toks.length; j++)
				v.add(toks[j].trim());
		}
		irc.sendContextReply(mes, "Answer to \"" + t + "\" is " + v.get((new Random()).nextInt(v.size())) + ".");
	}

	public void command8Ball( Message con, Modules mods, IRCInterface irc )
	{
		// http://r.wesley.edwards.net/writes/JavaScript/magic8ball.js
		randomReply(con, irc, new String[] {"Signs point to yes.", "Yes.", "Reply hazy, try again.", "Without a doubt.", "My sources say no.", "As I see it, yes.", "You may rely on it.", "Concentrate and ask again.", "Outlook not so good.", "It is decidedly so.", "Better not tell you now.", "Very doubtful.", "Yes - definitely.", "It is certain.", "Cannot predict now.", "Most likely.", "Ask again later.", "My reply is no.", "Outlook good.", "Don't count on it." });
	}

}
