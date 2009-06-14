import java.util.List;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobBadSyntaxError;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.ChoobPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;


class OutputFilterObject
{
	public OutputFilterObject(final String target, final String expression, final String owner)
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

		mods.security.checkNickPerm(new ChoobPermission("plugin.outputfilter.add." + params[1]), mes);

		final String output = doEval(params[2], "omg ponies", mes.getNick(), params[1]);
		mods.odb.save(new OutputFilterObject(params[1], params[2], mes.getNick()));
		irc.sendContextReply(mes, "Okay, sample output: " + output);
	}

	public void commandRemoveAll(Message mes)
	{
		final String[] params = mods.util.getParamArray(mes);

		mods.security.checkNickPerm(new ChoobPermission("plugin.outputfilter.removeall." + params[1]), mes);

		for (String channel : params)
			for (OutputFilterObject o : mods.odb.retrieve(OutputFilterObject.class, whereTarget(channel)))
				mods.odb.delete(o);
		irc.sendContextReply(mes, "Okay!");
	}

	public String apiApply(final String target, final String nick, final String message)
	{
		String working = message;
		try
		{
			final List<OutputFilterObject> ret = mods.odb.retrieve(OutputFilterObject.class, whereTarget(target));

			for (OutputFilterObject a : ret)
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

	private String doEval(String expression, String stdin, final String nick,
			final String target) throws ChoobNoSuchCallException
	{
		System.out.println("Applying " + expression + " to " + stdin);
		return (String) mods.plugin.callAPI("Pipes", "Eval", expression, nick, target, stdin);
	}

	private String whereTarget(final String target)
	{
		return "WHERE target='" + mods.odb.escapeString(target) + "' ORDER BY RAND()";
	}
}
