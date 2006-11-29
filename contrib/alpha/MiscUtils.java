import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.net.*;

public class MiscUtils
{

	Modules mods;
	private IRCInterface irc;

	public MiscUtils(Modules mods, IRCInterface irc)
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
	public void filterReplace ( Message mes )
	{
		try
		{
			Matcher matcher = Pattern.compile(irc.getTriggerRegex() + filterReplaceRegex, Pattern.CASE_INSENSITIVE).matcher(mes.getMessage());
			String messageString = mes.getMessage().replaceAll("\\\\\\/","[:FWSLASH:]");
			if (!matcher.find()) {
				return;
			}
			String replace[] = messageString.split("\\/");
			String original = replace[1].replaceAll("\\[:FWSLASH:\\]","/");
			String replacement = "";

			if (replace.length > 2)  replacement = replace[2].replaceAll("\\[:FWSLASH:\\]","/");

			List<Message> history = mods.history.getLastMessages( mes, 10 );
			String lastMessage;
			for(int i=0; i<history.size(); i++)
			{
				Message thisLine = history.get(i);
				matcher = Pattern.compile(irc.getTriggerRegex(), Pattern.CASE_INSENSITIVE).matcher(thisLine.getMessage());
				if ((thisLine.getNick().equals(mes.getNick())) 
					&& (thisLine.getContext().equals(mes.getContext())) 
					&& (thisLine.getMessage().matches(".*" + original + ".*"))
					&& (!(thisLine.getMessage().matches(filterReplaceRegex)))
					&& (!matcher.find())
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
				Message thisLine = history.get(i);
				matcher = Pattern.compile(irc.getTriggerRegex(), Pattern.CASE_INSENSITIVE).matcher(thisLine.getMessage());
				if ((thisLine.getContext().equals(mes.getContext())) 
					&& (thisLine.getMessage().matches(".*" + original + ".*"))
					&& (!(thisLine.getMessage().matches(filterReplaceRegex)))
					&& (!matcher.find())
					)
				{
					String newLine = thisLine.getMessage().replaceAll(original,replacement);
					if (newLine.length() > MAXLENGTH) newLine = newLine.substring(0,MAXLENGTH);
					irc.sendContextMessage(mes, mes.getNick() + " thinks " + thisLine.getNick() + " meant: " + newLine);
					return;
				}
				
			}

		} catch (Exception e) {return;}
	}
	

	public String[] helpCommandFilter = {
		"Replace <ORIGINAL CHARS> with <REPLACEMENT CHARS> in <MESSAGE>",
		"<ORIGINAL CHARS> <REPLACEMENT CHARS> <MESSAGE>."
	};
	public void commandFilter(Message mes)
	{
		List<String> parm=mods.util.getParams(mes);

		String oldString = parm.get(1);
		String replacementString = parm.get(2);
		replacementString = replacementString.replaceAll("\\[:SPACE:\\]"," ");
		oldString = oldString.replaceAll("\\[:SPACE:\\]"," ");

		char[] oldChars = oldString.toCharArray();
		char[] replacementChars = replacementString.toCharArray();

		String returnedString = (mods.util.getParamString(mes));
		returnedString = returnedString.substring(returnedString.indexOf(" ") +1);
		returnedString = returnedString.substring(returnedString.indexOf(" ") +1);
		String originalString = returnedString;
		for (int i = 0 ; i < returnedString.length(); i++)
		{
			for (int ii = 0; ii < oldChars.length; ii++)
			{
				if (originalString.charAt(i) == oldChars[ii] ) 
				{
					char[] temp = returnedString.toCharArray();
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
	public void commandReplaceAll(Message mes)
	{
		List<String> parms=mods.util.getParams(mes);

		String[] parm = new String[parms.size()];

		for (int i = 0; i < parms.size(); i++)
		{
			parm[i] = parms.get(i);
		}

		for (int i = 1; i < 3; i++)
		{
			parm[i] = parm[i].replaceAll("\\\\;","[:INSERTCOLON:]");
		}
		String[] oldStrings = parm[1].replaceAll("\"","").split(";");
		String[] replacementStrings = parm[2].replaceAll("\"","").split(";");


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

		String returnedString = (mods.util.getParamString(mes));
		returnedString = returnedString.substring(returnedString.indexOf(" ") +1);
		returnedString = returnedString.substring(returnedString.indexOf(" ") +1);
		for (int i = 0; i < oldStrings.length; i++)
		{
			returnedString = returnedString.replaceAll(oldStrings[i],replacementStrings[i]);
		}
		irc.sendContextMessage(mes,returnedString);
	}

}
