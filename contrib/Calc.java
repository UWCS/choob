import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

public class Calc
{
	public String[] info()
	{
		return new String[] {
			"Plugin to do some simple calculations.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private final IRCInterface irc;
	private final Modules mods;
	public Calc(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public String[] helpCommandCalc = {
		"Evaluates a mathematical expression.",
		"<Expression>",
		"<Expression> is some lovely mathematical expression"
	};

	public String commandCalc( final String expr )
	{
		try
		{
			return expr + " = " + apiEval(expr);
		}
		catch (final BadMathException e)
		{
			return "Urgh; could not parse! Error: " + e.getMessage();
		}
	}

	public double apiEval(final String expr) throws BadMathException
	{
		final MathParser parser = new MathParser(expr);
		return parser.eval();
	}
}

class MathParser
{
	String expr;
	int ptr; // Holds position in the string.
	int length;
	public MathParser(final String expr)
	{
		this.expr = expr.replaceAll("\\s", ""); // Mmmm, strippage.
		this.length = this.expr.length();
		ptr = 0;
	}

	public double eval() throws BadMathException
	{
		final double result = additionExpr();
		if (ptr != length)
			throw new BadMathException("Trailing characters.");
		return result;
	}

	int OP_ADD = 0, OP_MINUS = 1, OP_MULT = 2, OP_DIV = 3;

	// a+b+c+...+e
	public double additionExpr() throws BadMathException
	{
		double total = 0;
		int op = OP_ADD;
		while(true)
		{
			final double term = multExpr();
			if (op == OP_ADD)
				total += term;
			else
				total -= term;

			if (ptr == length)
				return total;
			else if (expr.charAt(ptr) == '+')
				op = OP_ADD;
			else if (expr.charAt(ptr) == '-')
				op = OP_MINUS;
			else
				return total;

			ptr++;
		}
	}

	// a*b*c*...*e
	public double multExpr() throws BadMathException
	{
		double total = 1;
		int op = OP_MULT;
		while(true)
		{
			final double term = powerExpr();
			if (op == OP_MULT)
				total *= term;
			else
				total /= term;

			if (ptr == length)
				return total;
			else if (expr.charAt(ptr) == '*')
				op = OP_MULT;
			else if (expr.charAt(ptr) == '/')
				op = OP_DIV;
			else
				return total;

			ptr++;
		}
	}

	// a*b*c*...*e
	public double powerExpr() throws BadMathException
	{
		double total = termExpr();
		while(true)
		{
			if (ptr == length)
				return total;
			else if (expr.charAt(ptr) == '^')
			{
				// Still good...
			}
			else
				return total;

			ptr++;

			final double term = termExpr();
			total = Math.pow(total, term);
		}
	}

	// Either additionExpr(), bracketExpr(), funcExpr() or a number.
	public double termExpr() throws BadMathException
	{
		if (expr.length()<=ptr)
			throw err();
		final char current = expr.charAt(ptr);
		if (current >= '0' && current <= '9' || current == '-' || current == '.')
			return numberExpr();
		else if (current == '(')
			return bracketExpr();
		else if (Character.isLetter(current))
			return funcExpr();
		else
			throw err();
	}

	// [0-9]+(?:\.[0-9]+)?
	public double numberExpr() throws BadMathException
	{
		final int origPtr = ptr;
		while(true)
		{
			if (ptr == length)
			{
				try
				{
					return Double.parseDouble(expr.substring(origPtr,ptr));
				}
				catch (final NumberFormatException e)
				{
					throw new BadMathException(expr.substring(origPtr,ptr) + " is not a valid number!");
				}
			}
			final char current = expr.charAt(ptr);
			if (current >= '0' && current <= '9' || current == '.' || current == '-' && ptr == origPtr)
				ptr++;
			else
			{
				try
				{
					return Double.parseDouble(expr.substring(origPtr,ptr));
				}
				catch (final NumberFormatException e)
				{
					throw new BadMathException(expr.substring(origPtr,ptr) + " is not a valid number!");
				}
			}
		}
	}

	// ( additionExpr() )
	public double bracketExpr() throws BadMathException
	{
		ptr++;
		final double inner = additionExpr();
		if (ptr >= length || expr.charAt(ptr) != ')')
			throw err();
		ptr++;
		return inner;
	}

	// name bracketExpr()
	public double funcExpr() throws BadMathException
	{
		final int oldPtr = ptr;
		char current = expr.charAt(ptr);
		while(Character.isLetter(current))
		{
			System.out.println(current);
			ptr++;
			if (ptr >= expr.length())
				break;
			current = expr.charAt(ptr);
		}
		final String name = expr.substring(oldPtr, ptr).toLowerCase();
		if (current != '(')
			throw new BadMathException(name + " must take bracketed operands!");

		// Parse out the parameter.
		final double param = bracketExpr();

		if (name.equals("sin"))
			return Math.sin(param);
		else if (name.equals("cos"))
			return Math.cos(param);
		else if (name.equals("tan"))
			return Math.tan(param);
		else if (name.equals("asin"))
			return Math.asin(param);
		else if (name.equals("acos"))
			return Math.acos(param);
		else if (name.equals("atan"))
			return Math.atan(param);
		else if (name.equals("log"))
			return Math.log10(param);
		else if (name.equals("ln"))
			return Math.log(param);
		else if (name.equals("exp"))
			return Math.exp(param);
		else
			throw new BadMathException("Unknown function: " + name);
	}

	public BadMathException err()
	{
		if (ptr < length)
			return new BadMathException("Unexpected symbol at position " + ptr + ": " + expr.charAt(ptr));
		else
			if (ptr-1>0)
				return new BadMathException("Unexpected end of expression after " + expr.charAt(ptr-1));
			else
				return new BadMathException("Unexpected empty string.");
	}
}

class BadMathException extends Exception
{
	private static final long serialVersionUID = 1L;

	public BadMathException(final String text)
	{
		super(text);
	}
}
