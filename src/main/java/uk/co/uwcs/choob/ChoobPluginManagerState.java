package uk.co.uwcs.choob;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import uk.co.uwcs.choob.support.IRCInterface;

public class ChoobPluginManagerState {
	public final IRCInterface irc;
	public final Map<String, ChoobPluginManager> pluginMap;
	public final Map<String, List<String>> commands;
	public final List<ChoobPluginManager> pluginManagers;
	public final SpellDictionaryChoob phoneticCommands;

	public ChoobPluginManagerState(final IRCInterface ircinter) {
		irc = ircinter;
		pluginManagers = new LinkedList<ChoobPluginManager>();
		pluginMap = new HashMap<String,ChoobPluginManager>();
		commands = new HashMap<String,List<String>>();
		File transFile = new File("share/en_phonet.dat");
		if (!transFile.exists()) {
			transFile = new File("../share/en_phonet.dat");
		}
		try
		{
			Reader reader = null;
			try {
				if (transFile.exists()) {
					reader = new FileReader(transFile);
				} else {
					reader = new InputStreamReader(ChoobPluginManager.class.getResourceAsStream("/share/en_phonet.dat"));
				}

				phoneticCommands = new SpellDictionaryChoob(reader);
			} finally {
				if (null != reader) {
					reader.close();
				}
			}
		}
		catch (final IOException e)
		{
			System.err.println("Could not load phonetics file: " + transFile);
			throw new RuntimeException("Couldn't load phonetics file", e);
		}
	}
}
