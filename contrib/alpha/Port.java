/** @author Faux */

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.regex.Matcher;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

public class Port
{
	Modules mods;
	IRCInterface irc;

	private static URL iana_numbers;

	HashMap<String, String>ports;

	public Port(Modules mods, IRCInterface irc) throws ChoobException, IOException
	{
		try
		{
			iana_numbers=new URL("http://www.iana.org/assignments/port-numbers");
		}
		catch (MalformedURLException e)
		{
			throw new ChoobException("Error in constant data", e);
		}

		this.mods=mods;
		this.irc=irc;
		updateSources();
	}

	public void commandRebuild(Message mes)
	{
		try
		{
			updateSources();
			irc.sendContextReply(mes, "Done!");
		}
		catch (IOException e)
		{
			irc.sendContextReply(mes, "Couldn't updated. " + e);
		}
	}

	public void commandToNumber(Message mes)
	{
		String parm=mods.util.getParamString(mes).trim();

		if (!ports.containsKey(parm))
			irc.sendContextReply(mes, "Not found.");
		else
			irc.sendContextReply(mes, ports.get(parm));

	}

	private void updateSources() throws IOException
	{
		ports=new HashMap<String, String>();
		Matcher ma=mods.scrape.getMatcher(iana_numbers, (long)60*60*1000, "(.{17}) +([0-9]+)/(?:(?:tcp)|(?:udp)) +(.*)");
		while (ma.find())
		{
			ports.put(ma.group(1).trim(), ma.group(2) + " (" + ma.group(3) + ")");
		}
	}
}