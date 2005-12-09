import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import uk.co.uwcs.choob.modules.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class SVN
{
	public String[] info()
	{
		return new String[] {
			"Watches the Choob SVN for new versions.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			mods.util.getVersion()
		};
	}

	private Modules mods;
	private IRCInterface irc;
	public SVN( Modules mods, IRCInterface irc )
	{
		mods.interval.callBack( null, 100 );
		this.irc = irc;
		this.mods = mods;
	}

	public synchronized void interval( Object parameter ) throws ChoobException
	{
		URL svnURL;
		try
		{
			svnURL = new URL("http://svn.uwcs.co.uk/repos/choob/");
		}
		catch (MalformedURLException e)
		{
			throw new ChoobException("Constant URL in SVN plugin broken...");
		} // squelch

		String revString;

		try
		{
			BufferedReader svnPage = new BufferedReader( new InputStreamReader( svnURL.openStream() ) );

			revString = svnPage.readLine();
		}
		catch (IOException e)
		{
			throw new ChoobException("IO Exception reading version number.", e);
		}

		String newRev = revString.substring( revString.indexOf("tle>")+4, revString.indexOf(":") );

		if( parameter != null )
		{
			if( newRev.compareTo( (String)parameter ) != 0 )
			{
				irc.sendMessage("#bots", newRev + " now available in Subversion!");
			}
		}

		mods.interval.callBack( newRev, 30000 );
	}
}
