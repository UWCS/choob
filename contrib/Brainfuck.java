import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;

/**
 * Brainfuck interpreter.
 *
 * This plugin implements a simple interpreter for the minimalistic Turing-complete
 * programming language, Brainfuck.
 *
 * All Brainfuck op-codes are supported except "," (input character to memory address
 * pointed to by the pointer) as it does not make sense in an IRC context. The memory
 * size is 30,000 in accordance with the original Brainfuck specification. More
 * informationi about Brainfuck, a quick tutorial as well as many external references
 * may be found on Wikipedia[0].
 *
 * The plugin has two artificial limits in an attempt to prevent denial-of-service
 * issues with other parts of the bot. Firstly, the total number of instructions the
 * interpreter will execute can be specified with the 'MAX_INSTRUCTIONS' constant.
 * This prevents (possibly accidental) "infinite" loops such as "+[]". If the
 * maximum number is reached, an error message is displayed. Secondly, the maximum
 * size of the interpreter's output can be specified with the 'MAX_OUTPUT' constant,
 * pre-empting various issues related to the bot generating large amounts of output.
 *
 * [0] http://en.wikipedia.org/wiki/Brainfuck
 *
 * @author lamby
 */

public class Brainfuck
{
	private static int MAX_INSTRUCTIONS = 9000;
	private static int MAX_OUTPUT = 70;

	private IRCInterface irc;
	private Modules mods;

	public Brainfuck(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public String[] helpCommandCalc = {
		"Evaluates a Brainfuck program.",
		"<expr>",
		"<expr> is a Brainfuck program"
	};

	public void commandEval( Message mes )
	{
		String reply;

		try
		{
			reply = eval(mods.util.getParamString(mes));

			if (reply.length() == 0)
			{
				reply = "(no output)";
			}
		}
		catch (InstructionCountExceededException e)
		{
			reply = e.getMessage() + " (halted after " + MAX_INSTRUCTIONS + " instructions at offset " + e.getOffset() + ")";
		}
		catch (OutputLengthExceededException e)
		{
			reply = e.getMessage() + " (halted after " + MAX_OUTPUT + " chars of output at offset " + e.getOffset() + ")";
		}
		catch (BrainfuckException e)
		{
			reply = "Parse error at offset " + e.getOffset() + ": "	+ e.getMessage();
		}

		irc.sendContextReply(mes, reply);
	}

	public String eval(String expr) throws BrainfuckException
	{
		BrainfuckInterpreter interp = new BrainfuckInterpreter(expr, MAX_INSTRUCTIONS, MAX_OUTPUT);
		return interp.eval();
	}
}

class BrainfuckInterpreter
{
	private byte[] mem = new byte[30000];
	private int pc = 0;
	private int ptr = 0; 
	private int count = 0;

	private int max_output;
	private int max_instructions;
	private String expr;

	public BrainfuckInterpreter(String expr, int max_instructions, int max_output)
	{
		this.expr = expr;
		this.max_instructions = max_instructions;
		this.max_output = max_output;
	}

	public String eval() throws BrainfuckException
	{
		StringBuilder output = new StringBuilder();
		Stack<Integer> pc_stack = new Stack();

		while (pc < expr.length())
		{
			++count;

			if (count == max_instructions)
			{
				throw new InstructionCountExceededException(output.toString(), pc);
			}

			switch (expr.charAt(pc))
			{
				case '>':
					++ptr;
					break;
				case '<':
					--ptr;
					break;
				case '+':
					++mem[ptr];
					break;
				case '-':
					--mem[ptr];
					break;
				case '.':
					output.append((char) (mem[ptr] & 0xFF));

					if (output.length() == max_output)
					{
						throw new OutputLengthExceededException(output.toString(), pc);
					}
					break;
				case '[':
					pc_stack.push(pc);
					break;
				case ']':
					try
					{
						if (mem[ptr] == 0)
						{
							pc_stack.pop();
						}
						else
						{
							pc = pc_stack.peek();
						}
					}
					catch (EmptyStackException e)
					{
						throw new ParseErrorException(pc);
					}
					break;
				default:
					break;
			}
			++pc;
		}

		if (!pc_stack.isEmpty())
		{
			throw new ParseErrorException(pc);
		}

		return output.toString();
	}
}

class BrainfuckException extends Exception
{
	int offset;

	public BrainfuckException(String message, int offset)
	{
		super(message);
		this.offset = offset;	
	}

	public int getOffset()
	{
		return offset;
	}
} 

class ParseErrorException extends BrainfuckException
{
	public ParseErrorException(int offset)
	{
		super("Mismatched bracket", offset);
	}
}

class InstructionCountExceededException extends BrainfuckException
{
	public InstructionCountExceededException(String output, int offset)
	{
		super(output, offset);
	}
}

class OutputLengthExceededException extends BrainfuckException
{
	public OutputLengthExceededException(String output, int offset)
	{
		super(output, offset);
	}
}
