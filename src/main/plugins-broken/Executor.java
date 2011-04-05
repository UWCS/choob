/** @author RLMW */

import interrupter.Interrupter;
import interrupter.InterruptingClassLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

public class Executor {
	
	private int interrupts = 10000;
	
	private final Modules mods;
	private final IRCInterface irc;

	public Executor(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}
	
	public String[] optionsGeneral = { "Interrupts" };
	public String[] optionsGeneralDefaults = { "10" };
	public String[] helpOptionInterrupts = {
		"Set the number of bytecode operations (sic) before code is interrupted",
	};

	public boolean optionCheckGeneralInterrupts(final String optionValue) {
		try {
			interrupts = Integer.parseInt(optionValue);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
	
	private int counter = 0;
	
	public void commandRun(final Message mes, final Modules mods,
			final IRCInterface irc) {
		final String msg = mods.util.getParamString(mes);
		
		try {
			final String output = generateAndRun(msg);
			System.out.println("FAIL out: "+output);
			irc.sendContextReply(mes, output);
		} catch (CompileException e) {
			irc.sendContextReply(mes, "You have failed at life:");
			irc.sendContextReply(mes, e.getErrors());
		} catch (Exception e) {
			irc.sendContextReply(mes, e.getMessage());
		}
	}

//  Faux won't allow us to have nice things
	public String apiRan(final String mes) {
		try {
			return generateAndRun(mes);
		} catch (Exception e) {
			return e.getMessage();
		}
	}

	public static void main(String[] args) {
		// test it
		try {
			System.out.println(new Executor(null,null).generateAndRun("1+2+System.currentTimeMillis()"));
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("some fail");
		}
	}

	private String generateAndRun(final String expression) throws Exception {
		String cls = null;
		int localCounter = 0;
		synchronized (this) {
			localCounter = counter++;
		}
		
		cls = "public class UniqueClass"+localCounter+
		" implements java.util.concurrent.Callable<String> { public String call() {return String.valueOf("
		+ expression + ");}}";
		final String result = compile("UniqueClass"+localCounter,cls,localCounter);
		if(mods != null) {
			mods.odb.save(new Execution(expression,result));
		}
		return result;
	}

	/**
	 * Compile a string and load into the current classpath
	 * 
	 * @param name
	 * @param src
	 * @param counter2 
	 * @throws IOException 
	 */
	private String compile(final String name, final String src, int localCounter) throws Exception {
		final String dir = System.getProperty("java.io.tmpdir");
		final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		final PipedReader pipe = new PipedReader();
		final BufferedReader in = new BufferedReader(pipe);
		final PipedWriter out = new PipedWriter(pipe);
		final CompilationTask task = compiler.getTask(out, null,
				null, Arrays.asList("-d",dir), null, Arrays.asList(new JavaSourceFromString(name,
						src)));
		
		if (task.call()) {
			out.close();
			final ClassLoader icl = new InterruptingClassLoader(localCounter,new URL[]{new URL("file://"+dir+"/")});
			Interrupter.setLimit(localCounter, interrupts);
			Class <?> cls = icl.loadClass("UniqueClass"+localCounter);
			Callable<String> f = (Callable<String>) cls.newInstance();
			return f.call();
		} else {
			// DO NOT READ BEYOND THIS POINT IT WAS WRITTEN BY THE DEVIL
			out.close();
			List<String> errors = new ArrayList<String>();
			String s;
			final int prefixLength = 120 + String.valueOf(localCounter).length();
			while((s=in.readLine())!=null) {
				if(s.startsWith("public class UniqueClass")) {
					s = s.substring(prefixLength);
					s = s.substring(0,s.length()-4);
					errors.add(s);
					s = in.readLine();
					s = s.substring(prefixLength);
				}
				errors.add(s);
			}
			throw new CompileException(errors);
		}
	}
	//-----------------------------------------------
	
	public void webList(final PrintWriter out, final String params, final String[] user) {
		out.println("HTTP/1.0 200 OK");
		out.println("Content-Type: text/html");
		out.println();
		out.println("<html><body>");
		try {
			int id = Integer.parseInt(params);
			final List<Execution> exec = mods.odb.retrieve(Execution.class, "WHERE id = "+id);
			if(exec.size() > 0) {
				final Execution res = exec.get(0);
				out.println(res.content);
				out.println("--------------------");
				out.println("Resulted in:");
				out.println("--------------------");
				out.println(res.result);
			} else {
				out.println("PEEEEEEEEEEEEEEEEEEEEEEEEEEEEDO");
			}
		} catch (NumberFormatException e) {
			out.println("Pass me a fucking integer");
		}
		out.println("</body></html>");
	}
	
}

class Execution
{
	public int id;

	public String content;
	public String result;

	public Execution()
	{
		// Unhide
	}

	public Execution(final String content, final String result)
	{
		this.content = content;
		this.result = result;
	}
}


class CompileException extends Exception {
	
	private final List<String> errors;
	
	public List<String> getErrors() {
		return errors;
	}

	public CompileException(List<String> errors) {
		this.errors = errors;
	}
}

/**
 * A file object used to represent source coming from a string.
 */
class JavaSourceFromString extends SimpleJavaFileObject {
	/**
	 * The source code of this "file".
	 */
	final String code;

	/**
	 * Constructs a new JavaSourceFromString.
	 * 
	 * @param name
	 *            the name of the compilation unit represented by this file
	 *            object
	 * @param code
	 *            the source code for the compilation unit represented by this
	 *            file object
	 */
	JavaSourceFromString(String name, String code) {
		super(URI.create("string:///" + name.replace('.', '/')
				+ Kind.SOURCE.extension), Kind.SOURCE);
		this.code = code;
	}

	@Override
	public CharSequence getCharContent(boolean ignoreEncodingErrors) {
		return code;
	}
}
