import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

class BugzillaDetails
{
	public int id;
	public BugzillaDetails()
	{
		// Unhiding
	}
	public BugzillaDetails(final String alias, final String url)
	{
		this.url = url;
		this.alias = alias;
	}
	public String url;
	public String alias;
}

public class Bugzilla
{
	private final Modules mods;
	private final IRCInterface irc;

	public Bugzilla(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	private List<BugzillaDetails> get(final String alias)
	{
		return mods.odb.retrieve( BugzillaDetails.class , "WHERE alias = \"" + alias + "\"");
	}

	private boolean dupe(final String alias, final String url)
	{
		return get(alias).size() != 0;
	}

	public void commandAdd(final Message mes)
	{
		mods.security.checkNickPerm(new ChoobPermission("plugins.bugzilla.add"), mes);
		final List<String> params = mods.util.getParams(mes,2);

		if (params.size() != 3)
		{
			irc.sendContextReply(mes,"Usage: Add <alias> <baseurl>");
			return;
		}

		final String alias = params.get(1).toLowerCase();
		final String url = params.get(2);

		try
		{
			new URL(url);
		} catch (final MalformedURLException e)
		{
			irc.sendContextReply(mes,"Second parameter must be a valid url");
			return;
		}

		if (dupe(alias,url))
		{
			irc.sendContextReply(mes,"That bugzilla is already configured");
			return;
		}
		try
		{
 			final BugzillaDetails bugdetails = new BugzillaDetails(alias,url);
			mods.odb.save( bugdetails );
			irc.sendContextReply(mes, "Ok, added bugzilla");
		}
		catch( final IllegalStateException e )
		{
			irc.sendContextReply(mes, "Failed to add bugzilla: " + e.toString());
		}
	}

	public void commandDelete(final Message mes)
	{
		mods.security.checkNickPerm(new ChoobPermission("plugins.bugzilla.remove"), mes);
		final List<String> params = mods.util.getParams(mes,2);
		if (params.size() < 2)
		{
			irc.sendContextReply(mes, "Delete which bugzilla?");
			return;
		}

		final String alias = params.get(1).toLowerCase();

		try
		{
			final List<BugzillaDetails> thisBugzilla = mods.odb.retrieve( BugzillaDetails.class , "WHERE alias =\"" + alias + "\"");
			for (final Object item : thisBugzilla)
			{
				mods.odb.delete(item);
			}
			irc.sendContextReply(mes,"Ok, deleted specified bugzilla");
		}
		catch( final IllegalStateException e )
		{
			irc.sendContextReply(mes,"Couldn't delete: " + e);
		}

	}

	private final static String titlePattern = "(?s)<title>(.*?)</title>";
	private final static String getBugByNumberUrlSuffix = "/show_bug.cgi?id=";
	private String getBugSummary(final String bugUrl,final String bugNumber) throws MalformedURLException, IOException
	{

		final URL url = new URL(bugUrl + getBugByNumberUrlSuffix + bugNumber);
		System.out.println(url);
		try
		{
			final Matcher scraped = mods.scrape.getMatcher(url,0,titlePattern);
			if (scraped.find())
				return mods.scrape.readyForIrc(scraped.group(1));
		} catch (final IllegalStateException e)
		{
			// What it says below is still true:
		}
		return "Could not find bug description.";
	}


	public void commandLookup(final Message mes)
	{
		final List<String> params = mods.util.getParams(mes,2);

		if (params.size() != 3)
		{
			irc.sendContextReply(mes,"Usage: Lookup <alias> <bugnumber>");
			return;
		}

		final String alias = params.get(1).toLowerCase();
		final String bugno = params.get(2);
		if (!bugno.matches("[0-9]+"))
		{
			irc.sendContextReply(mes,"Second parameter must be a valid bug number.");
			return;
		}


		final List<BugzillaDetails> thisBugzillaList = get(alias);

		if (thisBugzillaList == null || thisBugzillaList.size() == 0)
		{
			irc.sendContextReply(mes,"No configured bugzilla for that alias.");
			return;
		}
		final BugzillaDetails details = thisBugzillaList.get(0);
		try
		{
			irc.sendContextReply(mes,getBugSummary(details.url,bugno));
		} catch (final MalformedURLException e)
		{
			irc.sendContextReply(mes,"Invalid bugzilla url stored for this bugzilla.");
			return;
		} catch (final IOException e)
		{
			irc.sendContextReply(mes,"Could not read from bugzilla site at this time.");
			return;
		}
	}
}
















