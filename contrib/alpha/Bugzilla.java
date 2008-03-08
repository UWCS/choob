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
	public BugzillaDetails(String alias, String url)
	{
		this.url = url;
		this.alias = alias;
	}
	public String url;
	public String alias;
}

public class Bugzilla
{
	private Modules mods;
	private IRCInterface irc;

	public Bugzilla(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	private List<BugzillaDetails> get(String alias)
	{
		return mods.odb.retrieve( BugzillaDetails.class , "WHERE alias = \"" + alias + "\"");
	}

	private boolean dupe(String alias, String url)
	{
		return get(alias).size() != 0;
	}

	public void commandAdd(Message mes)
	{
		mods.security.checkNickPerm(new ChoobPermission("plugins.bugzilla.add"), mes);
		List<String> params = mods.util.getParams(mes,2);

		if (params.size() != 3)
		{
			irc.sendContextReply(mes,"Usage: Add <alias> <baseurl>");
			return;
		}

		String alias = params.get(1).toLowerCase();
		String url = params.get(2);

		try 
		{
			new URL(url);
		} catch (MalformedURLException e)
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
 			BugzillaDetails bugdetails = new BugzillaDetails(alias,url);
			mods.odb.save( bugdetails );
			irc.sendContextReply(mes, "Ok, added bugzilla");
		}
		catch( IllegalStateException e )
		{
			irc.sendContextReply(mes, "Failed to add bugzilla: " + e.toString());
		}
	}

	public void commandDelete(Message mes)
	{
		mods.security.checkNickPerm(new ChoobPermission("plugins.bugzilla.remove"), mes);
		List<String> params = mods.util.getParams(mes,2);
		if (params.size() < 2)
		{
			irc.sendContextReply(mes, "Delete which bugzilla?");
			return;
		}

		String alias = params.get(1).toLowerCase();

		try
		{
			List<BugzillaDetails> thisBugzilla = mods.odb.retrieve( BugzillaDetails.class , "WHERE alias =\"" + alias + "\"");
			for (Object item : thisBugzilla)
			{
				mods.odb.delete(item);
			}
			irc.sendContextReply(mes,"Ok, deleted specified bugzilla");
		}
		catch( IllegalStateException e )
		{
			irc.sendContextReply(mes,"Couldn't delete: " + e);
		}
		
	}

	private final static String titlePattern = "(?s)<title>(.*?)</title>";
	private final static String getBugByNumberUrlSuffix = "/show_bug.cgi?id=";
	private String getBugSummary(String bugUrl,String bugNumber) throws MalformedURLException, IOException
	{

		URL url = new URL(bugUrl + getBugByNumberUrlSuffix + bugNumber);
		System.out.println(url);
		try
		{
			Matcher scraped = mods.scrape.getMatcher(url,0,titlePattern);
			if (scraped.find())
				return mods.scrape.readyForIrc(scraped.group(1));
		} catch (IllegalStateException e)
		{
			// What it says below is still true:
		}
		return "Could not find bug description.";
	}

	
	public void commandLookup(Message mes)
	{
		List<String> params = mods.util.getParams(mes,2);

		if (params.size() != 3)
		{
			irc.sendContextReply(mes,"Usage: Lookup <alias> <bugnumber>");
			return;
		}

		String alias = params.get(1).toLowerCase();
		String bugno = params.get(2);
		if (!bugno.matches("[0-9]+"))
		{
			irc.sendContextReply(mes,"Second parameter must be a valid bug number.");
			return;
		}


		List<BugzillaDetails> thisBugzillaList = get(alias);

		if ((thisBugzillaList == null) || (thisBugzillaList.size() == 0)) 
		{
			irc.sendContextReply(mes,"No configured bugzilla for that alias.");
			return;
		}
		BugzillaDetails details = thisBugzillaList.get(0);
		try
		{
			irc.sendContextReply(mes,getBugSummary(details.url,bugno));
		} catch (MalformedURLException e)
		{
			irc.sendContextReply(mes,"Invalid bugzilla url stored for this bugzilla.");
			return;
		} catch (IOException e)
		{
			irc.sendContextReply(mes,"Could not read from bugzilla site at this time.");
			return;
		}
	}
}
















