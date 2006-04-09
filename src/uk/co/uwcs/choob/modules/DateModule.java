package uk.co.uwcs.choob.modules;

import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.plugins.*;
import uk.co.uwcs.choob.modules.*;
import java.sql.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;

public final class DateModule
{
	public static enum longtokens { week, day, hour, minute, second, millisecond }

	// Yes, this is incredibly lame. Enum -> String[] is really beyond me (without a for-loop):
	public final static String[] longtokensstring = new String[] { "week", "day", "hour", "minute", "second", "millisecond" };

	final static long[] getSt(long i)
	{
		final long w= (i / (7*24*60*60*1000)); i -= w*(7*24*60*60*1000);
		final long d= (i / (24*60*60*1000)); i -= d*(24*60*60*1000);
		final long h= (i / (60*60*1000)); i -= h*(60*60*1000);
		final long m= (i / (60*1000)); i -= m*(60*1000);
		final long s= (i / (1000)); i -= s*1000;
		final long ms=(i); i -=ms;
		final long st[]={w,d,h,m,s,ms};
		return st;
	}

	public final String timeMicroStamp(long i)
	{
		return timeMicroStamp(i, 2);
	}

	public final String timeMicroStamp(long i, int corse)
	{
		return timeStamp(i, true, corse, longtokens.millisecond);
	}

	public final String timeLongStamp(long i)
	{
		return timeLongStamp(i, 2);
	}

	public final String timeLongStamp(long i, int corse)
	{
		return timeStamp(i, false, corse, longtokens.millisecond);
	}

	public final String timeStamp(final long thing, final boolean shortTokens, int replyDetail, final longtokens minCorse)
	{
		final StringBuilder t = new StringBuilder();
		final long st[]=getSt(thing);
		final String tokenName[];

		if (shortTokens)
			tokenName = new String[] {"w","d","h","m","s","ms"};
		else
			tokenName = longtokensstring;

		ArrayList<longtokens> useWhich = new ArrayList<longtokens>();

		for (int j = 0; j<st.length; j++)
			if (j-1 == minCorse.ordinal())
				break;
			else
				if (st[j] != 0)
					if (replyDetail-- <= 0)
						break;
					else
						useWhich.add(longtokens.values()[j]);

		longtokens[] use = useWhich.toArray(new longtokens[] {});

		if (use.length == 0)
			return t.append(shortTokens ? "<1" : "less than " + (minCorse == longtokens.hour ? "an " : "a "))
				.append(tokenName[minCorse.ordinal()]).toString();
		else
			for (int i = 0; i<use.length; i++)
			{
				final int j = use[i].ordinal();
				if (shortTokens)
					// 4h
					t.append(st[j]).append(tokenName[j]);
				else
					// '4' + ' ' +
					t.append(st[j]).append(" ")
					// 'hour' + 's' +
					.append(tokenName[j]).append(st[j]!=1 ? "s" : "")
					.append(i==use.length-1 ? "" : (i!=use.length-2 ? ", " : " and "));
			}

		return t.toString();
	}
}