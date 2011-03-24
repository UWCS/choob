import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.IRCInterface;

public class SVN
{
	public String[] info()
	{
		return new String[] {
			"Watches the Choob SVN for new versions.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private final Modules mods;
	private final IRCInterface irc;
	public SVN( final Modules mods, final IRCInterface irc )
	{
		mods.interval.callBack( null, 100 );
		this.irc = irc;
		this.mods = mods;
	}

	public synchronized void interval( final Object parameter ) throws ChoobException
	{
		URL svnURL;
		try
		{
			svnURL = new URL("http://svn.uwcs.co.uk/repos/choob/");
		}
		catch (final MalformedURLException e)
		{
			throw new ChoobException("Constant URL in SVN plugin broken...");
		} // squelch

		String revString;

		try
		{
			final BufferedReader svnPage = new BufferedReader( new InputStreamReader( svnURL.openStream() ) );
			try
			{
				revString = svnPage.readLine();
			}
			finally
			{
				svnPage.close();
			}
		}
		catch (final IOException e)
		{
			throw new ChoobException("IO Exception reading version number.", e);
		}

		final String newRev = revString.substring( revString.indexOf("tle>")+4, revString.indexOf(":") );

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
