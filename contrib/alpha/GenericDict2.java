import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.io.*;
import java.net.*;

public class GenericDict2
{


	private IRCInterface irc;
	private Modules mods;

	public GenericDict2(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	private final String c_style_quoted_string = "\"((?:\\\\.|[^\"\\\\])*)\"";
	private final String replace_all_statement = "\\.replaceAll\\(" + c_style_quoted_string + "\\s*,\\s*" + c_style_quoted_string + "\\)";

	private final String[] genericDictLongHelp = {
		"parameters may be specified in any order ",
		"Options: --url <urlparams> --match <patterns> --output <outputstring> --options <options>",
		"<urlparams> is a list of strings to be concatenated into a URL which will be used for scraping. Enclose sections to preserve spaces in with ` backticks ",
		"<patterns> is a list of patterns to match sections of the website to scrape, with variable names to assign the contents to, eg <a>%VARNAME%</a> would assign the section in the a tag to %VARNAME%",
		"<outputstring> is what the command will output, insert the variable data scraped with <patterns> as appropriate",
		"in the outputstring you may use '%VARNAME%.replaceAll(pattern,string)' to alter the variable contents and 'if ( SOMESTR == ASTR ) { FOO } ' to make selections about what to output",
		"Example: genericdict --url http://example.com/ ` $1 $2 ` /somemoreurl/ ` $3 ` /endurl --match <a>%SOMEVAR%</a> lalala%BADGERS%lalala --output if ( $1 == example ) { Somevar is %SOMEVAR%. } Here be badgers %BADGERS%.replaceAll(\"Badgers\",\"Ponies\")"
	};

	public void commandGetHelp(Message mes)
	{
		irc.sendContextMessage(mes,genericDictLongHelp);
	}

	public String[] helpCommandGenericDict = {
		"A Stupidly complex command to scrape text from a website, use genericdict2.gethelp for detailed usage."
	};
	public void commandGenericDict(Message mes)
	{
		try
		{
			String message = " " + mods.util.getParamString(mes);

			URL url = getURL(getParams(message,"url",true));

			HashMap<String,String> matchers = new HashMap<String,String>();
			HashMap<String,String> values = new HashMap<String,String>();

			final String patternsError = "Your patterns must contain a variable to assign result to";
			try
			{
				for (String regex : getParams(message,"match",true))
				{
					String[] split = regex.split("%.*?%");
					matchers.put(getVarName(regex),"(?s)" + split[0] + "(.*?)" + split[1]);
				}
			}
			catch (ArrayIndexOutOfBoundsException e)
			{
				throw new GenericDictException(patternsError);
			}
			catch (StringIndexOutOfBoundsException e)
			{
				throw new GenericDictException(patternsError);
			}

			for (String key : matchers.keySet())
			{
				Matcher ma;
				try
				{
					ma = mods.scrape.getMatcher(url, GetContentsCached.DEFAULT_TIMEOUT, matchers.get(key));
				}
				catch (IOException e)
				{
					throw new GenericDictException("Error reading from specified site");
				}

				if (ma.find())
				{
					values.put(key,ma.group(1));
				}
			}

			String toReturn = formatOutputString(getParams(message,"output",true),values);

			toReturn = mods.scrape.readyForIrc(toReturn) + " ";
			boolean appendURL = false;
			for (String opt : getParams(message,"options",false))
				if (opt.equals("appendurl"))
					appendURL = true;

			int maxLength = -1;
			for (String opt : getParams(message,"limit",false))
			{
				try
				{
					maxLength = Integer.parseInt(opt);
				} catch (NumberFormatException e) {}
			}

			if ((maxLength > 0) && (toReturn.length() > maxLength))
			{
				toReturn = toReturn.substring(0,maxLength) + "...";
			}

			if ((toReturn.length() == 0) || toReturn.matches("^(\\s|\\t|\\r)*$"))
				throw new GenericDictException("No results found");

			if (!appendURL)
				irc.sendContextReply(mes,toReturn);
			else
				irc.sendContextReply(mes,toReturn + url.toString());

		}
		catch (GenericDictException e)
		{
			irc.sendContextReply(mes,e.getMessage());
		}
	}

	private URL getURL(ArrayList<String> urlParts) throws GenericDictException
	{
		String urlString = "";
		boolean skipwhitespace = true;
		URL url = null;
		try
		{
			for (String urlPart : urlParts)
				if (urlPart.equals("`"))
				{
					skipwhitespace = !skipwhitespace;
					if (skipwhitespace)
						urlString = urlString.replaceFirst("\\+$","");
				}
				else
				{
					if (skipwhitespace)
						urlString = urlString + urlPart;
					else
						urlString = urlString + URLEncoder.encode(urlPart + " ","UTF-8");
				}

			url = new URL(urlString);
		}
		catch (MalformedURLException e)
		{
			throw new GenericDictException("Malformed URL");
		}
		catch (UnsupportedEncodingException e)
		{
			throw new GenericDictException("Unsupported Encoding");
		}

		return url;
	}

	private ArrayList<String> getParams(String message,String option, boolean required) throws GenericDictException
	{
		int index = message.indexOf(" --" + option + " ");
		if (index == -1)
		{
			if (required)
				throw new GenericDictException("No parameters found for required option: " + option);
			else
				return new ArrayList<String>();
		}
		try
		{
			String optionStr = message.substring(index + option.length() + 4).replaceFirst("\\s--\\w.*","");
			return new ArrayList<String>(Arrays.asList(optionStr.split("\\s")));
		} catch (StringIndexOutOfBoundsException e)
		{
			throw new GenericDictException("Error reading parameters for " + option);
		}
	}

	private String getVarName(String str) throws GenericDictException
	{
		try
		{
			String toReturn = str.substring(str.indexOf("%") + 1);
			return toReturn.substring(0,toReturn.indexOf("%"));
		} catch (StringIndexOutOfBoundsException e)
		{
			throw new GenericDictException("Unable to find variable name, syntax for variables is %variablename%");
		}
	}

	/** Delegate to formatOutputString without a list as the first param */
	private String formatOutputString(List<String> unformatted, Map<String, String> varValues) throws GenericDictException
	{
		StringBuilder sb = new StringBuilder();
		for (String s : unformatted)
			sb.append(s).append(" ");
		return formatOutputString(sb.toString(), varValues);
	}

	/** Format an --output string.
	 * @param unformatted List of words (split(" ")) in the output string.
	 * @param varVaules Values of the %THINGS%.
	 * @return The block of text to be processed before sending to irc.
	 */
	private String formatOutputString(String unformatted, Map<String, String> varValues) throws GenericDictException
	{
		final String varPattern = "%\\w*?%";
		final String varReplacePattern = varPattern + "(" + replace_all_statement + ")";

		String toReturn = "";

		String tmp = unformatted;
		Matcher p;
		final Pattern vrp = Pattern.compile(varReplacePattern);

		// While we can find a varReplacePattern in the string.
		while ((p = vrp.matcher(tmp)).find())
		{
			// Run the sane matcher to get the arguments.
			Matcher moo = Pattern.compile(replace_all_statement).matcher(p.group(1));
			if (!moo.find())
				throw new GenericDictException("syntax of replaceAll is %variable%.replaceAll(\"regex\",\"replacement\")");

			// Grab and check the args.
			final String toReplace = moo.group(1);
			final String theReplacement = moo.group(2);

			assert toReplace != null;
			assert theReplacement != null;

			System.out.println("toReplace" + toReplace);
			System.out.println("theReplacement" + theReplacement);

			// Do the replacement.
			try
			{
				tmp = tmp.replaceFirst(varReplacePattern, varValues.get(getVarName(tmp)).replaceAll(toReplace,theReplacement));
			}
			// (eew) The item didn't exist, so remove it and the statement.
			catch (NullPointerException e)
			{
				tmp = tmp.replaceFirst(varReplacePattern, "");
			}
		}

		// Remove anything that wasn't replaceAll'd to it's value.
		while (tmp.matches(".*?" + varPattern + ".*"))
		{
			try
			{
				tmp = tmp.replaceFirst(varPattern,varValues.get(getVarName(tmp)));
			}
			catch (NullPointerException e)
			{
				tmp = tmp.replaceFirst(varPattern,"");
			}
		}

		toReturn = toReturn + tmp + " ";

		toReturn = stripUndesirables(formatConditionals(toReturn));

		if (toReturn.length() == 0)
			throw new GenericDictException("You must specify something to output");

		return toReturn;
	}

	private String stripUnquotedWhiteSpace(String toStrip)
	{
		String toReturn = "";
		boolean skip = true;
		for (int i = 0 ; i < toStrip.length(); i++)
		{
			char chr = toStrip.charAt(i);
			if (chr != ' ')
			{
				if (chr == '"')
					skip = !skip;
				else
					toReturn = toReturn + Character.toString(chr);
			} else if (!skip)
			{
				toReturn = toReturn + Character.toString(chr);
			}
		}
		return toReturn;
	}

	private String formatConditionals(String unformatted) throws GenericDictException
	{
		String toReturn = unformatted.replaceAll("\n","");
		final String conditional = "(\\s|^)if\\s*?\\((.*?)\\)\\s*?\\{(.*?)\\}";
		Matcher conditionalPattern = Pattern.compile(".*?" +  conditional + ".*",Pattern.CASE_INSENSITIVE).matcher(toReturn);

		while (conditionalPattern.matches())
		{
			if (doComparison(conditionalPattern.group(2)))
				toReturn = toReturn.replaceFirst(conditional," " + conditionalPattern.group(3));
			else
				toReturn = toReturn.replaceFirst(conditional,"");
			conditionalPattern = Pattern.compile(".*?" +  conditional + ".*",Pattern.CASE_INSENSITIVE).matcher(toReturn);
		}
		return toReturn;
	}

	private boolean doComparison(String comparison) throws GenericDictException
	{
		int orIndex = comparison.indexOf("||");
		int andIndex = comparison.indexOf("&&");
		if (orIndex != -1)
		{
			return (doComparison(comparison.substring(0,orIndex)) || doComparison(comparison.substring(orIndex + 2,comparison.length())));
		}
		if (andIndex != -1)
		{
			return (doComparison(comparison.substring(0,andIndex)) || doComparison(comparison.substring(andIndex + 2,comparison.length() )));
		}
		String stripped = stripUnquotedWhiteSpace(comparison);
		final String syntax = "Conditionals must be in the form : 'if ( string <operator> string )";
		if (stripped.indexOf("==") != -1)
		{
			String[] split = ("'" + stripped + "'").split("==");
			if (split.length != 2)
				throw new GenericDictException(syntax);
			return ((split[0] + "'").equals("'" + split[1]));
		} else if (stripped.indexOf("!=") != -1)
		{
			String[] split = ("'" + stripped + "'").split("!=");
			if (split.length != 2)
				throw new GenericDictException(syntax);
			return !((split[0] + "'").equals("'" + split[1]));
		} else if (stripped.indexOf(">") != -1)
		{
			String[] split = ("'" + stripped + "'").split("");
			if (split.length != 2)
				throw new GenericDictException(syntax);
			return (((split[0] + "'").compareTo("'" + split[1])) > 0);
		} else if (stripped.indexOf("<") != -1)
		{
			String[] split = ("'" + stripped + "'").split("<");
			if (split.length != 2)
				throw new GenericDictException(syntax);
			return (((split[0] + "'").compareTo("'" + split[1])) < 0);
		}
		throw new GenericDictException(syntax);
	}

	private String stripUndesirables(String original)
	{
		String toReturn = original;
		int x = 0;
		while (toReturn.matches(".*\\s\\s.*") && x < 100)
		{
			toReturn = toReturn.replaceAll("\\s\\s"," ");
			x++;
		}
		toReturn = toReturn.replaceAll("\t","").replaceAll("\r","").replaceAll("\n"," ");
		return toReturn;
	}
}

class GenericDictException extends Exception
{
	public GenericDictException(String message)
	{
		super(message);
	}
}
