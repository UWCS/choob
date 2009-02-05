package uk.co.uwcs.choob.modules;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Some functions to help with time and date manipulation. */
public final class DateModule {
	/**
	 * Units of time used in these functions.
	 */
	public static enum TimeUnit {
		YEAR("y", 365l * 7 * 24 * 60 * 60 * 1000),
		MONTH("m", 31l * 7 * 24 * 60 * 60 * 1000),
		WEEK("w", 7 * 24 * 60 * 60 * 1000),
		DAY("d", 24 * 60 * 60 * 1000),
		HOUR("h", 60 * 60 * 1000),
		MINUTE("m", 60 * 1000),
		SECOND("s", 1000),
		MILLISECOND("ms", 1);

		private final String longToken;
		private final String shortToken;
		private final long duration;

		TimeUnit(final String shortToken, final long duration) {
			this.longToken = this.toString().toLowerCase();
			this.shortToken = shortToken;
			this.duration = duration;
		}

		public String quantity(final boolean useShortTokens, final long quantity) {
			if (useShortTokens) {
				return quantity + shortToken;
			}
			return quantity + " " + longToken + (quantity > 1 ? "s" : "");
		}

		public String lessThanOne(final boolean useShortTokens) {
			if (useShortTokens) {
				return "<1" + shortToken;
			}
			return "less than " + (this == HOUR ? "an" : "a") + " "
					+ longToken;
		}

		public long duration() {
			return duration;
		}
	}

	/**
	 * Helper, converts a long (ms) time to a Map.
	 */
	final static Map<TimeUnit, Long> getTimeUnitMap(long interval) {
		final Map<TimeUnit, Long> map = new EnumMap<TimeUnit, Long>(TimeUnit.class);

		for (final TimeUnit unit : EnumSet.allOf(TimeUnit.class)) {
			final long quantity = interval / unit.duration();
			map.put(unit, quantity);
			interval -= quantity * unit.duration();
		}

		return map;
	}

	/** Gives a minimal representation of the given time interval, ie 1w6d. */
	public final String timeMicroStamp(final long i) {
		return timeMicroStamp(i, 2);
	}

	/**
	 * Gives a minimal representation of the given time interval, with the
	 * specified (maximum) number of elements.
	 */
	public final String timeMicroStamp(final long i, final int granularity) {
		return timeStamp(i, true, granularity, TimeUnit.MILLISECOND);
	}

	/**
	 * Gives a long representation of the given time interval, ie. "1 week and 6
	 * days"
	 */
	public final String timeLongStamp(final long i) {
		return timeLongStamp(i, 2);
	}

	/**
	 * Gives a long representation of the given time interval, with the
	 * specified (maximum) number of elements.
	 */
	public final String timeLongStamp(final long i, final int granularity) {
		return timeStamp(i, false, granularity, TimeUnit.MILLISECOND);
	}

	/**
	 * General function for generating approximate string representations of
	 * time periods.
	 *
	 * @param interval
	 *            The time interval in question.
	 * @param shortTokens
	 *            Use the condensed form (1w6d) or generate full English (1 week
	 *            and 6 days).
	 * @param replyDetail
	 *            Maximum number of parts to return.
	 * @param minGranularity
	 *            Minimum level of output, i.e. passing 'days' here will cause
	 *            1w2d3h4m5s to only output 1w2d. Default is 'millisecond'.
	 */
	public final String timeStamp(final long interval,
			final boolean shortTokens, final int replyDetail,
			final TimeUnit minGranularity) {

		final Map<TimeUnit, Long> unitQuantity = getTimeUnitMap(interval);
		final Set<TimeUnit> usedUnits = EnumSet.noneOf(TimeUnit.class);

		int remainingDetail = replyDetail;

		/*
		 * Go through the map, discard empty or invalid parts until we have
		 * enough (replyDetail).
		 */
		for (final TimeUnit unit : TimeUnit.values()) {
			if (remainingDetail <= 0) {
				break;
			}
			if (unitQuantity.get(unit) != 0) {
				usedUnits.add(unit);
				remainingDetail--;
			}
			if (unit == minGranularity) {
				break;
			}
		}

		/*
		 * Take the desired parts of the map, and build a string.
		 */

		final String time;

		if (usedUnits.size() == 0) {
			// Special case: the period is less than the minGranularity.
			time = minGranularity.lessThanOne(shortTokens);
		} else {
			// Build a list of the result tokens.
			final List<String> result = new ArrayList<String>();
			for (final TimeUnit unit : usedUnits) {
				result.add(unit.quantity(shortTokens, unitQuantity.get(unit)));
			}

			final StringBuilder b = new StringBuilder();

			// Concatenate the result as a string.
			if (shortTokens) {
				for (final String token : result) {
					b.append(token);
				}
			} else {
				b.append(result.remove(0));
				if (result.size() > 0) {
					final String lastToken = result.remove(result.size() - 1);
					for (final String token : result) {
						b.append(", ");
						b.append(token);
					}
					b.append(" and ");
					b.append(lastToken);
				}
			}
			time = b.toString();
		}
		return time;
	}

	/** Prettyprint a date */
	public final static String absoluteDateFormat(final Date da)
	{
		// Some definitions.
			final SimpleDateFormat formatter = new SimpleDateFormat("EEEE d MMM h:mma");
			final SimpleDateFormat dayNameFormatter = new SimpleDateFormat("EEEE");
			final Calendar cda = new GregorianCalendar();
				cda.setTime(da);

			final Calendar cnow = new GregorianCalendar();
			final Date now = cnow.getTime();
			final Date midnight = new GregorianCalendar(cnow.get(Calendar.YEAR), cnow.get(Calendar.MONTH), cnow.get(Calendar.DAY_OF_MONTH), 24, 0, 0).getTime();
			final Date midnightTomorrow = new GregorianCalendar(cnow.get(Calendar.YEAR), cnow.get(Calendar.MONTH), cnow.get(Calendar.DAY_OF_MONTH), 48, 0, 0).getTime();
			final Date endOfThisWeek = new GregorianCalendar(cnow.get(Calendar.YEAR), cnow.get(Calendar.MONTH), cnow.get(Calendar.DAY_OF_MONTH) + 7, 0, 0, 0).getTime();
		// </definitions>

		if (da.compareTo(now) > 0) // It's in the future, we can cope with it.
		{
			if (da.compareTo(midnight) < 0) // It's before midnight tonight.
				return shortTime(cda) + " " +            // 9pm
					(cda.get(Calendar.HOUR_OF_DAY) < 18 ? "today" : "tonight");

			if (da.compareTo(midnightTomorrow) < 0) // It's before midnight tomorrow and not before midnight today, it's tomorrow.
				return shortTime(cda) +                  // 9pm
					" tomorrow " +                       // tomorrow
					futurePeriodOfDayString(cda);        // evening

			if (da.compareTo(endOfThisWeek) < 0) // It's not tomorrow, but it is some time when the week-day names alone mean something.
				return shortTime(cda) + " " +            // 9pm
					dayNameFormatter.format(da) + " " +  // Monday
					futurePeriodOfDayString(cda);        // evening

		}

		return formatter.format(da);
	}

	/** Convert a Calendar to "8pm", "7am", "7:30am" etc. */
	public final static String shortTime(final Calendar cda)
	{
		final SimpleDateFormat nomins = new SimpleDateFormat("ha");
		final SimpleDateFormat wimins = new SimpleDateFormat("h:mma");

		// Don't show the minutes if they're 0.
		if (cda.get(Calendar.MINUTE) != 0)
			return wimins.format(cda.getTime()).toLowerCase();

		return nomins.format(cda.getTime()).toLowerCase();
	}

	/** Work out if a calendar is in the morning, afternoon or evening. */
	public final static String futurePeriodOfDayString(final Calendar cda)
	{
		final int hour = cda.get(Calendar.HOUR_OF_DAY);

		if (hour < 12)
			return "morning";

		if (hour < 18)
			return "afternoon";

		return "evening";
	}
}
