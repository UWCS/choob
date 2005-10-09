package org.uwcs.choob.support;

import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;
import java.util.*;
import java.net.*;
import java.io.*;
import java.util.regex.*;

public class GetContentsCached
{
	//String variants require exceptions, throwing is irritating and catching makes no sense.
	
	//public GetContentsCached(String url) { this.url=new URL(url); } 
	//public GetContentsCached(String url, int mintime) { this.url=new URL(url); this.mintime=mintime*1000; }
	public GetContentsCached(URL url) { this.url=url; } 
	public GetContentsCached(URL url, int mintime) { this.url=url; this.mintime=mintime*1000; }
	
	URL url;
	long lastaccess;
	long mintime=5*60*1000; // <-- 5 mins in ms.
	protected String contents=null;
	
	public void updateIfNeeded() throws IOException
	{
		long now=(new Date()).getTime();
		if (contents != null && (now - lastaccess) < mintime)
			return;

		URLConnection site		= url.openConnection();
		InputStream is			= site.getInputStream();
		InputStreamReader isr	= new InputStreamReader(is);
		BufferedReader br		= new BufferedReader(isr);
		String l, ls="";
		
		while ((l=br.readLine())!=null)
			ls+=l+"\n";
		contents=ls;
		lastaccess=now;	
	}
	
	public String getContents() throws IOException
	{
		updateIfNeeded();
		System.out.println(contents);
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
