package uk.co.uwcs.choob.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

public final class GetContentsCached
{
	public static int DEFAULT_TIMEOUT=5*60*1000;  // <-- 5 mins in ms.

	public GetContentsCached(final URL url) { this.url=url; }
	public GetContentsCached(final URL url, final long mintime) { this.url=url; setTimeout(mintime); }

	URL url;
	long lastaccess;
	long mintime=DEFAULT_TIMEOUT;
	protected String contents=null;

	public boolean expired()
	{
		return lastaccess+mintime<new Date().getTime();
	}

	public long getTimeout()
	{
		return mintime;
	}

	public void setTimeout(final long timeout)
	{
		mintime=timeout;
		updateNextCheck();
	}

	public void updateIfNeeded() throws IOException
	{
		final long now=new Date().getTime();
		if (contents != null && now - lastaccess < mintime)
			return;

		final URLConnection site		= url.openConnection();
		site.setReadTimeout(15000);
		site.setConnectTimeout(15000);
		site.setRequestProperty("User-agent", "Opera/8.51 (X11; Linux x86_64; U; en)");
		final InputStream is			= site.getInputStream();
		final InputStreamReader isr	= new InputStreamReader(is);
		final BufferedReader br		= new BufferedReader(isr);
		String l;
		final StringBuilder ls=new StringBuilder();

		while ((l=br.readLine())!=null)
			ls.append(l).append("\n");

		contents=ls.toString();

		lastaccess=now;
	}

	public void updateNextCheck()
	{
		lastaccess=new Date().getTime()-mintime;
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
		catch (final IOException e)
		{
			return null;
		}
	}
}
