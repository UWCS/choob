import java.lang.reflect.InvocationTargetException;
import java.util.List;

import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;


public class Pipes
{
	private final Modules mods;
	private final IRCInterface irc;

	public Pipes(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}


	public void /* NOT a simple command */ commandPipes(final Message mes)
	{
		List<String> scs = mods.plugin.getSimpleCommands();
		StringBuilder sb = new StringBuilder();
		for (String s : scs)
			sb.append(s).append(", ");
		irc.sendContextReply(mes, sb.toString());
	}

	public void /* NOT a simple command */ commandExec(final Message mes) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException
	{
		String [] todo = mods.util.getParamString(mes).split("\\|");
		String running = "";
		for (String t : todo)
			running = mods.plugin.callSimpleCommand(t, running);
		irc.sendContextReply(mes, running);
	}

}
