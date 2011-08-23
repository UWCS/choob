import java.util.Collections;
import java.util.List;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobBadSyntaxError;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;


class OutputFilterObject implements Comparable<OutputFilterObject>
{
	public OutputFilterObject(final String target, final String expression,
		final String owner)
	{
		this.target = target;
		this.converted = expression;
		this.owner = owner;
		this.id = 0;
	}

	public OutputFilterObject()
	{
		// Unhide
	}

	@Override
	public int compareTo(OutputFilterObject other)
	{
		return Integer.valueOf(id).compareTo(Integer.valueOf(other.id));
	}

	public int id;
	public String target;
	public String converted;
	public String owner;
}


public class OutputFilter
{
	private final Modules mods;
	private final IRCInterface irc;
	public OutputFilter(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public void commandAdd(Message mes) throws ChoobNoSuchCallException
	{

		final String[] params = mods.util.getParamArray(mes, 2);
		if (params.length < 3)
			throw new ChoobBadSyntaxError();

		mods.security.checkNickPerm(new ChoobPermission(
			"plugin.outputfilter.add." + params[1]), mes);

		final String output = doEval(params[2], "omg ponies",
			mes.getNick(), params[1]);

		mods.odb.save(new OutputFilterObject(params[1], params[2],
			mes.getNick()));

		irc.sendContextReply(mes, "Okay, sample output: " + output);
	}

	public void commandRemoveAll(Message mes)
	{
		final String[] params = mods.util.getParamArray(mes);

		mods.security.checkNickPerm(new ChoobPermission(
			"plugin.outputfilter.removeall." + params[1]), mes);

		for (String channel : params)
		{
			for (OutputFilterObject o : mods.odb.retrieve(
				OutputFilterObject.class, whereTarget(channel)))
			{
				mods.odb.delete(o);
			}
		}

		irc.sendContextReply(mes, "Okay!");
	}

	public void commandRemove(Message mes)
	{
		final String[] params = mods.util.getParamArray(mes, 2);
		if (params.length < 3)
			throw new ChoobBadSyntaxError();

		int item = Integer.parseInt(params[2]);

		mods.security.checkNickPerm(new ChoobPermission(
			"plugin.outputfilter.remove." + params[1]), mes);

		final List<OutputFilterObject> filters = mods.odb.retrieve(
			OutputFilterObject.class, whereTarget(params[1]));

		Collections.sort(filters);

		mods.odb.delete(filters.get(item));

		irc.sendContextReply(mes, "Okay!");
	}

	public void commandList(Message mes)
	{
		final String[] params = mods.util.getParamArray(mes);
		if (params.length < 2)
			throw new ChoobBadSyntaxError();

		String channel;
		int item = -1;
		boolean toolong = false;
		int j = 0;

		channel = params[1];
		if (params.length > 2)
			item = Integer.parseInt(params[2]);

		irc.sendContextReply(mes, "OutputFilters for " + channel + ":");

		final List<OutputFilterObject> filters = mods.odb.retrieve(
			OutputFilterObject.class, whereTarget(channel));

		Collections.sort(filters);

		if(item != -1)
		{
			irc.sendContextReply(mes, "[" + item + "] -> " +
				filters.get(item).converted);
		}
		else
		{
			for (OutputFilterObject o : filters)
			{
				if(!toolong && j > 3)
				{
					toolong = true;
					j = 1;
				}
				else if(!toolong)
				{
					irc.sendContextReply(mes, "[" + j + "] -> " +
						o.converted);
				}
				j++;
			}
			if(toolong)
			{
				irc.sendContextReply(mes, "    -> " + (j-1) +
					" more items..");
			}
		}
	}

	public String apiApply(final String target, final String nick,
		final String message)
	{
		String working = message;
		try
		{
			final List<OutputFilterObject> filters = mods.odb.retrieve(
				OutputFilterObject.class, whereTarget(target));

			Collections.sort(filters);

			for (OutputFilterObject a : filters)
			{
				String converted = a.converted;
				try
				{
					working = doEval(converted, working, nick, target);
				}
				catch (Exception e)
				{
					System.out.println("Not sedding " + converted + ":");
					e.printStackTrace();
				}
			}
			return working;
		}
		catch (Throwable t)
		{
			t.printStackTrace();
			return message;
		}
	}

	private String doEval(String expression, String stdin,
		final String nick, final String target)
		throws ChoobNoSuchCallException
	{
		System.out.println("Applying " + expression + " to " + stdin);
		return (String)mods.plugin.callAPI("Pipes", "Eval", expression,
			nick, target, stdin);
	}

	private String whereTarget(final String target)
	{
		return "WHERE target='" + mods.odb.escapeString(target) +
			"' ORDER BY RAND()";
	}
}
