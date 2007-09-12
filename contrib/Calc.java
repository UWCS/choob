import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.event.*
import java.util.*;
import java.text.*;
import java.io.*;

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

	private IRCInterface irc;
	private Modules mods;
	public Calc(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	private static boolean hascoin = true;

	public String[] helpCommandCalc = {
		"Evaluates a mathematical expression.",
		"<Expression>",
		"<Expression> is some lovely mathematical expression"
	};
	public void commandCalc( Message mes )
	{
		String expr = mods.util.getParamString(mes);
		try
		{
			irc.sendContextReply(mes, expr + " = " + apiEval(expr));
		}
		catch (BadMathException e)
		{
			irc.sendContextReply(mes, "Urgh; could not parse! Error: " + e.getMessage());
		}
	}

	public double apiEval(String expr) throws BadMathException
	{
		MathParser parser = new MathParser(expr);
		return parser.eval();
	}
}

public class MathParser
{
	String expr;
	int ptr; // Holds position in the string.
	int length;
	public MathParser(String expr)
	{
		this.expr = expr.replaceAll("\\s", ""); // Mmmm, strippage.
		this.length = this.expr.length();
		ptr = 0;
	}

	public double eval() throws BadMathException
	{
		double result = additionExpr();
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
			double term = multExpr();
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
			double term = powerExpr();
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
			{ } // Still good...
			else
				return total;

			ptr++;

			double term = termExpr();
			total = Math.pow(total, term);
		}
	}

	// Either additionExpr(), bracketExpr(), funcExpr() or a number.
	public double termExpr() throws BadMathException
	{
		if (expr.length()<=ptr)
			throw err();
		char current = expr.charAt(ptr);
		if ((current >= '0' && current <= '9') || current == '-' || current == '.')
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
		int origPtr = ptr;
		int dpLength = 0;
		while(true)
		{
			if (ptr == length)
			{
				try
				{
					return Double.parseDouble(expr.substring(origPtr,ptr));
				}
				catch (NumberFormatException e)
				{
					throw new BadMathException(expr.substring(origPtr,ptr) + " is not a valid number!");
				}
			}
			char current = expr.charAt(ptr);
			if ((current >= '0' && current <= '9') || current == '.' || (current == '-' && ptr == origPtr))
				ptr++;
			else
			{
				try
				{
					return Double.parseDouble(expr.substring(origPtr,ptr));
				}
				catch (NumberFormatException e)
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
		double inner = additionExpr();
		if ((ptr >= length) || (expr.charAt(ptr) != ')'))
			throw err();
		ptr++;
		return inner;
	}

	// name bracketExpr()
	public double funcExpr() throws BadMathException
	{
		int oldPtr = ptr;
		char current = expr.charAt(ptr);
		while(Character.isLetter(current))
		{
			System.out.println(current);
			ptr++;
			if (ptr >= expr.length())
				break;
			current = expr.charAt(ptr);
		}
		String name = expr.substring(oldPtr, ptr).toLowerCase();
		if (current != '(')
			throw new BadMathException(name + " must take bracketed operands!");

		// Parse out the parameter.
		double param = bracketExpr();

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

public class BadMathException extends Exception
{
	public BadMathException(String text)
	{
		super(text);
	}
}
