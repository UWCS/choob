import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

abstract class SimpleDate
{
	protected SimpleDate other;

	SimpleDate()
	{
		other = new SimpleDate()
		{
			final Calendar nao = Calendar.getInstance();

			@Override
			public long getDay()
			{
				return nao.get(Calendar.DAY_OF_MONTH);
			}

			@Override
			public long getHour()
			{
				return nao.get(Calendar.HOUR_OF_DAY);
			}

			@Override
			public long getMinute()
			{
				return nao.get(Calendar.MINUTE);
			}

			@Override
			public long getMonth()
			{
				return nao.get(Calendar.MONTH);
			}

			@Override
			public long getSecond()
			{
				return nao.get(Calendar.SECOND);
			}

			@Override
			public long getYear()
			{
				return nao.get(Calendar.YEAR);
			}

		};
	}

	SimpleDate(final SimpleDate other)
	{
		this.other = other;
	}

	public abstract long getYear();

	public abstract long getMonth();

	public abstract long getDay();

	public abstract long getHour();

	public abstract long getMinute();

	public abstract long getSecond();
}

interface DateMutate
{
	SimpleDate mutate(SimpleDate d);
}

public class Timeleft
{
	public String[] info()
	{
		return new String[] { "", "The Choob Team", "choob@uwcs.co.uk",
				"$Rev$$Date$" };
	}

	private final Modules mods;
	private final IRCInterface irc;

	public Timeleft(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	DateFormat dateformat[] = { new SimpleDateFormat() };

	public void commandTo(final Message mes)
	{
		final Date dat = dateFromString(mes.getMessage());
		irc.sendContextReply(mes, dat.toString());
	}

	private static Date dateFromString(final String s)
	{
		final Date d = new Date();
		return d;
	}
}
