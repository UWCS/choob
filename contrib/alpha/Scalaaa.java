/** @author rlmw */

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.AllPermission;

import scala.tools.nsc.Interpreter;
import scala.tools.nsc.Settings;
import scala.tools.nsc.InterpreterResults.Result;
import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.plugins.RequiresPermission;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

/**
 * @author rlmw
 * Based on scala.console - code ideas ported to Java
 */
@RequiresPermission(value=AllPermission.class) //,permission="",action="")
public class Scalaaa {
	
	final Interpreter interp;
	final Settings settings;
	final StringWriter writer;
	
	public Scalaaa() {
		//interp = null;
		//settings = null;
		//new File("src/").
		writer = new StringWriter();
		settings = new Settings(null);
		settings.usejavacp().tryToSetFromPropertyValue("true");
		interp = new Interpreter(settings,new PrintWriter(writer));
	}
	
	public String[] info()
	{
		return new String[] {
			"SCALAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
			"mulletron",
			"ALPHA ALPHA",
			"<3",
		};
	}

	public String[] helpTopics = { "Using" };

	public String[] helpUsing = {
		  "!scalaaa.run <code> where <code> is a valid scala expression",
		  "Note takes a while to execute on first attempt",
	};

	/**
	 * Returns the user's last known location
	 */
	public void commandRun(final Message mes, final Modules mods,
			final IRCInterface irc) throws Exception {
			final String run = run(mods.util.getParamString(mes));
			//System.out.println("run = " + run);
			irc.sendContextReply(mes, run);
	}

	private String run(String paramString) {
		System.out.println(paramString);
		try {
			final Result interpret = interp.interpret(paramString);
			if(interpret.toString().equalsIgnoreCase("incomplete")) {
				return "Incomplete statement - please try again";
			} else {
				return writer.toString();
			}
		} finally {
			final StringBuffer buffer = writer.getBuffer();
		    buffer.delete(0, buffer.length());
		}
	}
}

