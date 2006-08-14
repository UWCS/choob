package uk.co.uwcs.choob.modules;

import java.util.*;

/** Some functions to help with time and date manipulation */
public final class DateModule
{
	/** enum representation of the levels of output that these functions produce */
	public static enum longtokens { week, day, hour, minute, second, millisecond }

	// Yes, this is incredibly lame. Enum -> String[] is really beyond me (without a for-loop):
	public final static String[] longtokensstring = new String[] { "week", "day", "hour", "minute", "second", "millisecond" };

	/** Helper, converts a long (ms since epoch) time to an array of weeks (index 0), days (index 1), hours, etc. */
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

	/** Gives a minimal representation of the given time interval, ie 1w6d. */
	public final String timeMicroStamp(long i)
	{
		return timeMicroStamp(i, 2);
	}

	/** Gives a minimal representation of the given time interval, with the specified (maximum) number of elements. */
	public final String timeMicroStamp(long i, int granularity)
	{
		return timeStamp(i, true, granularity, longtokens.millisecond);
	}

	/** Gives a long representation of the given time interval, ie. "1 week and 6 days" */
	public final String timeLongStamp(long i)
	{
		return timeLongStamp(i, 2);
	}

	/** Gives a long representation of the given time interval, with the specified (maximum) number of elements. */
	public final String timeLongStamp(long i, int granularity)
	{
		return timeStamp(i, false, granularity, longtokens.millisecond);
	}

	/** General function for generating approximate string representations of time periods.
	 * @param thing The time interval in question.
	 * @param shortTokens Use the condensed form (1w6d) or generate full English (1 week and 6 days).
	 * @param replyDetail Maximum number of parts to return.
	 * @param minGranularity Minimum level of output, ie. passing 'days' here will cause 1w2d3h4m5s to only output 1w2d. Default is 'millisecond'.
	 */
	public final String timeStamp(final long thing, final boolean shortTokens, int replyDetail, final longtokens minGranularity)
	{
		final StringBuilder t = new StringBuilder();
		final long st[]=getSt(thing);
		final String tokenName[];

		// Decide which tokens we're going to be using.
		if (shortTokens)
			tokenName = new String[] {"w","d","h","m","s","ms"};
		else
			tokenName = longtokensstring;

		// Work out what we're going to output.
		ArrayList<longtokens> useWhich = new ArrayList<longtokens>();

		// Go through the "st", discard empty or invalid parts until we have enough (replyDetail).
		for (int j = 0; j<st.length; j++)
			if (j-1 == minGranularity.ordinal())
				break;
			else
				if (st[j] != 0)
					if (replyDetail-- <= 0)
						break;
					else
						useWhich.add(longtokens.values()[j]);

		longtokens[] use = useWhich.toArray(new longtokens[] {});

		// Special case, if we didn't decide to use anything, the period is less than the minGranuality, say so:
		if (use.length == 0)
			return t.append(shortTokens ? "<1" : "less than " + (minGranularity == longtokens.hour ? "an " : "a "))
				.append(tokenName[minGranularity.ordinal()]).toString();
		else
			for (int i = 0; i<use.length; i++)
			{
				// Otherwise, go through each that we decided to use, and append it (and any padding required) to the return value.
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