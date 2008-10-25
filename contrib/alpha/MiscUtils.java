import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

public class MiscUtils
{

	Modules mods;
	private final IRCInterface irc;

	public MiscUtils(final Modules mods, final IRCInterface irc)
	{
		this.mods=mods;
		this.irc=irc;
	}

        public String[] info()
        {
                return new String[] {


                        "Plugin for random misc stuff",

                        "The Choob Team",

                        "choob@uwcs.co.uk",

                        "$Rev$$Date$"
                };
	}

	//now less spammy
	public String filterReplaceRegex = "s\\/.*?\\/.*?\\/";
	private final int MAXLENGTH = 300;
	public void filterReplace ( final Message mes )
	{
		try
		{
			Matcher matcher = Pattern.compile(irc.getTriggerRegex() + filterReplaceRegex, Pattern.CASE_INSENSITIVE).matcher(mes.getMessage());
			final String messageString = mes.getMessage().replaceAll("\\\\\\/","[:FWSLASH:]");
			if (!matcher.find()) {
				return;
			}
			final String replace[] = messageString.split("\\/");
			final String original = replace[1].replaceAll("\\[:FWSLASH:\\]","/");
			String replacement = "";

			if (replace.length > 2)  replacement = replace[2].replaceAll("\\[:FWSLASH:\\]","/");

			final List<Message> history = mods.history.getLastMessages( mes, 10 );
			for(int i=0; i<history.size(); i++)
			{
				final Message thisLine = history.get(i);
				matcher = Pattern.compile(irc.getTriggerRegex(), Pattern.CASE_INSENSITIVE).matcher(thisLine.getMessage());
				if (thisLine.getNick().equals(mes.getNick())
					&& thisLine.getContext().equals(mes.getContext())
					&& thisLine.getMessage().matches(".*" + original + ".*")
					&& !thisLine.getMessage().matches(filterReplaceRegex)
					&& !matcher.find()
					)
				{
					String newLine = thisLine.getMessage().replaceAll(original,replacement);
					if (newLine.length() > MAXLENGTH) newLine = newLine.substring(0,MAXLENGTH);
					irc.sendContextMessage(mes, mes.getNick() + " meant: " + newLine);
					return;
				}

			}
			for(int i=0; i<history.size(); i++)
			{
				final Message thisLine = history.get(i);
				matcher = Pattern.compile(irc.getTriggerRegex(), Pattern.CASE_INSENSITIVE).matcher(thisLine.getMessage());
				if (thisLine.getContext().equals(mes.getContext())
					&& thisLine.getMessage().matches(".*" + original + ".*")
					&& !thisLine.getMessage().matches(filterReplaceRegex)
					&& !matcher.find()
					)
				{
					String newLine = thisLine.getMessage().replaceAll(original,replacement);
					if (newLine.length() > MAXLENGTH) newLine = newLine.substring(0,MAXLENGTH);
					irc.sendContextMessage(mes, mes.getNick() + " thinks " + thisLine.getNick() + " meant: " + newLine);
					return;
				}

			}

		} catch (final Exception e) {return;}
	}


	public String[] helpCommandFilter = {
		"Replace <ORIGINAL CHARS> with <REPLACEMENT CHARS> in <MESSAGE>",
		"<ORIGINAL CHARS> <REPLACEMENT CHARS> <MESSAGE>."
	};
	public void commandFilter(final Message mes)
	{
		final List<String> parm=mods.util.getParams(mes);

		String oldString = parm.get(1);
		String replacementString = parm.get(2);
		replacementString = replacementString.replaceAll("\\[:SPACE:\\]"," ");
		oldString = oldString.replaceAll("\\[:SPACE:\\]"," ");

		final char[] oldChars = oldString.toCharArray();
		final char[] replacementChars = replacementString.toCharArray();

		String returnedString = mods.util.getParamString(mes);
		returnedString = returnedString.substring(returnedString.indexOf(" ") +1);
		returnedString = returnedString.substring(returnedString.indexOf(" ") +1);
		final String originalString = returnedString;
		for (int i = 0 ; i < returnedString.length(); i++)
		{
			for (int ii = 0; ii < oldChars.length; ii++)
			{
				if (originalString.charAt(i) == oldChars[ii] )
				{
					final char[] temp = returnedString.toCharArray();
					if (ii < replacementChars.length) temp[i] = replacementChars[ii]; else temp[i] = replacementChars[replacementChars.length -1];
					returnedString = new String(temp);
				}
			}
		}

		irc.sendContextMessage(mes,returnedString);
	}

	public String[] helpCommandReplaceAll = {
		"Replace all instances of <; Separated list of regexes> with <; Separated list of strings>, NB: each replacement is performed successively, so replaceAll a;b b;a will not change anything" ,
		"<ORIGINAL REGEX(s)> <REPLACEMENT STRING(s)> <MESSAGE>."
	};
	public void commandReplaceAll(final Message mes)
	{
		final List<String> parms=mods.util.getParams(mes);

		final String[] parm = new String[parms.size()];

		for (int i = 0; i < parms.size(); i++)
		{
			parm[i] = parms.get(i);
		}

		for (int i = 1; i < 3; i++)
		{
			parm[i] = parm[i].replaceAll("\\\\;","[:INSERTCOLON:]");
		}
		final String[] oldStrings = parm[1].replaceAll("\"","").split(";");
		final String[] replacementStrings = parm[2].replaceAll("\"","").split(";");


		for (int i = 0; i < oldStrings.length; i++)
		{
			oldStrings[i] = oldStrings[i].replaceAll("\\[:SPACE:\\]"," ").replaceAll("\\[:BLANK:\\]|\\[:NULL:\\]","");
		}

		for (int i = 0; i < replacementStrings.length; i++)
		{
			replacementStrings[i] = replacementStrings[i].replaceAll("\\[:SPACE:\\]"," ").replaceAll("\\[:BLANK:\\]|\\[:NULL:\\]","");
		}

		if (oldStrings.length != replacementStrings.length)
		{
			irc.sendContextMessage(mes,"Error, old and replacement parameters do not match");
			return;
		}

		for (int i = 0; i < oldStrings.length; i++)
		{
			oldStrings[i] = oldStrings[i].replaceAll("\\[:INSERTCOLON:\\]","\\\\;");
			replacementStrings[i] = replacementStrings[i].replaceAll("\\[:INSERTCOLON:\\]","\\\\;");
		}

		String returnedString = mods.util.getParamString(mes);
		returnedString = returnedString.substring(returnedString.indexOf(" ") +1);
		returnedString = returnedString.substring(returnedString.indexOf(" ") +1);
		for (int i = 0; i < oldStrings.length; i++)
		{
			returnedString = returnedString.replaceAll(oldStrings[i],replacementStrings[i]);
		}
		irc.sendContextMessage(mes,returnedString);
	}

}
