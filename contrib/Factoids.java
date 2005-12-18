import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;

public class FactoidObject
{
	public FactoidObject()
	{
	}

	public FactoidObject( String subject, String info )
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

	private Modules mods;
	private IRCInterface irc;
	public Factoids(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public void filterFactoids( Message msg ) throws ChoobException
	{
		Matcher factoidMatcher = (Pattern.compile( filterFactoidsRegex )).matcher( msg.getMessage() );

		factoidMatcher.find();

		try
		{
			FactoidObject fact = new FactoidObject( factoidMatcher.group(1).toLowerCase() , factoidMatcher.group(2) );
			mods.odb.save( fact );
		}
		catch( IllegalStateException e )
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
	public void commandWhatIs( Message msg ) throws ChoobException
	{
		List<String> params = mods.util.getParams(msg, 1);

		if (params.size()<2)
		{
			irc.sendContextReply(msg, "Whatis what?");
			return;
		}

		final String item = params.get(1).replaceAll("\\?","");

		List facts = mods.odb.retrieve( FactoidObject.class , "SORT RANDOM LIMIT (1) WHERE subject = \"" + item.toLowerCase() + "\"");

		if( facts.size() > 0 )
		{
			FactoidObject fact = (FactoidObject)facts.get(0);
			irc.sendContextReply( msg, item + " is " + fact.info );
		}
		else
			irc.sendContextReply( msg, "I don't know anything about " + item + "!");
	}
}
