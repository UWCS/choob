import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.jawk.Awk;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

/**
 * A wrapper for Jawk as a choob plugin
 * This plugin may break System.out/System.err
 * @author rlmw
 */
public class Jawk {

	public String simpleEval(final String ircArgs) {
		final int n = ircArgs.indexOf('\\');
		final String command = (n == -1)?ircArgs.substring(0):ircArgs.substring(0, n);
		final String input = (n == -1)?"":ircArgs.substring(n+1, ircArgs.length()).replace('\\', '\n');
		
		final String[] args = { command, };
		
		ByteArrayOutputStream err = null;
		try {
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			err = new ByteArrayOutputStream();
			
			new Awk(args, new ByteArrayInputStream(input.getBytes()),
					new PrintStream(out, true), new PrintStream(err, true));
			return out.toString();
		} catch (final Exception e) {
			e.printStackTrace();
			return err.toString()+" ... " + e.getMessage();
		}
	}
	
	public void commandEval(final Message mes, final Modules mods,
			final IRCInterface irc) {
		final String resp = simpleEval(mods.util.getParamString(mes));
		for (String line:resp.split("\\\n"))
			irc.sendContextReply(mes, line);
	}

}