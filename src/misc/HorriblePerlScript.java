/**
 * Class to generate events.
 * @author bucko
 */

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HorriblePerlScript
{
	// Inheritance map
	private final Map<String,String[]> inheritance = new HashMap<String,String[]>();
	private final Map<String,String[]> interfaces = new HashMap<String,String[]>();
	private final List<String> notinterfaces = new ArrayList<String>();
	private final Map<String,String[]> overrides = new HashMap<String,String[]>();
	private final List<String[]> handlers = new ArrayList<String[]>();
	private final List<String> notprotected = new ArrayList<String>();

	public HorriblePerlScript()
	{
		// Classes, and which intrefaces/classes the inherit from.
		inheritance.put("Event", null);

		inheritance.put("InternalEvent", new String[] { "Event", "InternalRootEvent" } );

		inheritance.put("PluginLoaded", new String[] { "InternalEvent", "PluginEvent" } );
		inheritance.put("PluginUnLoaded", new String[] { "InternalEvent", "PluginEvent" } );
		inheritance.put("PluginReLoaded", new String[] { "InternalEvent", "PluginEvent" } );

		inheritance.put("IRCEvent", new String[] { "Event", "IRCRootEvent" } );

		inheritance.put("Message", new String[] { "IRCEvent", "MessageEvent", "ContextEvent", "UserEvent", "AimedEvent", });

		inheritance.put("PrivateMessage", new String[] { "Message", "PrivateEvent", "CommandEvent", "FilterEvent", });
		inheritance.put("ChannelMessage", new String[] { "Message", "ChannelEvent", "CommandEvent", "FilterEvent", });
		inheritance.put("Action", new String[] { "__HORRORMUNGER__" } );
		inheritance.put("PrivateAction", new String[] { "Message", "PrivateEvent", "ActionEvent", "FilterEvent", });
		inheritance.put("ChannelAction", new String[] { "Message", "ChannelEvent", "ActionEvent", "FilterEvent", });
		inheritance.put("Notice", new String[] { "__HORRORMUNGER__" } );
		inheritance.put("PrivateNotice", new String[] { "Message", "PrivateEvent", });
		inheritance.put("ChannelNotice", new String[] { "Message", "ChannelEvent", });

		inheritance.put("ChannelInfo", new String[] { "IRCEvent", "MessageEvent", "ChannelEvent", });

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
		inheritance.put("ServerResponse", new String[] { "IRCEvent", "ServerEvent", });

		inheritance.put("ChannelModes", new String[] { "IRCEvent", "ChannelEvent", "MultiModeEvent", });
		inheritance.put("UserModes", new String[] { "IRCEvent", "MultiModeEvent", });


		// Interfaces, and their parameters.
		interfaces.put("Event", new String[] { "methodName" });
		interfaces.put("InternalRootEvent", new String[0]);
		interfaces.put("PluginEvent", new String[] { "pluginName", "(int)pluginStatus" });
		interfaces.put("IRCRootEvent", new String[] { "(long)millis", "(int)random" });
		interfaces.put("ChannelEvent", new String[] { "channel" });
		interfaces.put("PrivateEvent", new String[0] );
		interfaces.put("CommandEvent", new String[0] );
		interfaces.put("ActionEvent", new String[0] );
		interfaces.put("FilterEvent", new String[0] );
		interfaces.put("UserEvent", new String[] { "nick", "login", "hostname" });
		interfaces.put("MessageEvent", new String[] { "message" });
		interfaces.put("ModeEvent", new String[] { "mode", "(boolean)set" });
		interfaces.put("AimedEvent", new String[] { "target" });
		interfaces.put("MultiModeEvent", new String[] { "modes" });
		interfaces.put("NickChangeEvent", new String[] { "newNick" });
		interfaces.put("ContextEvent", new String[] { "(!String)context" });
		interfaces.put("ParamEvent", new String[] { "param" });
		interfaces.put("ServerEvent", new String[] { "(int)code", "response" });

		notinterfaces.add("Event");

		// Things that go onto the cloneEvent line.
		overrides.put("MessageEvent", new String[] { "message" });


		// Handlers. Key = Handler name. Value is array of: Event name, then param name and param assumed value in pairs.
		handlers.add(new String[] { "PluginLoaded", "PluginLoaded", "pluginName", null, "pluginStatus", "1" } );
		handlers.add(new String[] { "PluginReLoaded", "PluginReLoaded", "pluginName", null, "pluginStatus", "0" } );
		handlers.add(new String[] { "PluginUnLoaded", "PluginUnLoaded", "pluginName", null, "pluginStatus", "-1" } );

		handlers.add(new String[] { "Notice", "Notice", "nick", null, "login", null, "hostname", null, "target", null, "message", null, "channel", "target" } );
		handlers.add(new String[] { "Message", "ChannelMessage", "target", null, "nick", null, "login", null, "hostname", null, "message", null, "channel", "target" } );
		handlers.add(new String[] { "PrivateMessage", "PrivateMessage", "nick", null, "login", null, "hostname", null, "message", null, "target", "null" } );
		handlers.add(new String[] { "Action", "Action", "nick", null, "login", null, "hostname", null, "target", null, "message", null, "channel", "target" } );
		handlers.add(new String[] { "ChannelInfo", "ChannelInfo", "channel", null, "(int)userCount", null, "message", null } );

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
		handlers.add(new String[] { "SetChannelLimit", "ChannelParamMode", "channel", null, "nick", null, "login", null, "hostname", null, "(int)prm", null, "param", "String.valueOf(prm)", "mode", "\"l\"", "(boolean)set", "true" } );
		handlers.add(new String[] { "SetInviteOnly", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"i\"", "(boolean)set", "true" } );
		handlers.add(new String[] { "SetModerated", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"m\"", "(boolean)set", "true" } );
		handlers.add(new String[] { "SetNoExternalMessages", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"n\"", "(boolean)set", "true" } );
		handlers.add(new String[] { "SetPrivate", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"p\"", "(boolean)set", "true" } );
		handlers.add(new String[] { "SetSecret", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"s\"", "(boolean)set", "true" } );
		handlers.add(new String[] { "SetTopicProtection", "ChannelMode", "channel", null, "nick", null, "login", null, "hostname", null, "mode", "\"t\"", "(boolean)set", "true" } );

		handlers.add(new String[] { "Topic", "ChannelTopic", "channel", null, "message", null, "nick", null, "(long)date", null, "(boolean)changed", null } );
		handlers.add(new String[] { "Unknown", "UnknownEvent", "line", null } );
		handlers.add(new String[] { "ServerResponse", "ServerResponse", "(int)code", null, "response", null } );
		handlers.add(new String[] { "UserMode", "UserModes", "targetNick", null, "nick", null, "login", null, "hostname", null, "modes", null } );
		handlers.add(new String[] { "Voice", "ChannelUserMode", "channel", null, "nick", null, "login", null, "hostname", null, "target", null, "mode", "\"v\"", "(boolean)set", "true" } );

		notprotected.add("PluginLoaded");
		notprotected.add("PluginReLoaded");
		notprotected.add("PluginUnLoaded");
	}

	public static void main(String[] args)
	{
		(new HorriblePerlScript()).run();
	}

	public String getConstructorOrder(String className, Map<String,String> paramValueMap)
	{
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
		// IRCEvent
		if (inherited == null)
			inherited = new String[] { null, className };
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
						else
							ret.put(match.group(2), match.group(1).substring(1));
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

	public void saveToFile(String className, String classContent)
	{
		try
		{
			OutputStream stream = new FileOutputStream("uk/co/uwcs/choob/support/events/" + className + ".java");
			PrintWriter writer = new PrintWriter(stream);
			writer.print(classContent);
			writer.close();
		}
		catch (IOException e)
		{
			// We don't need particularly neat errors or anything, so...
			throw new RuntimeException(e);
		}
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

			StringBuffer eventHandler = new StringBuffer("\t");
			if (!notprotected.contains(handler))
				eventHandler.append("protected ");
			else
				eventHandler.append("public ");
			eventHandler.append("void " + evtName + "(" + prototype + ") {\n");
			if (inheritance.get(className)[0].equals("__HORRORMUNGER__")) {
				// Need to horrormunge!
				String constructorParamsPublic = getConstructorOrder("Channel" + className, paramValueMap);

				paramValueMap.put("methodName", "\"onPrivate" + handler + "\"");
				String constructorParamsPrivate = getConstructorOrder("Private" + className, paramValueMap);

				eventHandler.append("\t\tif (target.indexOf('#') == 0)\n");
				eventHandler.append("\t\t\tspinThread(new Channel" + className + "(" + constructorParamsPublic + "));\n");
				eventHandler.append("\t\telse\n");
				eventHandler.append("\t\t\tspinThread(new Private" + className + "(" + constructorParamsPrivate + "));\n\t}\n\n");

			} else if (className.equals("NickChange")) {
				String constructorParams = getConstructorOrder(className, paramValueMap);

				eventHandler.append("\t\t// Force update of name to match nick, as PircBot confuses the two.\n");
				eventHandler.append("\t\tthis.setName(this.getNick());\n");
				eventHandler.append("\t\tspinThread(new " + className + "(" + constructorParams + "));\n\t}\n\n");
			} else {
				String constructorParams = getConstructorOrder(className, paramValueMap);
				//my @constOrder = map { my @a=(); if ($params{$_}) { @a = @{$params{$_}} } (@a) } @inherit;
				//@constOrder = grep { !/\(\!/ } @constOrder;
				//map { s/\(\w+\)// } @constOrder;

				eventHandler.append("\t\tspinThread(new " + className + "(" + constructorParams + "));\n\t}\n\n");
			}
			eventHandlers.append(eventHandler.toString());
		}

		try
		{
			// Sigh, Java makes this so complicated...
			StringBuffer choob = new StringBuffer();
			FileReader input = new FileReader("uk/co/uwcs/choob/Choob.java");
			// Read it all!
			char[] buffer = new char[16384];
			int found;
			while((found = input.read(buffer,0,buffer.length)) > 0)
				choob.append(buffer,0,found);
			input.close();

			String choobData = choob.toString();
			choobData = Pattern.compile("(?<=// BEGIN PASTE!).*?(?=// END PASTE!)", Pattern.DOTALL).matcher(choobData).replaceFirst("\n\n" + eventHandlers + "\t");

			// Yet I can write the file like this...
			OutputStream stream = new FileOutputStream("uk/co/uwcs/choob/Choob.java");
			PrintWriter writer = new PrintWriter(stream);
			writer.print(choobData);
			writer.close();
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}

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
			List<String> realParamNames = getParamNames(className, false);
			List<String> memberVariables = getMemberNames(className);
			Map<String,String> paramTypes = getParamTypes(className);
			List<String> myOverrides = getOverrides(className, false);
			List<String> superParams = directInheritance == null ? new ArrayList<String>() : getParamNames(directInheritance[0], false);
			List<String> superOverrides = directInheritance == null ? new ArrayList<String>() : getOverrides(directInheritance[0], true);

			boolean first; // Used for commas in loops.

			// Preamble.
			StringBuffer classContent = new StringBuffer("/**\n *\n * @author Horrible Perl Script. Ewwww.\n */\n\npackage uk.co.uwcs.choob.support.events;\n\n");

			if (className.equals("IRCEvent"))
			{
				classContent.append("import java.util.*;\n\n");
			}

			// Class description.
			classContent.append("public class ");
			classContent.append(className);
			classContent.append(" ");
			if (directInheritance != null)
			{
				// Not a root class.

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

			// Hack to get IRCEvent property in
			if (className.equals("IRCEvent"))
			{
				memberVariables.add("synthLevel");
				paramTypes.put("synthLevel", "int");
				memberVariables.add("flags");
				paramTypes.put("flags", "Map<String,String>");
			}

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

			// Special case: ContextEvent!
			if (directInheritance != null && inherited.contains("ContextEvent"))
			{
				classContent.append("\t/**\n\t * Get the reply context in which this event resides\n\t * @return The context\n\t */\n");
				if (inherited.contains("ChannelEvent")) {
					classContent.append("\tpublic String getContext() {\n\t\treturn getChannel();\n\t}\n\n");
				} else {
					classContent.append("\tpublic String getContext() {\n\t\treturn getNick();\n\t}\n\n");
				}
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
			classContent.append(")\n\t{\n");
			// Call super if we're not the root.
			if (directInheritance != null)
			{
				classContent.append("\t\tsuper(");
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
			}
			// Member variable init.
			for(String fieldName: memberVariables)
			{
				// Hack: Skip this.
				if (fieldName.equals("synthLevel") || fieldName.equals("flags"))
					continue;
				classContent.append("\t\tthis.");
				classContent.append(fieldName + " = " + fieldName + ";\n");
			}
			// IRCEvent hack.
			if (className.equals("IRCEvent"))
			{
				classContent.append("\t\tjava.security.AccessController.checkPermission(new uk.co.uwcs.choob.support.ChoobPermission(\"event.create\"));\n");
				classContent.append("\t\tthis.synthLevel = 0;\n");
				classContent.append("\t\tthis.flags = new HashMap<String,String>();\n");
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
			classContent.append(")\n\t{\n");
			// Call super if we're not the root.
			if (directInheritance != null)
			{
				classContent.append("\t\tsuper(old");
				// super() params.
				for(String paramName: superOverrides)
					classContent.append(", " + paramName);
				classContent.append(");\n");
			}
			// Member variable init.
			for(String fieldName: memberVariables)
			{
				// Hack: Skip this.
				if (fieldName.equals("synthLevel") || fieldName.equals("flags"))
					continue;
				if (myOverrides.contains(fieldName))
					classContent.append("\t\tthis." + fieldName + " = " + fieldName + ";\n");
				else
					classContent.append("\t\tthis." + fieldName + " = old." + fieldName + ";\n");
			}
			// IRCEvent hack.
			if (className.equals("IRCEvent"))
			{
				classContent.append("\t\tjava.security.AccessController.checkPermission(new uk.co.uwcs.choob.support.ChoobPermission(\"event.create\"));\n");
				classContent.append("\t\tthis.synthLevel = old.synthLevel + 1;\n");
				classContent.append("\t\tthis.flags = new HashMap<String,String>();\n");
				classContent.append("\t\t// Properties starting \"_\" should not be cloned implicitly.\n");
				classContent.append("\t\tfor (String prop : old.flags.keySet()) {\n");
				classContent.append("\t\t\tif (!prop.startsWith(\"_\"))\n");
				classContent.append("\t\t\t\tthis.flags.put(prop, new String((old.flags.get(prop))));\n");
				classContent.append("\t\t}\n");
			}
			classContent.append("\t}\n\n");

			// cloneEvent
			classContent.append("\t/**\n\t * Synthesize a new " + className + " from this one.\n");
			classContent.append("\t * @return The new " + className + " object.\n\t */\n\tpublic Event cloneEvent");
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
			if (memberVariables.size() == 0)
				classContent.append("\treturn true;\n\t}\n\n");
			else
			{
				classContent.append(className + " thing = (" + className + ")obj;\n\t\tif ( true");
				for(String fieldName: memberVariables)
				{
					if (paramTypes.get(fieldName).equals("String"))
						classContent.append(" && " + fieldName + ".equals(thing." + fieldName + ")");
					else
						classContent.append(" && (" + fieldName + " == thing." + fieldName + ")");
				}
				classContent.append(" )\n\t\t\treturn true;\n\t\treturn false;\n\t}\n\n");
			}

			// toString
			classContent.append("\tpublic String toString()\n\t{\n\t\tStringBuffer out = new StringBuffer(\"");
			classContent.append(className + "(\");\n");
			if (directInheritance != null)
				classContent.append("\t\tout.append(super.toString());\n");
			for(String fieldName: memberVariables)
				classContent.append("\t\tout.append(\", " + fieldName + " = \" + " + fieldName + ");\n");
			classContent.append("\t\tout.append(\")\");\n");
			classContent.append("\t\treturn out.toString();\n");
			classContent.append("\t}\n\n");

			classContent.append("}\n");

			saveToFile(className, classContent.toString());
		}

		/**
		 * Interfaces.
		 */
		for(String interfaceName: interfaces.keySet())
		{
			// Not an interface; this is an interfacial superclass. Or something.
			if (notinterfaces.contains(interfaceName))
				continue;

			List<String> paramNames = getParamNames(interfaceName, true);
			Map<String,String> paramTypes = getParamTypes(interfaceName);

			// Preamble
			StringBuffer classContent = new StringBuffer("/**\n *\n * @author Horrible Perl Script. Ewwww.\n */\n\npackage uk.co.uwcs.choob.support.events;\n\npublic interface ");

			// Interface name.
			classContent.append(interfaceName);
			classContent.append("\n{\n");

			// Getters.
			for(String fieldName: paramNames)
			{
				boolean useIs = paramTypes.get(fieldName).equals("boolean");
				classContent.append("\t/**\n\t * Get the value of " + fieldName + "\n\t * @return The value of " + fieldName + "\n\t */\n");
				classContent.append("\tpublic ");
				classContent.append(paramTypes.get(fieldName) + " ");
				classContent.append(useIs ? "is" : "get");
				classContent.append("" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1));
				classContent.append("();\n\n");
			}

			classContent.append("}");

			saveToFile(interfaceName, classContent.toString());
		}
	}
}


/*
# IGNORE THESE EVENTS FOR NOW
#protected void onNickChange(String oldNick, String login, String hostname, String newNick)
# Handled internally in pircBot, overriding causes breakage, don't let it happen:
#protected void onFinger(String sourceNick, String sourceLogin, String sourceHostname, String target) { spinThread(new ChannelEvent(ChannelEvent.ce_Finger, new String[] {sourceNick, sourceLogin, sourceHostname, target })); }
#protected void onPing(String sourceNick, String sourceLogin, String sourceHostname, String target, String pingValue) { spinThread(new ChannelEvent(ChannelEvent.ce_Ping, new String[] {sourceNick, sourceLogin, sourceHostname, target, pingValue})); }
#protected void onServerPing(String response) { spinThread(new ChannelEvent(ChannelEvent.ce_ServerPing, new String[] {response  })); }
#protected void onServerResponse(int code, String response) { spinThread(new ChannelEvent(ChannelEvent.ce_ServerResponse, new String[] {Integer.toString(code), response  })); }
#protected void onTime(String sourceNick, String sourceLogin, String sourceHostname, String target) { spinThread(new ChannelEvent(ChannelEvent.ce_Time, new String[] {sourceNick, sourceLogin, sourceHostname, target })); }
#protected void onVersion(String sourceNick, String sourceLogin, String sourceHostname, String target) { spinThread(new ChannelEvent(ChannelEvent.ce_Version, new String[] {sourceNick, sourceLogin, sourceHostname, target })); }*/
