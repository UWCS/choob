package uk.co.uwcs.choob.modules;

import java.util.*;

/** Some functions to help with time and date manipulation. */
public final class DateModule {
	/**
	 * Units of time used in these functions.
	 */
	private static enum TimeUnit {
		WEEK("w", 7 * 24 * 60 * 60 * 1000),
		DAY("d", 24 * 60 * 60 * 1000),
		HOUR("h", 60 * 60 * 1000),
		MINUTE("m", 60 * 1000),
		SECOND("s", 1000),
		MILLISECOND("ms", 1);
		
		private final String longToken;
		private final String shortToken;
		private final int duration;

		TimeUnit(final String shortToken, final int duration) {
			this.longToken = this.toString().toLowerCase();
			this.shortToken = shortToken;
			this.duration = duration;
		}

		public String quantity(final boolean useShortTokens, final long quantity) {
			if (useShortTokens) {
				return quantity + shortToken;
			} else {
				return quantity + " " + longToken + (quantity > 1 ? "s" : "");
			}
		}

		public String lessThanOne(final boolean useShortTokens) {
			if (useShortTokens) {
				return "<1" + shortToken;
			} else {
				return "less than " + ((this == HOUR) ? "an" : "a") + " "
						+ longToken;
			}
		}

		public int duration() {
			return duration;
		}
	}

	/**
	 * Helper, converts a long (ms) time to a Map.
	 */	
	final static Map<TimeUnit, Long> getTimeUnitMap(long interval) {
		Map<TimeUnit, Long> map = new EnumMap<TimeUnit, Long>(TimeUnit.class);
		
		for (TimeUnit unit : EnumSet.allOf(TimeUnit.class)) {
			final long quantity = (interval / unit.duration());
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
		Set<TimeUnit> usedUnits = EnumSet.noneOf(TimeUnit.class);

		int remainingDetail = replyDetail;

		/*
		 * Go through the map, discard empty or invalid parts until we have
		 * enough (replyDetail).
		 */
		for (TimeUnit unit : TimeUnit.values()) {
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
			List<String> result = new ArrayList<String>();
			for (TimeUnit unit : usedUnits) {
				result.add(unit.quantity(shortTokens, unitQuantity.get(unit)));
			}

			final StringBuilder b = new StringBuilder();
			
			// Concatenate the result as a string.
			if (shortTokens) {
				for (String token : result) {
					b.append(token);
				}
			} else {
				b.append(result.remove(0));
				if (result.size() > 0) {
					String lastToken = result.remove(result.size() - 1);
					for (String token : result) {
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
}
