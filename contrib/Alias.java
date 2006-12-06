import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;

public class AliasObject
{
	public AliasObject(String name, String converted, String owner)
	{
		this.name = name;
		this.converted = converted;
		this.owner = owner;
		this.locked = false;
		this.id = 0;
	}
	public AliasObject() {}
	public int id;
	public String name;
	public String converted;
	public String owner;
	public boolean locked;
	public String help;
	public String core;
}

public class Alias
{
	public String[] info()
	{
		return new String[] {
			"Command aliasing plugin.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private static int MAX_ALIAS_EXPANSIONS = 2; // Maximum number of alias expansions before we stop.

	private Modules mods;
	private IRCInterface irc;
	public Alias(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public String[] helpTopics = { "Syntax", "HelpExamples" };

	public String[] helpSyntax = {
		  "Aliases use a clever alias syntax: You can either just give a"
		+ " simple string with no '$' characters, in which case any user"
		+ " parameters will be appended to the command. Alternatively, you can"
		+ " give a string with '$' characters, in which case, '$i' is replaced"
		+ " with parameter #i, '$$' is replaced simply with '$', '$[i-j]' is"
		+ " replaced with parameters #i through #j with '$[i-]' and '$[-j]'"
		+ " defined in the obvious way, and '$.' is replaced with nothing.",
		  "Example: If 'Foo' is aliased to 'Bar.Baz', 'Foo 1 2 3' will become"
		+ " 'Bar.Baz 1 2 3'. If 'Foo' is aliased to 'Bar.Baz$.', 'Foo 1 2 3'"
		+ " will become 'Bar.Baz', and if 'Foo' is aliased to"
		+ " 'Bar.Baz $3 $[1-2]', 'Foo 1 2 3' will become 'Bar.Baz 3 1 2'."
	};

	public String[] helpCommandAlias = {
		"Shows an existing alias, or adds a new alias to the bot.",
		"<Name> [<Alias>]",
		"<Name> is the name of the alias to show/add",
		"<Alias> is the alias content. See Alias.Syntax"
	};
	public void commandAlias( Message mes )
	{
		String[] params = mods.util.getParamArray(mes, 2);

		if (params.length == 2)
		{
			String name = params[1];

			AliasObject alias = getAlias(name);

			if (alias == null)
				irc.sendContextReply(mes, "Alias not found.");
			else
				irc.sendContextReply(mes, "'" + alias.name + "'" + (alias.locked ? " (LOCKED)" : "") + " was aliased to '" + alias.converted + "' by '" + alias.owner + "'.");

			return;
		}

		if (params.length <= 2)
		{
			throw new ChoobBadSyntaxError();
		}

		String name = params[1];
		String conv = params[2];

		// Validate name against unprintable characters.
		for (int i = 0; i < name.length(); i++)
			if (name.charAt(i) < 32)
			{
				irc.sendContextReply(mes, "Alias name contains disallowed characters. Try again!");
				return;
			}


		int spacePos = conv.indexOf(' ');
		final String actualParams;
		if (spacePos == -1)
		{
			spacePos = conv.length();
			actualParams = "";
		}
		else
			actualParams = conv.substring(spacePos + 1);

		final String subAlias = conv.substring(0, spacePos);

		// This is slightly inefficient as it'll recall the alias twice if it's recursive. Bah.
		if (!mods.plugin.validCommand(subAlias))
		{
			irc.sendContextReply(mes, "Sorry, you tried to create an alias to '" + subAlias + "', but it doesn't exist!");
			return;
		}


		// It doesn't have a dot in it, or (it has a space in it, and the dot is after the space).
		if (conv.indexOf('.') == -1 || (conv.indexOf(' ') != -1 && conv.indexOf(' ') < conv.indexOf('.')))
		{
			// Alias recursion?

			AliasObject alias = getAlias(subAlias);

			if (alias == null)
			{
				irc.sendContextReply(mes, "Sorry, you tried to use a recursive alias to '" + subAlias + "' - but '" + subAlias + "' doesn't exist!");
				return;
			}

			String aliasText = alias.converted;

			// Rebuild params with no upper limit.
			params = mods.util.getParamArray(mes);
			String[] aliasParams = new String[params.length - 2];
			for(int i=2; i<params.length; i++)
				aliasParams[i-2] = params[i];

			String newText = applyAlias(subAlias, alias.converted, aliasParams, actualParams, mes);
			if (newText == null)
			{
				irc.sendContextReply(mes, "Sorry, you tried to use a recursive alias to '" + subAlias + "' - but the alias text ('" + alias.converted + "') is invalid!");
				return;
			}
			conv = newText;
		}

		if (name.equals(""))
		{
			irc.sendContextReply(mes, "Syntax: Alias.Add <Name> <Alias>");
			return;
		}

		AliasObject alias = getAlias(name);

		String nick = mods.security.getRootUser(mes.getNick());
		if (nick == null)
			nick = mes.getNick();

		String oldAlias = ""; // Set to content of old alias, if there was one.
		if (alias != null)
		{
			if (alias.locked)
			{
				if (alias.owner.toLowerCase().equals(nick.toLowerCase()))
					mods.security.checkNS(mes);
				else
					mods.security.checkNickPerm(new ChoobPermission("plugin.alias.unlock"), mes);
			}

			oldAlias = " (was '" + alias.converted + "')";

			alias.converted = conv;
			alias.owner = nick;

			mods.odb.update(alias);
		}
		else
			mods.odb.save(new AliasObject(name, conv, nick));

		irc.sendContextReply(mes, "Aliased '" + name + "' to '" + conv + "'" + oldAlias + ".");
	}

	public String[] helpCommandSetCoreAlias = {
		"Make an alias a 'core' alias.",
		"<Command> <Alias>",
		"<Command> is the name of the command",
		"<Alias> is the name of the alias"
	};
	public void commandSetCoreAlias(Message mes) {
		String[] params = mods.util.getParamArray(mes);

		if (params.length != 3)
		{
			throw new ChoobBadSyntaxError();
		}

		String command = params[1];
		String aliasName = params[2];

		mods.security.checkNickPerm(new ChoobPermission("plugin.alias.setcore"), mes);

		// Sanity check
		AliasObject alias = getAlias(aliasName);
		if (alias == null)
		{
			irc.sendContextReply(mes, "Alias " + aliasName + " does not exist!");
			return;
		}

		alias.core = command;
		mods.odb.update(alias);

		irc.sendContextReply(mes, "OK, " + command + " set to have " + aliasName + " as core alias!");
	}

	public String[] helpHelpExamples = {
		"Some alias help examples:",
		"'Alias.AddHelp dance Dance with someone! ||| [ <Nick> ] ||| <Nick> is someone to dance with'",
		"'Alias.AddHelp time Get the current time.'",
		"'Alias.AddHelp msg Send a message to someone. ||| <Target> <Message> ||| <Target> is the person to send to ||| <Message> is the message to send'"
	};

	public String[] helpCommandAddHelp = {
		"Add help to an alias. See HelpExamples for examples.",
		"<Name> <Summary> [ ||| <Syntax> [ ||| <Param> <Description> [ ||| <Param> <Description> ... ] ] ]",
		"<Name> is the name of the alias to alter",
		"<Summary> is a brief summary of the command",
		"<Syntax> is the syntax, minus the command name",
		"<Param> is a parameter from the syntax",
		"<Description> is the description of that parameter"
	};
	public void commandAddHelp(Message mes) {
		String[] params = mods.util.getParamArray(mes, 2);

		if (params.length <= 2)
		{
			throw new ChoobBadSyntaxError();
		}

		String name = params[1];

		// TODO - check help.
		String[] help = params[2].split("\\s*\\|\\|\\|\\s*");

		AliasObject alias = getAlias(name);

		String nick = mods.security.getRootUser(mes.getNick());
		if (nick == null)
			nick = mes.getNick();

		if (alias != null)
		{
			if (alias.locked)
			{
				if (alias.owner.toLowerCase().equals(nick.toLowerCase()))
					mods.security.checkNS(mes);
				else
					mods.security.checkNickPerm(new ChoobPermission("plugin.alias.unlock"), mes);
			}

			alias.help = params[2];
			alias.owner = nick;

			mods.odb.update(alias);

			irc.sendContextReply(mes, "OK, " + help.length + " lines of help created for '" + name + "'.");
		}
		else
		{
			irc.sendContextReply(mes, "Sorry, couldn't find an alias of that name!");
		}
	}

	public String[] helpCommandRemoveHelp = {
		"Remove help from an alias.",
		"<Name>",
		"<Name> is the name of the alias to alter"
	};
	public void commandRemoveHelp(Message mes) {
		String[] params = mods.util.getParamArray(mes);

		if (params.length != 2)
		{
			throw new ChoobBadSyntaxError();
		}

		String name = params[1];

		if (name.equals(""))
		{
			irc.sendContextReply(mes, "Syntax: 'Alias.RemoveHelp " + helpCommandRemoveHelp[1] + "'.");
			return;
		}

		AliasObject alias = getAlias(name);

		String nick = mods.security.getRootUser(mes.getNick());
		if (nick == null)
			nick = mes.getNick();

		if (alias != null)
		{
			if (alias.locked)
			{
				if (alias.owner.toLowerCase().equals(nick.toLowerCase()))
					mods.security.checkNS(mes);
				else
					mods.security.checkNickPerm(new ChoobPermission("plugin.alias.unlock"), mes);
			}

			alias.help = null;
			alias.owner = nick;

			mods.odb.update(alias);

			irc.sendContextReply(mes, "OK, removed help for '" + name + "'.");
		}
		else
		{
			irc.sendContextReply(mes, "Sorry, couldn't find an alias of that name!");
		}
	}

	public String[] helpCommandRemove = {
		"Remove an alias from the bot.",
		"<Name>",
		"<Name> is the name of the alias to remove",
	};
	public void commandRemove( Message mes )
	{
		String[] params = mods.util.getParamArray(mes);

		if (params.length != 2)
		{
			throw new ChoobBadSyntaxError();
		}

		String name = params[1];

		AliasObject alias = getAlias(name);

		String nick = mods.security.getRootUser(mes.getNick());
		if (nick == null)
			nick = mes.getNick();

		if (alias != null)
		{
			if (alias.locked)
			{
				if (alias.owner.toLowerCase().equals(nick.toLowerCase()))
					mods.security.checkNS(mes);
				else
					mods.security.checkNickPerm(new ChoobPermission("plugin.alias.unlock"), mes);
			}

			mods.odb.delete(alias);

			irc.sendContextReply(mes, "Deleted '" + alias.name + "', was aliased to '" + alias.converted + "'.");
		}
		else
			irc.sendContextReply(mes, "Alias not found.");
	}

	public String[] helpCommandList = {
		"List all aliases.",
		"[<Which>]",
		"<Which> is either 'locked', or 'unlocked' or 'all' (default: 'locked')"
	};
	public void commandList( Message mes )
	{
		String params[] = mods.util.getParamArray(mes);

		String clause = "locked = 1";
		if (params.length > 2)
			throw new ChoobBadSyntaxError();
		else if (params.length == 2)
		{
			if (params[1].equals("locked"))
				clause = "locked = 1";
			else if (params[1].equals("unlocked"))
				clause = "locked = 0";
			else if (params[1].equals("all"))
				clause = "1";
			else
				throw new ChoobBadSyntaxError();
		}

		List<AliasObject> results = mods.odb.retrieve( AliasObject.class, "WHERE " + clause );

		if (results.size() == 0)
			irc.sendContextReply(mes, "No aliases.");
		else
		{
			String list = "Alias list: ";
			for (int i=0; i < results.size(); i++)
			{
				list += results.get(i).name;
				if (results.get(i).locked)
					list += "*";
				if (i < results.size() - 2)
					list += ", ";
				else if (i == results.size() - 2)
				{
					if (i == 0)
						list += " and ";
					else
						list += ", and ";
				}
			}
			list += ".";
			irc.sendContextReply(mes, list);
		}
	}

	public String apiGet( String name )
	{
		if (name == null)
			return null;

		AliasObject alias = getAlias(name);

		if (alias == null)
			return null;
		else
			return alias.converted;
	}

	public String[] apiGetHelp( String name )
	{
		if (name == null)
			return null;

		AliasObject alias = getAlias(name);

		if (alias == null || alias.help == null)
			return null;
		else
			return alias.help.split("\\s*\\|\\|\\|\\s*");
	}

	public String apiGetCoreAlias( String name )
	{
		String command = name.toLowerCase();

		List<AliasObject> results = mods.odb.retrieve( AliasObject.class, "WHERE core = \"" + mods.odb.escapeString(command) + "\"" );

		if (results.size() == 0)
			return name;
		else
			return results.get(0).name;
	}

	public String[] helpCommandLock = {
		"Lock an alias so that no-one but its owner can change it.",
		"<Name>",
		"<Name> is the name of the alias to lock"
	};
	public void commandLock( Message mes )
	{
		String[] params = mods.util.getParamArray(mes);

		if (params.length != 2)
		{
			throw new ChoobBadSyntaxError();
		}

		String name = params[1];

		AliasObject alias = getAlias(name);

		if (alias.locked == true)
		{
			irc.sendContextReply(mes, "'" + name + "' is already locked!");
		}
		else
		{
			if (alias != null)
			{
				// No need to NS check here.

				final String originalOwner=alias.owner;

				alias.locked = true;
				alias.owner = mes.getNick();

				mods.odb.update(alias);
				irc.sendContextReply(mes, "Locked " + (!originalOwner.equals(alias.owner) ? "and taken ownership of alias by " + originalOwner + ": " : "") + "'" + name + "': " + alias.converted);
			}
			else
			{
				irc.sendContextReply(mes, "Alias '" + name + "' not found.");
			}
		}
	}

	public String[] helpCommandUnlock = {
		"Unlock an alias so that anyone can change it.",
		"<Name>",
		"<Name> is the name of the alias to unlock"
	};
	public void commandUnlock( Message mes )
	{
		String[] params = mods.util.getParamArray(mes);

		if (params.length != 2)
		{
			throw new ChoobBadSyntaxError();
		}

		String name = params[1];

		AliasObject alias = getAlias(name);

		if (alias != null)
		{
			String nick = mods.security.getRootUser(mes.getNick());
			if (nick == null)
				nick = mes.getNick();

			if (nick.toLowerCase().equals(alias.owner.toLowerCase()))
				mods.security.checkNS(mes);
			else
				mods.security.checkNickPerm(new ChoobPermission("plugin.alias.unlock"), mes);

			alias.locked = false;
			mods.odb.update(alias);
			irc.sendContextReply(mes, "Unlocked '" + name + "'!");
		}
		else
		{
			irc.sendContextReply(mes, "Alias '" + name + "' not found.");
		}
	}

	private AliasObject getAlias( String name )
	{
		String alias = mods.odb.escapeString(name).toLowerCase();

		List<AliasObject> results = mods.odb.retrieve( AliasObject.class, "WHERE name = \"" + alias + "\"" );

		if (results.size() == 0)
			return null;
		else
			return results.get(0);
	}

	// Trigger on everything, but is a trigger, not an event ==> takes account of ignore etc.
	public String filterTriggerRegex = "";
	public void filterTrigger( Message mes )
	{
		String text = mes.getMessage();

		Matcher matcher = Pattern.compile(irc.getTriggerRegex(), Pattern.CASE_INSENSITIVE).matcher(text);
		int offset = 0;

		// Make sure this is actually a command...
		if (matcher.find())
		{
			offset = matcher.end();
		}
		else if (!(mes instanceof PrivateEvent))
		{
			return;
		}

		int dotIndex = text.indexOf('.', offset);

		int cmdEnd = text.indexOf(' ', offset) + 1;
		if (cmdEnd == 0)
			cmdEnd = text.length();

		// Real command, not an alias...
		// Drop out.
		if (dotIndex != -1 && dotIndex < cmdEnd)
		{
			return;
		}

		// Text is everything up to the next space...
		String cmdParams;
		if (cmdEnd <= offset)
		{
			return; // null alias! Oh noes!
		}
		else
			cmdParams = text.substring(cmdEnd);

		String aliasName = text.substring(offset, cmdEnd);

		if (aliasName.equals(""))
		{
			return;
		}

		AliasObject alias = getAlias( aliasName );

		if (alias == null)
		{
			// Consider an error here...
			return;
		}

		int recurseLevel = 1;
		boolean securityOK = false;

		// Message extends IRCEvent, so this cast will always succeed.
		Map<String,String> mesFlags = ((IRCEvent)mes).getFlags();

		if (mesFlags.containsKey("alias.expanded"))
		{
			recurseLevel = Integer.parseInt(mesFlags.get("alias.expanded")) + 1;

			// Stop recursion
			if (recurseLevel > MAX_ALIAS_EXPANSIONS) {
				irc.sendContextReply(mes, "Synthetic event recursion detected (alias.expanded). Stopping.");
				return;
			}
		}
		else
		{
			try
			{
				int ret = (Integer)mods.plugin.callAPI("Flood", "IsFlooding", mes.getNick(), 1500, 4);
				if (ret != 0)
				{
					if (ret == 1)
						irc.sendContextReply(mes, "You're flooding, ignored. Please wait at least 1.5s between your messages.");
					return;
				}
			}
			catch (ChoobNoSuchCallException e)
			{ } // ignore
			catch (Throwable e)
			{
				System.err.println("Couldn't do antiflood call: " + e);
			}
		}

		if (mesFlags.containsKey("_securityOK"))
			securityOK = (mesFlags.get("_securityOK").equals("true"));

		String aliasText = alias.converted;

		String[] params = new String[0];
		List<String> paramList = mods.util.getParams(mes);
		params = paramList.toArray(params);

		String newText = applyAlias(aliasName, aliasText, params, cmdParams, mes);

		if (newText == null)
		{
			irc.sendContextReply(mes, "Invalid alias: '" + aliasName + "' -> '" + aliasText + "'.");
			return;
		}

		// Extract command name etc. We know they're non-null.
		int dotPos = newText.indexOf('.');
		int spacePos = newText.indexOf(' ');
		String extra = "";
		if (spacePos == -1)
			spacePos = newText.length();

		String pluginName = newText.substring(0, dotPos);
		String commandName = newText.substring(dotPos + 1, spacePos);

		newText = pluginName + "." + commandName + newText.substring(spacePos);

		mes = (Message)mes.cloneEvent( irc.getTrigger() + newText );

		mesFlags = ((IRCEvent)mes).getFlags();

		// Set expanded value for new message.
		mesFlags.put("alias.expanded", new Integer(recurseLevel).toString());

		if (securityOK)
		{
			// Tell the bot that security checks on this new message are safe.
			mesFlags.put("_securityOK", "true");
		}

		// XXX This is a hack. We should change the event to simply have a setMessage(). Or something.
		mods.history.addLog( mes ); // Needed in case a plugin needs to retrieve authoritative message.

		try
		{
			mods.plugin.queueCommand( pluginName, commandName, mes );
		}
		catch (ChoobNoSuchCallException e)
		{
			irc.sendContextReply(mes, "Sorry, that command is an alias ('" + alias.converted + "', made by '" + alias.owner + "') that points to an invalid command!");
		}
	}

	private String applyAlias(String name, String alias, String[] params, String origParams, Message mes)
	{
		// Make sure command name is valid...
		final Pattern validconv=Pattern.compile("^[a-zA-Z0-9]+\\.[a-zA-Z0-9]+.*");
		if (!validconv.matcher(alias).matches())
			return null;

		if (alias.indexOf("$") == -1)
		{
			if (origParams != null && origParams.length() > 0)
				return alias + " " + origParams;
			else
				return alias;
		}
		else
		{
			// Advanced syntax
			StringBuilder newCom = new StringBuilder();

			int pos = alias.indexOf('$'), oldPos = 0;
			int convEnd = alias.length() - 1;
			int curlyLevel = 0;
			while (pos != -1 && pos <= convEnd)
			{
				try
				{
					newCom.append(alias.substring(oldPos, pos));

					// Deal with curlies
					if (alias.charAt(pos) == '}')
					{
						pos++;
						if (curlyLevel == 0)
						{
							newCom.append("}");
							continue;
						}

						curlyLevel--;
						if (pos > convEnd)
							break;

						if (alias.charAt(pos) != '{')
							continue;

						// else block
						int newPos = alias.indexOf('}', pos);
						if (newPos == -1)
							continue;

						pos = newPos + 1;
						continue;
					}

					// Sanity check for $ at end of alias...
					if (pos == convEnd)
						break;

					char next = alias.charAt(pos + 1);
					if (next == '$')
					{
						newCom.append("$");
						pos = pos + 2;
					}
					else if (next == '.')
					{
						pos = pos + 2;
					}
					else if (next == '*')
					{
						for(int i = 1; i < params.length; i++)
						{
							newCom.append(params[i]);
							if (i != params.length - 1)
								newCom.append(" ");
						}
						pos = pos + 2;
					}
					else if (next >= '0' && next <= '9')
					{
						// Parameter
						int end = pos + 1;
						while(true)
						{
							if (end > convEnd)
								break;
							char test = alias.charAt(end);
							if (test < '0' || test > '9')
								break;
							// Another number!
							end++;
						}
						int paramNo = 0;
						try
						{
							paramNo = Integer.parseInt(alias.substring(pos + 1, end));
						}
						catch (NumberFormatException e)
						{
							// LIES!
						}
						if (paramNo < params.length)
							newCom.append(params[paramNo]);
						pos = end;
					}
					// $?ParamNo{if-true}
					// $?ParamNo{if-true}{if-false}
					else if (next == '?')
					{
						// If
						int newPos = pos + 2;
						char test = alias.charAt(newPos);
						if (test < '0' || test > '9')
						{
							newCom.append("$");
							pos++;
							continue;
						}

						while(newPos < convEnd)
						{
							test = alias.charAt(newPos + 1);
							if (test >= '0' && test <= '9')
								newPos++;
							else
								break;
						}

						newPos++;

						if (alias.charAt(newPos) != '{')
						{
							newCom.append("$");
							pos++;
							continue;
						}

						int paramNo = Integer.parseInt(alias.substring(pos + 2, newPos));

						newPos++;
						if (paramNo < params.length)
						{
							// true, so do guts.
							curlyLevel++;
							pos = newPos;
						}
						else
						{
							// skip
							newPos = alias.indexOf('}', newPos);
							if (newPos == -1)
							{
								// Bad syntax.
								newCom.append("$");
								pos++;
								continue;
							}

							if (newPos < convEnd && alias.charAt(newPos + 1) == '{')
							{
								// else clause
								curlyLevel++;
								pos = newPos + 2;
							}
							else
							{
								pos = newPos + 1;
							}
						}
					}
					else if (next == '[')
					{
						// Parameter list
						if (alias.length() < pos + 3)
						{
							newCom.append("$");
							pos++;
							continue;
						}

						int firstParam = -1, lastParam = -1;
						int newPos = pos + 2;
						char test = alias.charAt(newPos);

						// First param is '-' - set firstParam to be undefined.
						if (test == '-')
						{
							firstParam = -2;
							newPos++;
							test = alias.charAt(newPos);
						}

						// Begin eating params.
						if (test >= '0' && test <= '9')
						{
							int end = newPos + 1;
							while(true)
							{
								if (end > convEnd)
									break;
								test = alias.charAt(end);

								// End of number!
								if (test == '-' || test == ']')
								{
									int paramNo = -1;
									try
									{
										paramNo = Integer.parseInt(alias.substring(newPos, end));
									}
									catch (NumberFormatException e)
									{
										// LIES!
									}
									newPos = end + 1;
									if (firstParam == -1)
									{
										if (test == ']')
											break;
										firstParam = paramNo;
									}
									else if (lastParam == -1)
									{
										if (test == '-')
											break;
										lastParam = paramNo;
										break;
									}
								}
								else if (test < '0' || test > '9')
									break;
								// Another number!
								end++;
							}

							// Sort out undefined length ranges
							if (firstParam == -2)
							{
								// lastParam > 0
								if (lastParam <= 1)
									firstParam = params.length - 1;
								else
									firstParam = 1;
							}
							else if (lastParam == -1)
							{
								lastParam = params.length - 1;
							}

							// Process output now.
							if (lastParam < 0 || firstParam < 0 || lastParam + firstParam > 100)
							{
								newCom.append("$");
								pos++;
							}
							else
							{
								int direction = lastParam > firstParam ? 1 : -1;
								lastParam += direction; // For simpler termination of loop.
								for(int i = firstParam; i != lastParam; i += direction)
								{
									if (i < params.length)
									{
										newCom.append(params[i]);
										if (i != lastParam - direction)
											newCom.append(" ");
									}
								}
								pos = end + 1;
							}
						}
						else
						{
							// First digit wasn't a number.
							if (pos + 6 <= convEnd && alias.substring(pos + 2, pos + 7).equalsIgnoreCase("nick]"))
							{
								pos += 7;
								newCom.append(mes.getNick());
							}
							else if (pos + 6 <= convEnd && alias.substring(pos + 2, pos + 7).equalsIgnoreCase("chan]"))
							{
								pos += 7;
								newCom.append(mes.getContext());
							}
							else
							{
								newCom.append("$");
								pos++;
							}
						}
					}
					else
					{
						newCom.append("$");
						pos++;
					}
				}
				finally
				{
					oldPos = pos;
					int pos1 = alias.indexOf('$', pos);
					int pos2 = alias.indexOf('}', pos);
					if (pos1 == -1)
						pos = pos2;
					else if (pos2 == -1)
						pos = pos1;
					else if (pos1 < pos2)
						pos = pos1;
					else
						pos = pos2;
				}
			}
			newCom.append(alias.substring(oldPos, convEnd + 1));
			return newCom.toString();
		}
	}
}
