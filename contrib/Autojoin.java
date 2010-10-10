import static com.google.common.collect.ImmutableList.copyOf;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static uk.co.uwcs.choob.modules.UtilModule.hrList;

import java.util.List;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.plugins.RequiresPermission;
import uk.co.uwcs.choob.support.ChoobBadSyntaxError;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;

class ChannelObj {
	public int id;
	public String name;
}

/**
 * Channels to join are in the database.  The bot calls us during startup.
 */

@RequiresPermission(value=ChoobPermission.class, permission="state.join.*", action="")
class Autojoin
{
	private static final int DELAY_MS = 1000;
	private static final int FAST_JOIN = 10;

	private final Modules mods;
	private final IRCInterface irc;
	public Autojoin(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public List<String> apiList()
	{
		return copyOf(transform(mods.odb.retrieve(ChannelObj.class, ""),
			new Function<ChannelObj, String>()
			{
				@Override public String apply(ChannelObj chan)
				{
					return chan.name;
				}
		}));
	}

	public Void apiJoin() throws InterruptedException
	{
		int done = 0;
		for (String chan : apiList()) {
			irc.join(chan);
			if (done++ > FAST_JOIN)
				delayIfBotWont();
		}
		return null;
	}

	private void delayIfBotWont() throws InterruptedException
	{
		if (0 == irc.getMessageDelay())
			Thread.sleep(DELAY_MS);
	}

	public String[] helpCommandList = {
			"List automatically joined channels.",
			"[<filter>]",
			"<filter> optionally filter for this regex"
		};
	public String commandList(String args) {
		final Pattern p = Pattern.compile(args);
		final ImmutableList<String> channers = copyOf(filter(apiList(),
			new Predicate<String>()
			{
				@Override public boolean apply(String chan)
				{
					return p.matcher(chan).find();
				}
		}));

		return channers.isEmpty()
			? "Not currently autojoining anywhere" + (args.isEmpty() ? "." : " matching /" + args + "/.")
			: "Currently autojoining " + hrList(channers) + ".";
	}

	public String[] helpCommandAdd = {
			"Add a channel to automatically join on start.",
			"<channel>",
			"<channel> name"
		};
	public void commandAdd(final Message mes)
	{
		final String chan = getCleanedParam(mes);
		if (null == chan)
			return;

		final List<ChannelObj> ret = getObjs(chan);

		if (!ret.isEmpty()) {
			irc.sendContextReply(mes, ret.get(0).name + " already in autojoin.");
			return ;
		}

		final ChannelObj next = new ChannelObj();
		next.name = chan;
		mods.odb.save(next);

		irc.sendContextReply(mes, "Added.");
	}

	public String[] helpCommandRemove = {
			"Stop a channel being automatically joined on start.",
			"<channel>",
			"<channel> name"
		};
	public void commandRemove(final Message mes)
	{
		final String chan = getCleanedParam(mes);
		if (null == chan)
			return;

		final List<ChannelObj> ret = getObjs(chan);

		if (ret.isEmpty()) {
			irc.sendContextReply(mes, chan + " not alread in autojoin.");
			return;
		}

		mods.odb.delete(ret.get(0));

		irc.sendContextReply(mes, "Removed.");
	}

	/** @return null on permission error */
	private String getCleanedParam(final Message mes)
	{
		if (!mods.security.hasNickPerm(new ChoobPermission("state.join"), mes)) {
			irc.sendContextReply(mes, "You don't have permission!");
			return null;
		}

		String s = mods.util.getParamString(mes);
		if (s.contains(" ") || s.contains("\""))
			throw new ChoobBadSyntaxError();

		return s.toLowerCase();
	}

	private List<ChannelObj> getObjs(final String chan)
	{
		return mods.odb.retrieve(ChannelObj.class,
				"WHERE name=\"" + mods.odb.escapeString(chan) + "\"");
	}
}
