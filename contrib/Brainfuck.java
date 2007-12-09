import java.util.*;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

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
			String[] tokens = {"<", ">", "+", "-", ".", "[", "]"};
			reply = eval(mods.util.getParamString(mes), tokens);

			if (reply.length() == 0)
			{
				reply = "(no output)";
			}
		}
		catch (InstructionCountExceededException e)
		{
			reply = e.getMessage() + " (halted after " + MAX_INSTRUCTIONS + " instructions at position " + e.getOffset() + ")";
		}
		catch (OutputLengthExceededException e)
		{
			reply = e.getMessage() + " (halted after " + MAX_OUTPUT + " chars of output at position " + e.getOffset() + ")";
		}
		catch (BrainfuckException e)
		{
			reply = "Parse error at position " + e.getOffset() + ": " + e.getMessage();
		}

		irc.sendContextReply(mes, reply);
	}

	public String eval(String expr, String[] tokens) throws BrainfuckException
	{
		BrainfuckInterpreter interp = new BrainfuckInterpreter(expr, tokens, MAX_INSTRUCTIONS, MAX_OUTPUT);
		return interp.eval();
	}
}

class BrainfuckInterpreter
{
	private byte[] mem = new byte[30000];
	private int ptr = 0; 
	private int count = 0;

	private String expr;
	private String[] tokens;
	private int max_instructions;
	private int max_output;

	public BrainfuckInterpreter(String expr, String[] tokens, int max_instructions, int max_output)
	{
		this.expr = expr;
		this.tokens = tokens;
		this.max_instructions = max_instructions;
		this.max_output = max_output;
	}


	public String eval() throws BrainfuckException
	{
		StringBuilder output = new StringBuilder();
		List<BrainfuckToken> my_tokens = tokenise();

		BrainfuckToken token;
		int pc = 0;

		while (pc < my_tokens.size())
		{
			token = my_tokens.get(pc);
			++count;

			if (count == max_instructions)
			{
				throw new InstructionCountExceededException(output.toString(), token.pos);
			}

			switch (token.sym)
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
						throw new OutputLengthExceededException(output.toString(), token.pos);
					}
					break;
				case '[':
					if (mem[ptr] == 0)
					{
						pc = token.jne;
					}
					break;
				case ']':
					pc = token.jne - 1;
					break;
			}
			++pc;
		}

		return output.toString();
	}

	private List<BrainfuckToken> tokenise() throws BrainfuckException
	{
	  	List<BrainfuckToken> my_tokens = new ArrayList<BrainfuckToken>();
		Stack<Integer> pc_stack = new Stack<Integer>();

		int pos = 0;
		int jne = 0;

		char[] symbols = {'<', '>', '+', '-', '.', '[', ']'};

		while (pos < expr.length())
		{
			boolean flag = true;
			for (int i = 0; (i < tokens.length) && flag; ++i)
			{
				if (expr.substring(pos, pos + tokens[i].length()).equals(tokens[i]))
				{
					switch (symbols[i])
					{
						case '[':
							pc_stack.push(my_tokens.size());
							break;
						case ']':
							try
							{
								jne = pc_stack.pop();
								my_tokens.get(jne).jne = my_tokens.size();
							}
							catch (EmptyStackException e)
							{
								throw new ParseErrorException(pos);
							}
							break;
					}

					my_tokens.add(new BrainfuckToken(symbols[i], jne, pos + 1));
					pos += tokens[i].length() - 1;
					flag = true;
				}
			}

			++pos;
		}

		if (pc_stack.size() > 0)
		{
			throw new ParseErrorException(pos);
		}

		return my_tokens;
	}

	class BrainfuckToken
	{
		public char sym;
		public int jne;
		public int pos;

		public BrainfuckToken(char sym, int jne, int pos)
		{
			this.sym = sym;
			this.jne = jne;
			this.pos = pos;
		}
	}
}

class BrainfuckException extends Exception
{
	private static final long serialVersionUID = -7656099851954125772L;
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
	private static final long serialVersionUID = 4152970669488324439L;

	public ParseErrorException(int offset)
	{
		super("Mismatched bracket", offset);
	}
}

class InstructionCountExceededException extends BrainfuckException
{
	private static final long serialVersionUID = 7180806496577431500L;

	public InstructionCountExceededException(String output, int offset)
	{
		super(output, offset);
	}
}

class OutputLengthExceededException extends BrainfuckException
{
	private static final long serialVersionUID = 2318303252562127659L;

	public OutputLengthExceededException(String output, int offset)
	{
		super(output, offset);
	}
}
