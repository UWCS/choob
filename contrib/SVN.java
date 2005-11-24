import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import uk.co.uwcs.choob.modules.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class SVN
{
	public SVN( Modules mods )
	{
		mods.interval.callBack( null, 100 );
	}

	public synchronized void interval( Object parameter, Modules mods, IRCInterface irc ) throws ChoobException
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
