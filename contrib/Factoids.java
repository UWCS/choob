import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

class FactoidObject
{
	public FactoidObject()
	{
		// Unhide
	}

	public FactoidObject( final String subject, final String info )
	{
		this.subject = subject;
		this.info = info;
	}

	public int id;
	public String subject;
	public String info;
}

public class Factoids
{
	public String filterFactoidsRegex = "(\\w{4,})\\s+(?:is|was)\\s+(.{4,})";

	public String[] info()
	{
		return new String[] {
			"Factoid watching/query plugin.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private final Modules mods;
	private final IRCInterface irc;
	public Factoids(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public void filterFactoids( final Message msg )
	{
		final Matcher factoidMatcher = Pattern.compile( filterFactoidsRegex ).matcher( msg.getMessage() );

		factoidMatcher.find();

		try
		{
			final FactoidObject fact = new FactoidObject( factoidMatcher.group(1).toLowerCase() , factoidMatcher.group(2) );
			mods.odb.save( fact );
		}
		catch( final IllegalStateException e )
		{
			// Well shiver me timbers! What the hell causes this?
		}
	}

	public String[] helpTopics = { "Remember" };

	public String[] helpRemember = {
		  "The bot will automagically remember things which are said in the"
		+ " channels of the form '<a> is <b>' or '<a> was <b>'."
	};

	public String[] helpCommandWhatIs = {
		"Ask the bot what it thinks something is.",
		"<Name>",
		"<Name> is the name of the object to enquire about"
	};
	public void commandWhatIs( final Message msg )
	{
		final List<String> params = mods.util.getParams(msg, 1);

		if (params.size()<2)
		{
			irc.sendContextReply(msg, "Whatis what?");
			return;
		}

		final String item = params.get(1).replaceAll("\\?","");

		final List<FactoidObject> facts = mods.odb.retrieve( FactoidObject.class , "SORT RANDOM LIMIT (1) WHERE subject = \"" + mods.odb.escapeString(item.toLowerCase()) + "\"");

		if( facts.size() > 0 )
		{
			final FactoidObject fact = facts.get(0);
			irc.sendContextReply( msg, item + " is " + fact.info );
		}
		else
			irc.sendContextReply( msg, "I don't know anything about " + item + "!");
	}
}
