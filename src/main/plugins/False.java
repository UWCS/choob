import java.util.EmptyStackException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Stack;

public class False
{
	private static int MAX_INSTRUCTIONS = 10000;
	private static int MAX_OUTPUT = 200;

	public String[] info()
	{
		return new String[] {
			"False interpreter.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};

	}
	public String[] helpCommandEval = {
		"Evaluates a False program.",
		"<expr>",
		"<expr> is a False program"
	};

	public String commandEval(final String mes)
	{
		String reply;

		try
		{
			reply = eval(mes);

			if (reply.length() == 0)
			{
				reply = "(no output)";
			}
		}
		catch (final EmptyStackException e)
		{
			reply = "Expected element on stack";
		}
		catch (final FalseException e)
		{
			reply = e.getMessage();
		}

		return reply;
	}

	public String eval(final String expr) throws FalseException
	{
		final FalseInterpreter interp = new FalseInterpreter(MAX_INSTRUCTIONS, MAX_OUTPUT);
		return interp.eval(expr);
	}
}

class FalseInterpreter
{
	private final Object vars[];
	private final FalseStack stack;
	private final int max_instructions;
	private final int max_output;
	private final StringBuilder output;
	private int count = 0;

	public FalseInterpreter(final int max_instructions, final int max_output)
	{
		this.vars = new Object[26];
		this.stack = new FalseStack();
		this.output = new StringBuilder();
		this.max_instructions = max_instructions;
		this.max_output = max_output;
		this.count = 0;
	}

	public String eval(final String expr) throws FalseException
	{
		return eval(new FalseTokenList(expr));
	}

	public String eval(final FalseTokenList tokens) throws FalseException
	{
		FalseTokenList func, cond;
		FalseToken tok, tok2;
		Object o1, o2, o3;
		Stack<Object> objs;
		int tmp, n;

		Iterator<FalseToken> it = tokens.iterator();

		while(it.hasNext())
		{
			tok = it.next();

			switch(tok.type)
			{
			case Value:
				stack.PushValue(tok.GetInt());
				break;
			case Variable:
				if(!it.hasNext())
				{
					throw new FalseException("Expected ';' or ':' after variable");
				}

				tok2 = it.next();
				if(tok2.type == FalseTokenType.Set)
				{
					vars[tok.GetInt()] = stack.Pop();
				}
				else if(tok2.type == FalseTokenType.Get)
				{
					if(vars[tok.GetInt()] == null)
					{
						throw new FalseException("Used uninitialised variable '" +
							(char)(tok.GetInt() + 'a') + "'");
					}

					stack.Push(vars[tok.GetInt()]);
				}
				else
				{
					throw new FalseException("Expected ';' or ':' after variable");
				}
				break;
			case Function:
				stack.PushFunc(tok.GetFunc());
				break;
			case Comment:
				break;
			case Apply:
				eval(stack.PopFunction().func);
				break;
			case Add:
				stack.PushValue(stack.PopValue().val + stack.PopValue().val);
				break;
			case Sub:
				stack.PushValue(- stack.PopValue().val + stack.PopValue().val);
				break;
			case Mul:
				stack.PushValue(stack.PopValue().val * stack.PopValue().val);
				break;
			case Div:
				tmp = stack.PopValue().val;
				stack.PushValue(stack.PopValue().val / tmp);
				break;
			case Neg:
				stack.PushValue(-stack.PopValue().val);
				break;
			case And:
				stack.PushValue(stack.PopValue().val == 0 || stack.PopValue().val == 0 ? 0 : -1);
				break;
			case Or:
				stack.PushValue(stack.PopValue().val == 0 && stack.PopValue().val == 0 ? 0 : -1);
				break;
			case Not:
				stack.PushValue(stack.PopValue().val == 0 ? -1 : 0);
				break;
			case Equal:
				stack.PushValue(stack.PopValue().val == stack.PopValue().val ? -1 : 0);
				break;
			case GreaterThan:
				stack.PushValue(stack.PopValue().val < stack.PopValue().val ? -1 : 0);
				break;
			case Dup:
				stack.Push(stack.Peek());
				break;
			case Drop:
				stack.Pop();
				break;
			case Swap:
				o1 = stack.Pop();
				o2 = stack.Pop();
				stack.Push(o1);
				stack.Push(o2);
				break;
			case Rot:
				o1 = stack.Pop();
				o2 = stack.Pop();
				o3 = stack.Pop();
				stack.Push(o2);
				stack.Push(o1);
				stack.Push(o3);
				break;
			case Pick:
				objs = new Stack<Object>();
				tmp = stack.PopValue().val;
				n = tmp;
				while(n-- > 0)
				{
					objs.push(stack.Pop());
				}
				o1 = stack.Peek();
				while(n++ < tmp)
				{
					stack.Push(objs.pop());
				}
				stack.Push(o1);
				break;
			case If:
				func = stack.PopFunction().func;
				if(stack.PopValue().val != 0)
				{
					eval(func);
				}
				break;
			case While:
				func = stack.PopFunction().func;
				cond = stack.PopFunction().func;

				eval(cond);
				while(stack.PopValue().val != 0)
				{
					eval(func);
					eval(cond);
				}
				break;
			case String:
				output.append((String)(tok.val));
				break;
			case PrintInt:
				output.append(stack.PopValue().val);
				break;
			case PrintChar:
				output.append((char)(stack.PopValue().val));
				break;
			case Flush:
				break;
			case Asm:
				throw new FalseException("Inline assembly unimplemented");
			case Read:
				throw new FalseException("Read unimplemented");
			default:
				throw new FalseException("Internal Error: Invalid token");
			}

			count++;

			if(count == max_instructions)
			{
				throw new FalseException("Instruction count exceeded");
			}

			if(output.length() >= max_output)
			{
				throw new FalseException("Maximum output exceeded");
			}
		}

		return output.toString();
	}

	enum FalseTokenType
	{
		Variable, Function, Value, Comment, String,
		Dup, Drop, Swap, Rot, Pick,
		Add, Sub, Mul, Div, Neg,
		And, Or, Not, Equal, GreaterThan,
		Set, Get, Apply, If, While,
		PrintInt, PrintChar, Read, Flush,
		Asm,
	}

	class FalseToken
	{
		public FalseTokenType type;
		public Object val;

		public FalseToken(FalseTokenType type, Object val)
		{
			this.type = type;
			this.val = val;
		}

		public int GetInt()
		{
			return ((Integer)(this.val)).intValue();
		}

		public FalseTokenList GetFunc()
		{
			return ((FalseTokenList)(this.val));
		}
	}

	class FalseTokenList implements Iterable<FalseToken>
	{
		private LinkedList<FalseToken> tokens;

		public FalseTokenList(final String expr) throws FalseException
		{
			int pos = 0;
			int nest = 0;

			tokens = new LinkedList<FalseToken>();

			while(pos < expr.length())
			{
				StringBuilder s = new StringBuilder();
				char c;

				switch(expr.charAt(pos))
				{
				case '+':
					tokens.offer(new FalseToken(FalseTokenType.Add, null));
					break;
				case '-':
					tokens.offer(new FalseToken(FalseTokenType.Sub, null));
					break;
				case '*':
					tokens.offer(new FalseToken(FalseTokenType.Mul, null));
					break;
				case '/':
					tokens.offer(new FalseToken(FalseTokenType.Div, null));
					break;
				case '_':
					tokens.offer(new FalseToken(FalseTokenType.Neg, null));
					break;
				case '$':
					tokens.offer(new FalseToken(FalseTokenType.Dup, null));
					break;
				case '%':
					tokens.offer(new FalseToken(FalseTokenType.Drop, null));
					break;
				case '\\':
					tokens.offer(new FalseToken(FalseTokenType.Swap, null));
					break;
				case '@':
					tokens.offer(new FalseToken(FalseTokenType.Rot, null));
					break;
				case 'ø':
					tokens.offer(new FalseToken(FalseTokenType.Pick, null));
					break;
				case '&':
					tokens.offer(new FalseToken(FalseTokenType.And, null));
					break;
				case '|':
					tokens.offer(new FalseToken(FalseTokenType.Or, null));
					break;
				case '~':
					tokens.offer(new FalseToken(FalseTokenType.Not, null));
					break;
				case '=':
					tokens.offer(new FalseToken(FalseTokenType.Equal, null));
					break;
				case '>':
					tokens.offer(new FalseToken(FalseTokenType.GreaterThan, null));
					break;
				case ':':
					tokens.offer(new FalseToken(FalseTokenType.Set, null));
					break;
				case ';':
					tokens.offer(new FalseToken(FalseTokenType.Get, null));
					break;
				case '?':
					tokens.offer(new FalseToken(FalseTokenType.If, null));
					break;
				case '#':
					tokens.offer(new FalseToken(FalseTokenType.While, null));
					break;
				case '!':
					tokens.offer(new FalseToken(FalseTokenType.Apply, null));
					break;
				case '.':
					tokens.offer(new FalseToken(FalseTokenType.PrintInt, null));
					break;
				case ',':
					tokens.offer(new FalseToken(FalseTokenType.PrintChar, null));
					break;
				case '^':
					tokens.offer(new FalseToken(FalseTokenType.Read, null));
					break;
				case 'ß':
					tokens.offer(new FalseToken(FalseTokenType.Flush, null));
					break;
				case '`':
					tokens.offer(new FalseToken(FalseTokenType.Asm, null));
					break;
				case '\'':
					c = expr.charAt(++pos);
					tokens.offer(new FalseToken(FalseTokenType.Value, Integer.valueOf(c)));
					break;
				case '{':
					try
					{
						pos++;

						while((c = expr.charAt(pos)) != '}')
						{
							s.append(c);
							pos++;
						}
					}
					catch(StringIndexOutOfBoundsException e)
					{
						throw new FalseException("Missing closing '}'");
					}

					tokens.offer(new FalseToken(FalseTokenType.Comment, s.toString()));
					break;
				case '"':
					pos++;

					try
					{
						while((c = expr.charAt(pos)) != '"')
						{
							s.append(c);
							pos++;
						}
					}
					catch(StringIndexOutOfBoundsException e)
					{
						throw new FalseException("Missing closing '\"'");
					}

					tokens.offer(new FalseToken(FalseTokenType.String, s.toString()));
					break;
				case '[':
					nest = 0;
					pos++;

					try
					{
						while((c = expr.charAt(pos)) != ']' || nest != 0)
						{
							if(c == '[')
							{
								nest++;
							}
							else if(c == ']')
							{
								nest--;
							}

							s.append(c);
							pos++;
						}
					}
					catch(StringIndexOutOfBoundsException e)
					{
						throw new FalseException("Missing closing ']'");
					}

					tokens.offer(new FalseToken(FalseTokenType.Function,
						new FalseTokenList(s.toString())));
					break;
				case ' ':
					break;
				default:
					c = expr.charAt(pos);

					if(c >= 'a' && c <= 'z')
					{
						tokens.offer(new FalseToken(FalseTokenType.Variable, c - 'a'));
					}
					else if(c >= '0' && c <= '9')
					{
						while(c >= '0' && c <= '9')
						{
							s.append(c);

							if(++pos >= expr.length() - 1)
							{
								break;
							}

							c = expr.charAt(pos);
						}

						tokens.offer(new FalseToken(FalseTokenType.Value,
							Integer.parseInt(s.toString())));

						if(pos < expr.length())
						{
							pos--;
						}
					}
					else
					{
						throw new FalseException("Invalid token '" + c + "'");
					}
				}

				pos++;
			}
		}

		@Override
		public Iterator<FalseToken> iterator()
		{
			return tokens.iterator();
		}
	}

	class FalseStack
	{
		private Stack<Object> stack;

		public FalseStack()
		{
			stack = new Stack<Object>();
		}

		public Object Push(Object val)
		{
			return stack.push(val);
		}

		public Object PushValue(int val)
		{
			return stack.push(new FalseValue(val));
		}

		public Object PushFunc(FalseTokenList val)
		{
			return stack.push(new FalseFunction(val));
		}

		public FalseValue PopValue() throws FalseException
		{
			Object val = stack.pop();

			if(val instanceof FalseValue)
			{
				return (FalseValue)val;
			}
			else
			{
				throw new FalseException("Expected value on stack");
			}
		}

		public FalseFunction PopFunction() throws FalseException
		{
			Object val = stack.pop();

			if(val instanceof FalseFunction)
			{
				return (FalseFunction)val;
			}
			else
			{
				throw new FalseException("Expected function on stack");
			}
		}

		public Object Peek()
		{
			return stack.peek();
		}

		public Object Pop()
		{
			return stack.pop();
		}
	}

	class FalseValue
	{
		public int val;

		public FalseValue(final int val)
		{
			this.val = val;
		}
	}

	class FalseFunction
	{
		public FalseTokenList func;

		public FalseFunction(final FalseTokenList func)
		{
			this.func = func;
		}
	}
}

class FalseException extends Exception
{
	private static final long serialVersionUID = -3487398475938759873L;

	public FalseException(final String message)
	{
		super(message);
	}
}
