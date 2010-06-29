import static org.joda.time.DateTimeFieldType.dayOfMonth;
import static org.joda.time.DateTimeFieldType.monthOfYear;
import static org.joda.time.DateTimeZone.getAvailableIDs;
import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.Hours;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.Minutes;
import org.joda.time.Months;
import org.joda.time.Partial;
import org.joda.time.ReadablePeriod;
import org.joda.time.Seconds;
import org.joda.time.Weeks;
import org.joda.time.Years;
import org.junit.Test;
import org.parboiled.BaseParser;
import org.parboiled.Parboiled;
import org.parboiled.ReportingParseRunner;
import org.parboiled.Rule;
import org.parboiled.errors.ErrorUtils;
import org.parboiled.support.ParsingResult;

import uk.co.uwcs.choob.modules.DateModule;
import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;

public class Timeleft
{
	final Modules mods;

	public String[] info()
	{
		return new String[] { "", "The Choob Team", "choob@uwcs.co.uk",
				"$Rev$$Date$" };
	}

	public Timeleft(final Modules mods)
	{
		this.mods = mods;
	}

	public String commandUntil(final String mes)
	{
		final String param = mes;
		final Date d;
		Object o = go(param);
		if (o instanceof DateTime)
			d = ((DateTime) o).toDate();
		else
			return "What?! " + String.valueOf(o);

		long diff = d.getTime() - new Date().getTime();
		return (DateModule.absoluteDateFormat(d)
				+ (diff <= 0 ? " was " : " is in ") + mods.date.timeLongStamp(diff, 3)
				+ (diff <= 0 ? " ago" : "") + ".");
	}

	public String commandRaw(final String msg) {
		return String.valueOf(go(msg));
	}

	static final DateTimeZone LONDON = DateTimeZone.forID("Europe/London");

	@Test public void testTimeAdvance() {
		assertEquals(new LocalTime(13, 38), go("1 minute after 13:37"));
		assertEquals(new LocalTime(13, 39), go("2 minutes after 13:37"));
		assertEquals(new LocalTime(13, 39, 23), go("2minutes after 13:37:23"));
		assertEquals(new LocalTime(13, 37, 30), go("7 seconds after 13:37:23"));
	}

	@Test public void testTimeReverse() {
		assertEquals(new LocalTime(13, 36), go("1 minute before 13:37"));
		assertEquals(new LocalTime(13, 35), go("2 minutes before 13:37"));
		assertEquals(new LocalTime(13, 35, 23), go("2minutes before 13:37:23"));
		assertEquals(new LocalTime(13, 37, 16), go("7 seconds before 13:37:23"));
	}

	@Test public void testDateTimeAdvance() {
		final DateTime expected = new DateTime(2001, 03, 27, 13, 21, 19, 0);

		assertEquals(
				expected,
				go("2 years after 27th march 1999 13:21:19"));
		assertEquals(
				expected,
				go("2 years after 13:21:19 27th march 1999"));
	}

	@Test public void testTimeFiddling() {
		assertEquals(new LocalTime(13, 37), go("1 minute before 1 hour after 12:38"));
	}

	@Test public void testTimeTimezoneChanges() {
		final String pref = (24 * 30) + " hours after 1 mar ";
		// toString() as two TimeZones with the same offset aren't equal
		assertEquals(new DateTime(2010,3,31,18,0,0,0,DateTimeZone.forOffsetHours(-6)).toString(),
				go(pref + "2010 17:00 MDT").toString());
		assertEquals(new DateTime(2005,3,31,17,0,0,0,DateTimeZone.forOffsetHours(-7)).toString(),
				go(pref + "2005 17:00 MDT").toString());
	}

	@Test public void testTimeTimezone() {
		assertEquals(new LocalTime().toDateTimeToday(LONDON)
				.withTime(17, 0, 0, 0),
				go("17:00 Europe/London in BST"));
	}

	@Test public void testAmPm() {
		assertEquals(new DateTime(2006, 1, 1, 17, 0, 0, 0), go("1 jan 2006 5:00pm"));
	}

	// technically full of race conditions
	@Test public void testNow() {
		assertEquals(new LocalTime().withMillisOfSecond(0), go("now"));
		assertEquals(new DateTime(LONDON).withMillisOfSecond(0), go("now in Europe/London"));
	}

	@Test public void testAt() {
		// Is this portable?  I doubt it.
		assertEquals(new DateTime(1970, 1, 1, 1, 0, 0, 1, LONDON), go("@1 in Europe/London"));
	}

	@Test public void testBFL() {
		assertEquals(TimeParser.BFL, go("start of bfl"));
	}

	@Test(expected=RuntimeException.class)
	public void testEndOfFail() {
		go("end of fred's penis");
	}

	Object go(final String input) {
		final TimeParser parser = Parboiled.createParser(TimeParser.class, this);
		final ParsingResult<?> pr = ReportingParseRunner.run(parser.Root(), input.toLowerCase());
		if (!pr.matched) {
			throw new RuntimeException(ErrorUtils.printParseErrors(pr.parseErrors, pr.inputBuffer));
		}
		return pr.parseTreeRoot.getValue();
	}
}


enum PeriodMapper {
	YEAR {
		@Override ReadablePeriod asPeriod(int i) {
			return Years.years(i);
		}
	},
	MONTH {
		@Override ReadablePeriod asPeriod(int i) {
			return Months.months(i);
		}
	},
	WEEK {
		@Override ReadablePeriod asPeriod(int i) {
			return Weeks.weeks(i);
		}
	},
	DAY {
		@Override ReadablePeriod asPeriod(int i) {
			return Days.days(i);
		}
	},
	HOUR {
		@Override ReadablePeriod asPeriod(int i) {
			return Hours.hours(i);
		}
	},
	MINUTE {
		@Override ReadablePeriod asPeriod(int i) {
			return Minutes.minutes(i);
		}
	},
	SECOND {
		@Override ReadablePeriod asPeriod(int i) {
			return Seconds.seconds(i);
		}
	};

	abstract ReadablePeriod asPeriod(int i);
}

enum Month {
	JANUARY, FEBRUARY, MARCH,
	APRIL, MAY, JUNE,
	JULY, AUGUST, SEPTEMBER,
	OCTOBER, NOVEMBER, DECEMBER;

	public static Month of(String s) {
		for (Month m : values())
			if (m.name().toLowerCase().startsWith(s.toLowerCase()))
				return m;
		throw new IllegalArgumentException("Not a month: " + s);
	}
}

class TimeParser extends BaseParser<Object> {

	static final Partial CHRISTMAS = new Partial().with(dayOfMonth(), 25).with(monthOfYear(), 12);
	static final DateTime BFL = new DateTime(2010, 6, 28, 9, 0, 0, 0, Timeleft.LONDON);
	public static final Map<String, DateTimeZone> SUPPORTABLE_TIME_ZONES = supportableTimezones();

	private final Timeleft plugin;

	TimeParser(Timeleft plugin) {
		this.plugin = plugin;
	}

	public Rule Root() {
		return Sequence(
				ZeroOrMore(Sequence(
					Integer().label("num"), Optional(Space()),
					PeriodUnit().label("per"), Space(),
					Direction().label("dir"), Space(),
					set(UP(set(addPeriods(
							DOWN((Integer)value(nodeByLabel("num"))),
							DOWN((String)value(nodeByLabel("per"))),
							DOWN((String)value(nodeByLabel("dir"))),
							value())))
					))).label("flap"),
				DateTime().label("time"),
				Optional(Sequence(Space(), "in", Space(), TimeZone().label("cozone"))),
				Eoi(),
				set(coerce(advance(
						value(nodeByLabel("flap")),
						value(nodeByLabel("time"))
						), (DateTimeZone)value(nodeByLabel("cozone")))));
	}

	/** @return passthrough or DateTime of dt is set. */
	protected Object coerce(Object datetime, DateTimeZone dt) {
		if (null == dt)
			return datetime;
		if (datetime instanceof DateTime)
			return ((DateTime) datetime).withZone(dt);
		if (datetime instanceof LocalTime)
			return ((LocalTime) datetime).toDateTimeToday(dt);
		if (datetime instanceof LocalDate)
			return ((LocalDate) datetime).toDateTimeAtCurrentTime(dt);
		throw new IllegalArgumentException("Can't timezoneise " + datetime);
	}

	public Rule TimeZone() {
		return Sequence(
				FirstOf(SUPPORTABLE_TIME_ZONES.keySet().toArray(new String[SUPPORTABLE_TIME_ZONES.size()])),
				set(SUPPORTABLE_TIME_ZONES.get(lastText())));
	}

	public Rule DateTime() {
		return FirstOf(
			Sequence(
				FirstOf(
					Sequence(
						LocalTime().label("tim"), Space(),
						Date().label("dat"),
						Optional(Sequence(Optional(Space()), TimeZone()).label("zone"))
					),
					Sequence(
						Optional(Sequence(Date().label("dat"), Space())),
						LocalTime().label("tim"),
						Optional(Sequence(Optional(Space()), TimeZone()).label("zone"))
					)
				),
				set(conglomerate(
					(LocalDate)value(nodeByLabel("dat")),
					(LocalTime)value(nodeByLabel("tim")),
					(DateTimeZone) value(nodeByLabel("zone"))
				))),
			Sequence(
				"@",
				Long(),
				set(new DateTime(Long.parseLong(lastText())))
			),
			Sequence(
				Sequence(FirstOf("start", "end"), set(lastText())).label("type"),
				Space(), "of", Space(),
				Sequence(OneOrMore(Any()), set(lastText())).label("info"),
				event(
					(String)value(nodeByLabel("type")),
					(String)value(nodeByLabel("info"))
				)
			));
	}

	protected boolean event(String startOrEnd, String key) {
		final Object dateRes;
		try {
			dateRes = plugin.mods.plugin.callAPI("Events", "dateOf", startOrEnd, key);
		} catch (ChoobNoSuchCallException e) {
			return false;
		}
		if (null == dateRes)
			return false;

		getContext().setNodeValue(dateRes);
		return true;
	}

	public Rule Long() {
		return OneOrMore(Digit());
	}

	/** @return LocalTime or DateTime. */
	protected Object conglomerate(LocalDate dat, LocalTime tim, DateTimeZone tz) {
		return dat != null ? dat.toDateTime(tim, tz) :
			tz == null ? tim : tim.toDateTimeToday(tz);
	}

	public Rule PeriodUnit() {
		return Sequence(
				FirstOf("year", "month", "week", "day", "hour", "minute", "second"),
				set(lastText()),
				Optional("s")
				);
	}

	public Rule Direction() {
		return Sequence(FirstOf("after", "before"), set(lastText()));
	}

	public Rule Integer() {
		return Sequence(OneOrMore(Digit()), set(Integer.parseInt(lastText())));
	}

	public Rule Space() {
		return OneOrMore(CharSet(" \t"));
	}

	protected Object advance(Object per, Object abs) {
		return plus(abs, (ReadablePeriod) per);
	}

	/** @return :t orig or orig->plus(ReadablePeriod). */
	protected Object addPeriods(Integer num, String per, String dir, Object orig) {
		final int mul;
		if ("after".equals(dir))
			mul = 1;
		else if ("before".equals(dir))
			mul = -1;
		else
			throw new IllegalArgumentException("dir can't be " + dir);

		final ReadablePeriod period = PeriodMapper.valueOf(per.toUpperCase()).asPeriod(mul * nn(num));
		if (null == orig)
			return period;
		return plus(orig, period);
	}

	protected Object plus(Object o, ReadablePeriod p) {
		if (o instanceof LocalTime)
			return ((LocalTime)o).plus(p);
		if (o instanceof LocalDate)
			return ((LocalDate)o).plus(p);
		if (o instanceof LocalDateTime)
			return ((LocalDateTime)o).plus(p);
		if (o instanceof DateTime)
			return ((DateTime)o).plus(p);
		if (o instanceof ReadablePeriod)
			return ((ReadablePeriod) o).toPeriod().plus(p);
		if (o instanceof Partial)
			return ((Partial) o).plus(p);
		throw new IllegalArgumentException("Can't plus " + o + (o != null ? " of type " + o.getClass() : ""));
	}

	public Rule LocalTime() {
		return FirstOf(
			Sequence(
				Time_HH_MM_SS(),
				Optional(Sequence(
					Optional(Space()),
					FirstOf("am", "pm"),
					set(lastText())
					)).label("meri"),
				set(convertToTime(
						(Integer) value(nodeByLabel("hours")),
						(Integer) value(nodeByLabel("minutes")),
						(Integer) value(nodeByLabel("seconds")),
						(String) value(nodeByLabel("meri"))
						)))
			, Sequence(
				"now",
				set(new LocalTime().withMillisOfSecond(0))
			)
		);

	}

	public Rule Date() {
		return Sequence(
			OneOrTwoDigits().label("day"), Optional(Ordinal()), DateSep(),
			Optional(Sequence("of", Space())),
			FirstOf(
				Sequence(TwoDigits(), set(Month.values()[Integer.valueOf(lastText())-1])),
				MonthWord()).label("month"), DateSep(),
			Integer().label("year"),
			set(buildDate(
				(Integer) value(nodeByLabel("day")),
				(Month) value(nodeByLabel("month")),
				(Integer) value(nodeByLabel("year"))
				))
		);
	}

	protected LocalDate buildDate(Integer day, Month month, Integer year) {
		return new LocalDate(year, month.ordinal() + 1, day);
	}

	public Rule DateSep() {
		return FirstOf("/", "-", Space());
	}

	public Rule MonthWord() {
		return Sequence(FirstOf(
				"january", "february", "march",
				"april", "may", "june",
				"july", "august", "september",
				"october", "november", "december",
				"jan", "feb", "mar",
				"apr", "may", "jun",
				"jul", "aug", "sep",
				"oct", "nov", "dec"), set(Month.of(lastText())));
	}

	public Rule Ordinal() {
		return FirstOf("st", "nd", "rd", "th");
	}

	/** hh:mm(:ss)? */
	public Rule Time_HH_MM_SS() {
		return Sequence(OneOrTwoDigits().label("hours"), ':', TwoDigits().label("minutes"),
				Optional(Sequence(':', TwoDigits().label("seconds"))));
	}

	public Rule OneOrTwoDigits() {
		return FirstOf(TwoDigits(), OneDigit());
	}

	public Rule OneDigit() {
		return Sequence(Digit(), set(Integer.parseInt(lastText())));
	}

	public Rule TwoDigits() {
		return Sequence(Sequence(Digit(), Digit()), set(Integer.parseInt(lastText())));
	}

	public Rule Digit() {
		return CharRange('0', '9');
	}

	protected LocalTime convertToTime(Integer hours, Integer minutes, Integer seconds, String ampm) {
		int h = nn(hours);
		if ("pm".equals(ampm) && h < 12)
			h += 12;
		return new LocalTime(h, nn(minutes), nn(seconds));
	}

	private static int nn(Integer i) {
		return i == null ? 0 : i;
	}

	private static Map<String, DateTimeZone> supportableTimezones() {
		final Map<String, DateTimeZone> m = new HashMap<String, DateTimeZone>();
		final Set<String> rejects = new HashSet<String>();
		final long now = new Date().getTime();

		for (String s : availableIds()) {
			final DateTimeZone tz = DateTimeZone.forID(s);
			final String sn = tz.getShortName(now).toLowerCase();
			m.put(s.toLowerCase(), tz);
			if (sn.matches("[+-]?\\d+:\\d+"))
				continue;
			final DateTimeZone ig = m.get(sn);
			final int off = tz.getOffset(now);
			if (null == ig)
				m.put(sn, tz);
			else
				if (ig.getOffset(now) != off)
					rejects.add(sn);
		}

		for (String s : rejects)
			m.remove(s);
		return m;
	}

	@SuppressWarnings("unchecked")
	private static Set<String> availableIds() {
		return getAvailableIDs();
	}
}

