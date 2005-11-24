package uk.co.uwcs.choob.support;

import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.net.*;
import java.io.*;
import java.util.regex.*;

public final class GetContentsCached
{
	public static int DEFAULT_TIMEOUT=5*60*1000;  // <-- 5 mins in ms.

	public GetContentsCached(URL url) { this.url=url; }
	public GetContentsCached(URL url, long mintime) { this.url=url; setTimeout(mintime); }

	URL url;
	long lastaccess;
	long mintime=DEFAULT_TIMEOUT;
	protected String contents=null;

	public boolean expired()
	{
		return lastaccess+mintime<(new Date()).getTime();
	}

	public long getTimeout()
	{
		return mintime;
	}

	public void setTimeout(long timeout)
	{
		mintime=timeout;
		updateNextCheck();
	}

	public void updateIfNeeded() throws IOException
	{
		long now=(new Date()).getTime();
		if (contents != null && (now - lastaccess) < mintime)
			return;

		URLConnection site		= url.openConnection();
		site.setRequestProperty("User-agent", "Choob!");
		InputStream is			= site.getInputStream();
		InputStreamReader isr	= new InputStreamReader(is);
		BufferedReader br		= new BufferedReader(isr);
		String l;
		StringBuilder ls=new StringBuilder();

		while ((l=br.readLine())!=null)
			ls.append(l).append("\n");

		contents=ls.toString();

		lastaccess=now;
	}

	public void updateNextCheck()
	{
		lastaccess=(new Date()).getTime()-mintime;
	}

	public String getContents() throws IOException
	{
		updateIfNeeded();
		return contents;
	}

	public String getContentsNull()
	{
		try
		{
			return getContents();
		}
		catch (IOException e)
		{
			return null;
		}
	}
}
