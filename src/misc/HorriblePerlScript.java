/**
 * Class to generate events.
 * @author bucko
 */

package org.uwcs.choob.misc;

import java.util.*;
import java.io.*;
import java.util.regex.*;

public class HorriblePerlScript
{
	// Inheritance map
	private final Map<String,String[]> inheritance = new HashMap<String,String[]>();
	private final Map<String,String[]> interfaces = new HashMap<String,String[]>();
	private final Map<String,String[]> overrides = new HashMap<String,String[]>();
	private final List<String[]> handlers = new ArrayList<String[]>();

	public HorriblePerlScript()
	{
		// Classes, and which intrefaces/classes the inherit from.
		inheritance.put("IRCEvent", null);

		inheritance.put("Message", new String[] { "IRCEvent", "MessageEvent", "ContextEvent", "UserEvent", "AimedEvent", });

		inheritance.put("PrivateMessage", new String[] { "Message", "PrivateEvent", "CommandEvent", "FilterEvent", });
		inheritance.put("ChannelMessage", new String[] { "Message", "ChannelEvent", "CommandEvent", "FilterEvent", });
		inheritance.put("Action", new String[] { "__HORRORMUNGER__" } );
		inheritance.put("PrivateAction", new String[] { "Message", "PrivateEvent", "FilterEvent", });
		inheritance.put("ChannelAction", new String[] { "Message", "ChannelEvent", "FilterEvent", });
		inheritance.put("Notice", new String[] { "__HORRORMUNGER__" } );
		inheritance.put("PrivateNotice", new String[] { "Message", "PrivateEvent", });
		inheritance.put("ChannelNotice", new String[] { "Message", "ChannelEvent", });

		inheritance.put("ChannelInfo", new String[] { "IRCEvent", "ChannelEvent", });

		inheritance.put("ChannelMode", new String[] { "IRCEvent", "ChannelEvent", "ModeEvent", });
		inheritance.put("ChannelParamMode", new String[] { "ChannelMode", "ParamEvent", });
		inheritance.put("ChannelUserMode", new String[] { "ChannelMode", "AimedEvent", });

		inheritance.put("ChannelInvite", new String[] { "IRCEvent", "ChannelEvent", "UserEvent", "AimedEvent", });
		inheritance.put("ChannelJoin", new String[] { "IRCEvent", "ChannelEvent", "ContextEvent", "UserEvent", });
		inheritance.put("ChannelPart", new String[] { "IRCEvent", "ChannelEvent", "ContextEvent", "UserEvent", });
		inheritance.put("ChannelKick", new String[] { "IRCEvent", "MessageEvent", "ChannelEvent", "ContextEvent", "UserEvent", "AimedEvent", });
		inheritance.put("ChannelTopic", new String[] { "IRCEvent", "MessageEvent", "ChannelEvent", "ContextEvent", }); // !!! (extras)

		inheritance.put("QuitEvent", new String[] { "IRCEvent", "MessageEvent", "UserEvent", });

		inheritance.put("NickChange", new String[] { "IRCEvent", "UserEvent", "NickChangeEvent", });

		inheritance.put("UnknownEvent", new String[] { "IRCEvent", });

		inheritance.put("ChannelModes", new String[] { "IRCEvent", "ChannelEvent", "MultiModeEvent", });
		inheritance.put("UserModes", new String[] { "IRCEvent", "MultiModeEvent", });


		// Interfaces, and their parameters.
		interfaces.put("IRCEvent", new String[] { "methodName", "(long)millis", "(int)random" });
		interfaces.put("ChannelEvent", new String[] { "channel" });
		interfaces.put("PrivateEvent", new String[0] );
		interfaces.put("CommandEvent", new String[0] );
		interfaces.put("FilterEvent", new String[0] );
		interfaces.put("UserEvent", new String[] { "nick", "login", "hostname" });
		interfaces.put("MessageEvent", new String[] { "message" });
		interfaces.put("ModeEvent", new String[] { "mode", "(boolean)set" });
		interfaces.put("AimedEvent", new String[] { "target" });
		interfaces.put("MultiModeEvent", new String[] { "modes" });
		interfaces.put("NickChangeEvent", new String[] { "newNick" });
		interfaces.put("ContextEvent", new String[] { "(!String)context" });
		interfaces.put("ParamEvent", new String[] { "param" });


		// Things that go onto the cloneEvent line.
		overrides.put("MessageEvent", new String[] { "message" });


		// Handlers. Key = Handler name. Value is array of: Event name, then param name and param assumed value in pairs.
		handlers.add(new String[] { "Notice", "Notice", "nick", null, "login", null, "hostname", null, "target", null, "message", null, "channel", "target" } );
		handlers.add(new String[] { "Message", "ChannelMessage", "target", null, "nick", null, "login", null, "hostname", null, "message", null, "channel", "target" } );
		handlers.add(new String[] { "PrivateMessage", "PrivateMessage", "nick", null, "login", null, "hostname", null, "message", null, "target", "null" } );
		handlers.add(new String[] { "Action", "Action", "nick", null, "login", null, "hostname", null, "target", null, "message", null, "channel", "target" } );
		handlers.add(new String[] { "ChannelInfo", "ChannelInfo", "channel", null, "(int)userCount", null, "topic", null } );

		handlers.add(new String[] { "DeVoice", "ChannelUserMode", "channel", null, "nick", null, "login", null, "hostname", null, "target", null, "mode", "\"v\"", "(boolean)set", "false" } );
		handlers.add(new String[] { "Deop", "ChannelUserMode", "channel", null, "nick", null, "login", null, "hostname", null, "target", null, "mode", "\"o\"", "(boolean)set", "false" } );

		handlers.add(new String[] { "Invite", "ChannelInvite", "target", null, "nick", null, "login", null, "hostname", null, "channel", null } );
		handlers.add(new String[] { "Join", "ChannelJoin", "channel", null, "nick", null, "login", null, "hostname", null } );
		handlers.add(new String[] { "Kick", "ChannelKick", "channel", null, "nick", null, "login", null, "hostname", null, "target", null, "message", null } );
		handlers.add(new String[] { "Mode", "ChannelModes", "channel", null, "nick", null, "login", null, "hostname", null, "modes", null } );
		handlers.add(new String[] { "NickChange", "NickChange", "nick", null, "login", null, "hostname", null, "newNick", null } );
		handlers.add(new String[] { "Op", "ChannelUserMode", "channel", null, "nick", null, "login", null, "hostname", null, "target", null, "mode", "\"o\"", "(boolean)set", "true" } );
		handlers.add(new String[] { "Part", "ChannelPart", "channel", null, "nick", null, "login", null, "hostname", null } );
		handlers.add(new String[] { "Quit", "QuitEvent", "nick", null, "login", null, "hostname", null, "message", null } );

		handlers.add(new String[] { "RemoveChannelBan", "ChannelParamMode", "channel", null, "nick", null, "login", null, "hostname", null, "param", null, "mode", "\"b\"", "(boolean)set", "false" } );
		handlers.add(new String[] { "RemoveChannelKey", "ChannelParamMode", "channel", null, "nick", null, "login", null, "hostname", null, "param", null, "mode", "\"k\"", "(boolean)set", "false" } );
		handlers.add(new String[] { "RemoveChannelLimit", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"l\"", "(boolean)set", "false" } );
		handlers.add(new String[] { "RemoveInviteOnly", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"i\"", "(boolean)set", "false" } );
		handlers.add(new String[] { "RemoveModerated", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"m\"", "(boolean)set", "false" } );
		handlers.add(new String[] { "RemoveNoExternalMessages", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"n\"", "(boolean)set", "false" } );
		handlers.add(new String[] { "RemovePrivate", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"p\"", "(boolean)set", "false" } );
		handlers.add(new String[] { "RemoveSecret", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"s\"", "(boolean)set", "false" } );
		handlers.add(new String[] { "RemoveTopicProtection", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"t\"", "(boolean)set", "false" } );
		handlers.add(new String[] { "SetChannelBan", "ChannelParamMode", "channel", null, "nick", null, "login", null, "hostname", null, "param", null, "mode", "\"b\"", "(boolean)set", "true" } );
		handlers.add(new String[] { "SetChannelKey", "ChannelParamMode", "channel", null, "nick", null, "login", null, "hostname", null, "param", null, "mode", "\"k\"", "(boolean)set", "true" } );
		handlers.add(new String[] { "SetChannelLimit", "ChannelParamMode", "channel", null, "nick", null, "login", null, "hostname", null, "(int)prm", null, "param", "(String)prm", "mode", "\"l\"", "(boolean)set", "true" } );
		handlers.add(new String[] { "SetInviteOnly", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"i\"", "(boolean)set", "true" } );
		handlers.add(new String[] { "SetModerated", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"m\"", "(boolean)set", "true" } );
		handlers.add(new String[] { "SetNoExternalMessages", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"n\"", "(boolean)set", "true" } );
		handlers.add(new String[] { "SetPrivate", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"p\"", "(boolean)set", "true" } );
		handlers.add(new String[] { "SetSecret", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"s\"", "(boolean)set", "true" } );
		handlers.add(new String[] { "SetTopicProtection", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"t\"", "(boolean)set", "true" } );

		handlers.add(new String[] { "Topic", "ChannelTopic", "channel", null, "message", null, "nick", null, "(long)date", null, "(boolean)changed", null } );
		handlers.add(new String[] { "Unknown", "UnknownEvent", "line", null } );
		handlers.add(new String[] { "UserMode", "UserModes", "targetNick", null, "nick", null, "login", null, "hostname", null, "modes", null } );
		handlers.add(new String[] { "Voice", "ChannelUserMode", "channel", null, "nick", null, "login", null, "hostname", null, "target", null, "mode", "\"v\"", "(boolean)set", "true" } );
	}

	public static void main(String[] args)
	{
		(new HorriblePerlScript()).run();
	}

	public String getConstructorOrder(String className, Map<String,String> paramValueMap)
	{
		List<String> inherited = getInherit(className);
		boolean first = true;
		StringBuffer ret = new StringBuffer();
		for(String param: getParamNames(className, false))
		{
			if (first)
				first = false;
			else
				ret.append(", ");
			ret.append(paramValueMap.get(param));
		}
		return ret.toString();
	}

	public List<String> getParamNames(String className, boolean virtual)
	{
		List<String> ret = new ArrayList<String>();
		List<String> inherited = getInherit(className);
		Pattern pat = Pattern.compile("\\(([^)]+)\\)(.*)");
		for(String inheritedClassName: inherited)
		{
			String[] paramList = interfaces.get(inheritedClassName);
			if (paramList != null)
			{
				for(String name: paramList)
				{
					Matcher match = pat.matcher(name);
					if ( match.matches() )
					{
						if ( virtual || match.group(1).charAt(0) != '!' )
							ret.add(match.group(2));
					}
					else
						ret.add(name);
				}
			}
		}
		return ret;
	}

	public List<String> getMemberNames(String className)
	{
		List<String> ret = new ArrayList<String>();
		Pattern pat = Pattern.compile("\\(([^)]+)\\)(.*)");
		String[] inherited = inheritance.get(className);
		if (inherited == null)
			return ret;
		for(int i = 1; i<inherited.length; i++)
		{
			String[] paramList = interfaces.get(inherited[i]);
			if (paramList != null)
			{
				for(String name: paramList)
				{
					Matcher match = pat.matcher(name);
					if ( match.matches() )
					{
						if ( match.group(1).charAt(0) != '!' )
							ret.add(match.group(2));
					}
					else
						ret.add(name);
				}
			}
		}
		return ret;
	}

	public Map<String,String> getParamTypes(String className)
	{
		Map<String,String> ret = new HashMap<String,String>();
		List<String> inherited = getInherit(className);
		Pattern pat = Pattern.compile("\\(([^)]+)\\)(.*)");
		for(String inheritedClassName: inherited)
		{
			String[] paramList = interfaces.get(inheritedClassName);
			if (paramList != null)
			{
				for(String name: paramList)
				{
					Matcher match = pat.matcher(name);
					if ( match.matches() )
					{
						if ( match.group(1).charAt(0) != '!' )
							ret.put(match.group(2), match.group(1));
					}
					else
						ret.put(name, "String");
				}
			}
		}
		return ret;
	}

	public List<String> getOverrides(String className, boolean recurse)
	{
		List<String> ret = new ArrayList<String>();
		String[] inherited = inheritance.get(className);
		if (inherited == null)
			return ret;
		if (recurse)
			ret.addAll(getOverrides(inherited[0], true));
		for(int i=1; i<inherited.length; i++)
		{
			String[] overridden = overrides.get(inherited[i]);
			if (overridden != null)
				ret.addAll(Arrays.asList(overridden));
		}
		return ret;
	}

	public List<String> getInherit(String className)
	{
		List<String> ret = new ArrayList<String>();
		getInheritRecursive(className, ret);
		return ret;
	}

	public void getInheritRecursive(String className, List<String> ret)
	{
		ret.add(className);
		String[] inherit = inheritance.get(className);
		if (inherit == null)
			return;
		for(String superClass: inherit)
			getInheritRecursive(superClass, ret);
	}

	public void run()
	{
		/**
		 * Generate parser routines.
		 */
		Pattern typePattern = Pattern.compile("\\((.*)\\)(.*)");
		StringBuffer eventHandlers = new StringBuffer();
		for (String[] vals: handlers)
		{
			// Stuff to hold values:
			StringBuffer prototype = null;

			String handler = vals[0];
			String className = vals[1];

			System.out.println("Handling: " + handler);

			String evtName = "on" + handler;

			String[] paramName = new String[(vals.length - 2)/2 + 3];
			String[] paramValue = new String[(vals.length - 2)/2 + 3];
			String[] paramType = new String[(vals.length - 2)/2 + 3];
			Map<String,String> paramValueMap = new HashMap<String,String>();

			// Decode parameters in order.
			for(int i=2; i<vals.length; i += 2)
			{
				paramName[(i - 2)/2] = vals[i];
				paramValue[(i - 2)/2] = vals[i + 1];
			};

			// Common to all.
			paramName[paramName.length - 3] = "methodName";
			paramValue[paramName.length - 3] = "\"" + evtName + "\"";
			paramName[paramName.length - 2] = "(long)millis";
			paramValue[paramName.length - 2] = "System.currentTimeMillis()";
			paramName[paramName.length - 1] = "(int)random";
			paramValue[paramName.length - 1] = "((int)(Math.random()*127))";

			// Find out types.
			for( int i=0; i<paramName.length; i++ )
			{
				Matcher typeMatcher = typePattern.matcher(paramName[i]);
				if (typeMatcher.matches())
				{
					paramName[i] = typeMatcher.group(2);
					paramType[i] = typeMatcher.group(1);
				}
				else
				{
					paramType[i] = "String";
				}

				if (paramValue[i] == null)
				{
					if (prototype != null)
						prototype.append(", " + paramType[i] + " " + paramName[i]);
					else
						prototype = new StringBuffer(paramType[i] + " " + paramName[i]);

					paramValue[i] = paramName[i];
				}
				paramValueMap.put(paramName[i], paramValue[i]);
			}

			StringBuffer eventHandler = new StringBuffer("\tprotected void " + evtName + "(" + prototype + ") {\n");
			if (inheritance.get(className)[0].equals("__HORRORMUNGER__")) {
				// Need to horrormunge!
				String constructorParamsPublic = getConstructorOrder("Channel" + className, paramValueMap);

				paramValueMap.put("methodName", "\"onPrivate" + handler + "\"");
				String constructorParamsPrivate = getConstructorOrder("Private" + className, paramValueMap);

				eventHandler.append("\t\tif (target.indexOf('#') == 0)\n");
				eventHandler.append("\t\t\tspinThread(new Channel" + className + "(" + constructorParamsPublic + "));\n");
				eventHandler.append("\t\telse\n");
				eventHandler.append("\t\t\tspinThread(new Private" + className + "(" + constructorParamsPrivate + "));\n\t}\n\n");
			} else {
				String constructorParams = getConstructorOrder(className, paramValueMap);
				//my @constOrder = map { my @a=(); if ($params{$_}) { @a = @{$params{$_}} } (@a) } @inherit;
				//@constOrder = grep { !/\(\!/ } @constOrder;
				//map { s/\(\w+\)// } @constOrder;

				eventHandler.append("\t\tspinThread(new " + className + "(" + constructorParams + "));\n\t}\n\n");
			}
			System.out.println(eventHandler.toString());
			eventHandlers.append(eventHandler.toString());
		}
		System.out.println("Result:\n" + eventHandlers);
		// TODO Write to file!
/*
open CHOOB, "org/uwcs/choob/Choob.java";
my $choob = do { local $/; <CHOOB> };
close CHOOB;
$choob =~ s[(?<=// BEGIN PASTE!).*?(?=// END PASTE!)][\n\n$eventHandlers\t]s;
open CHOOB, ">org/uwcs/choob/Choob.java";
print CHOOB $choob;
close CHOOB; */

		/**
		 * Generate event classes
		 */
		for(String className: inheritance.keySet())
		{
			// Skip horrormungers.
			String[] directInheritance = inheritance.get(className);
			if (directInheritance != null && directInheritance[0].startsWith("__"))
				continue;

			List<String> inherited = getInherit(className);
			List<String> paramNames = getParamNames(className, true);
			List<String> realParamNames = getParamNames(className, false);
			List<String> memberVariables = getMemberNames(className);
			Map<String,String> paramTypes = getParamTypes(className);
			List<String> myOverrides = getOverrides(className, false);
			List<String> superInherited = directInheritance == null ? new ArrayList<String>() : getInherit(directInheritance[0]);
			List<String> superParams = directInheritance == null ? new ArrayList<String>() : getParamNames(directInheritance[0], false);
			List<String> superOverrides = directInheritance == null ? new ArrayList<String>() : getOverrides(directInheritance[0], true);

			boolean first; // Used for commas in loops.

			// Preamble.
			StringBuffer classContent = new StringBuffer("/**\n *\n * @author Horrible Perl Script. Ewwww.\n */\n\npackage org.uwcs.choob.support.events;\nimport org.uwcs.choob.support.events.*;\n\n");

			// Class description.
			classContent.append("public class ");
			classContent.append(className);
			classContent.append(" ");
			if (directInheritance != null)
			{
				// Not a root class, ie. IRCEvent.

				// Superclass
				classContent.append("extends " + directInheritance[0] + " ");

				// Interfaces
				first = true;
				for(int i=1; i<directInheritance.length; i++)
				{
					if (!first)
						classContent.append(", ");
					else
						classContent.append("implements ");
					first = false;
					classContent.append(directInheritance[i]);
				}
			}
			classContent.append("\n{\n");

			// Member variables now.
			for(String fieldName: memberVariables)
			{
				// Variable
				classContent.append("\t/**\n\t * " + fieldName + "\n\t */\n\tprivate final ");
				classContent.append(paramTypes.get(fieldName) + " ");
				classContent.append(fieldName);
				classContent.append(";\n\n");

				// Getter.
				boolean useIs = paramTypes.get(fieldName).equals("boolean");
				classContent.append("\t/**\n\t * Get the value of " + fieldName + "\n\t * @return The value of " + fieldName + "\n\t */\n");
				classContent.append("\tpublic ");
				classContent.append(paramTypes.get(fieldName) + " ");
				classContent.append(useIs ? "is" : "get");
				classContent.append("" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1));
				classContent.append("() {\n\t\t return ");
				classContent.append(fieldName);
				classContent.append(";\n\t}\n\n");
			}

			// Constructor 1
			classContent.append("\n\t/**\n\t * Construct a new ");
			classContent.append(className);
			classContent.append(".\n\t */\n\tpublic ");
			classContent.append(className);
			classContent.append("(");
			// Prototype.
			first = true;
			for(String paramName: realParamNames)
			{
				if (!first)
					classContent.append(", ");
				first = false;
				classContent.append(paramTypes.get(paramName) + " " + paramName);
			}
			classContent.append(")\n\t{\n\t\tsuper(");
			// super() params.
			first = true;
			for(String paramName: superParams)
			{
				if (!first)
					classContent.append(", ");
				first = false;
				classContent.append(paramName);
			}
			classContent.append(");\n");
			// Member variable init.
			for(String fieldName: memberVariables)
			{
				classContent.append("\t\tthis.");
				classContent.append(fieldName + " = " + fieldName + ";\n");
			}
			classContent.append("\t}\n\n");

			// Constructor 2
			classContent.append("\t/**\n\t * Synthesize a new " + className + " from an old one.\n");
			classContent.append("\t */\n\tpublic ");
			classContent.append(className);
			// Prototype.
			classContent.append("(");
			classContent.append(className + " old");
			for(String paramName: superOverrides)
				classContent.append(", " + paramTypes.get(paramName) + " " + paramName);
			for(String paramName: myOverrides)
				classContent.append(", " + paramTypes.get(paramName) + " " + paramName);
			classContent.append(")\n\t{\n\t\tsuper(old");
			// super() params.
			for(String paramName: superOverrides)
				classContent.append(", " + paramName);
			classContent.append(");\n");
			// Member variable init.
			for(String fieldName: memberVariables)
			{
				if (myOverrides.contains(fieldName))
					classContent.append("\t\tthis." + fieldName + " = " + fieldName + ";\n");
				else
					classContent.append("\t\tthis." + fieldName + " = old." + fieldName + ";\n");
			}
			classContent.append("\t}\n\n");

			// cloneEvent
			classContent.append("\t/**\n\t * Synthesize a new " + className + " from this one.\n");
			classContent.append("\t * @return The new " + className + " object.\n\t */\n\tpublic IRCEvent cloneEvent");
			// Prototype.
			classContent.append("(");
			first = true;
			for(String paramName: superOverrides)
			{
				if (!first)
					classContent.append(", ");
				first = false;
				classContent.append(paramTypes.get(paramName) + " " + paramName);
			}
			for(String paramName: myOverrides)
			{
				if (!first)
					classContent.append(", ");
				first = false;
				classContent.append(paramTypes.get(paramName) + " " + paramName);
			}
			// Body.
			classContent.append(")\n\t{\n\t\treturn new ");
			classContent.append(className);
			classContent.append("(this");
			for(String paramName: superOverrides)
				classContent.append(", " + paramName);
			for(String paramName: myOverrides)
				classContent.append(", " + paramName);
			classContent.append(");\n\t}\n\n");

			// equals
			classContent.append("\tpublic boolean equals(Object obj)\n\t{\n\t\tif (obj == null || !(obj instanceof ");
			classContent.append(className);
			classContent.append("))\n\t\t\treturn false;\n\t\t");
			if (directInheritance != null)
				classContent.append("if ( !super.equals(obj) )\n\t\t\treturn false;\n\t\t");
			classContent.append(className + " thing = (" + className + ")obj;\n\t\tif ( true");
			for(String fieldName: memberVariables)
			{
				if (paramTypes.get(fieldName).equals("String"))
					classContent.append(" && " + fieldName + ".equals(thing." + fieldName + ")");
				else
					classContent.append(" && (" + fieldName + " == thing." + fieldName + ")");
			}
			classContent.append(" )\n\t\t\treturn true;\n\t\treturn false;\n\t}\n\n");

			// toString
			classContent.append("\tpublic String toString()\n\t{\n\t\tStringBuffer out = new StringBuffer(\"");
			classContent.append(className + "(\");\n");
			if (directInheritance != null)
				classContent.append("\t\tout.append(super.toString())\n");
			for(String fieldName: memberVariables)
				classContent.append("\t\tout.append(\", " + fieldName + " = \" + " + fieldName + ");\n");
			classContent.append("\t\tout.append(\")\")\n");
			classContent.append("\t\treturn out.toString();\n");
			classContent.append("\t}\n\n");

			classContent.append("}\n");

			
			System.out.println("Class: " + classContent);
		}
	}
}


/*
$extra
}
	if ($inheritance{$class}) {
		my @i = @{$inheritance{$class}};
		if (grep /ContextEvent/, @inherit) {
			if (grep /UserEvent|ChannelEvent/, @i) {
				# Must re-implement getContext
				$extra .= END;
	/**
	 * Get the reply context in which this event resides
	 * \@return The context
	 *
END
				if (grep /ChannelEvent/, @inherit) {
					$extra .= END;
	public String getContext() {
		return getChannel();
	}
END
				} else {
					$extra .= END;
	public String getContext() {
		return getNick();
	}
END
				}
			}
		}

		# Now for synthetic overrides
	}

	if ($class eq "IRCEvent") {
		# Speshul things needed!
		push @constOrder, "synthLevel";
		$paramType{synthLevel} = "int";
	}

	if ($class eq "IRCEvent") {
		$memberInit1 = <<END;
		java.security.AccessController.checkPermission(new org.uwcs.choob.support.ChoobPermission("event.create"));
		this.methodName = methodName;
		this.millis = millis;
		this.random = random;
		this.synthLevel = 0;
END
		$memberInit2 = <<END;
		java.security.AccessController.checkPermission(new org.uwcs.choob.support.ChoobPermission("event.create"));
		this.methodName = old.methodName;
		this.millis = old.millis;
		this.random = old.random;
		this.synthLevel = old.synthLevel + 1;
END
	}

	open CLASS, ">org/uwcs/choob/support/events/$class.java";
	print CLASS $classdef;
	close CLASS

	}


}

foreach my $interface (keys %params) {
	next if $interface eq 'IRCEvent';
	my $getters = "";
	foreach (@{$params{$interface}}) {
		my $paramType;

		if (s/\((!)?(\w+)\)//) {
			$paramType = $2;
		} else {
			$paramType = "String";
		}

		my $get = ($paramType eq 'boolean') ? 'is' : 'get';
		$getters .= <<END;
	/**
	 * Get the value of $_
	 * \@return The value of $_
	 *
	public $paramType $get\\u$_();

END
	}
	my $classdef = <<END;
/**
 *
 * \@author Horrible Perl Script. Ewwww.
 *

package org.uwcs.choob.support.events;
import org.uwcs.choob.support.events.*;
 
public interface $interface
{
$getters
}
END

	open CLASS, ">org/uwcs/choob/support/events/$interface.java";
	print CLASS $classdef;
	close CLASS
}

sub getInherit {
	my $class = shift;
	my @stuff = ($class);
	if ($inheritance{$class}) {
		return ((map { (&getInherit($_)) } @{$inheritance{$class}}), $class);
	} else {
		return ($class);
	}
}



# IGNORE THESE EVENTS FOR NOW
#protected void onNickChange(String oldNick, String login, String hostname, String newNick)
# Handled internally in pircBot, overriding causes breakage, don't let it happen:
#protected void onFinger(String sourceNick, String sourceLogin, String sourceHostname, String target) { spinThread(new ChannelEvent(ChannelEvent.ce_Finger, new String[] {sourceNick, sourceLogin, sourceHostname, target })); }
#protected void onPing(String sourceNick, String sourceLogin, String sourceHostname, String target, String pingValue) { spinThread(new ChannelEvent(ChannelEvent.ce_Ping, new String[] {sourceNick, sourceLogin, sourceHostname, target, pingValue})); }
#protected void onServerPing(String response) { spinThread(new ChannelEvent(ChannelEvent.ce_ServerPing, new String[] {response  })); }
#protected void onServerResponse(int code, String response) { spinThread(new ChannelEvent(ChannelEvent.ce_ServerResponse, new String[] {Integer.toString(code), response  })); }
#protected void onTime(String sourceNick, String sourceLogin, String sourceHostname, String target) { spinThread(new ChannelEvent(ChannelEvent.ce_Time, new String[] {sourceNick, sourceLogin, sourceHostname, target })); }
#protected void onVersion(String sourceNick, String sourceLogin, String sourceHostname, String target) { spinThread(new ChannelEvent(ChannelEvent.ce_Version, new String[] {sourceNick, sourceLogin, sourceHostname, target })); }

# Protect against RFC breakage.

# Handled elsewhere in this file, for now:
#protected void onMessage(String channel, String sender, String login, String hostname, String message) { spinThread(new ChannelEvent(ChannelEvent.ce_Message, new String[] {channel, sender, login, hostname, message})); }
#protected void onPrivateMessage(String sender, String login, String hostname, String message) { spinThread(new ChannelEvent(ChannelEvent.ce_PrivateMessage, new String[] {sender, login, hostname, message })); }


# THESE EVENTS ARE REAL
__DATA__
*/
