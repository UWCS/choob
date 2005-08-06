import org.uwcs.choob.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.modules.*;
import java.net.*;
import java.io.*;

class SVN
{
	public void create( Modules mods )
	{
		mods.interval.callBack( this, null, new Date( (new Date().getTime())+1000 ) );
	}

	public void interval( Object parameter, Modules mods, IRCInterface irc )
	{
		URL svnURL = new URL("http://svn.uwcs.co.uk/repos/choob/");

		BufferedReader svnPage = new BufferedReader( new InputStreamReader( svnURL.openStream() ) );

		String revString = svnPage.readLine();

		String newRev = revString.substring( revString.indexOf("tle>")+4, revString.indexOf(":") );

		if( parameter != null )
		{
			if( newRev.compareTo( (String)parameter ) != 0 )
			{
				irc.sendMessage("#bots", newRev + " now available in Subversion!");
			}
		}

		mods.interval.callBack( this, newRev, new Date( (new Date().getTime())+10000 ) );		
	}
}
