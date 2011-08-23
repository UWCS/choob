/*
 * Choob.java
 *
 * Created on June 1, 2005, 2:22 AM
 */

/**
 *
 * @author	sadiq
 */

package uk.co.uwcs.choob;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.PircBot;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobError;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.ChoobProtectionDomain;
import uk.co.uwcs.choob.support.ConfigReader;
import uk.co.uwcs.choob.support.DbConnectionBroker;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.Interval;
import uk.co.uwcs.choob.support.events.ChannelAction;
import uk.co.uwcs.choob.support.events.ChannelInfo;
import uk.co.uwcs.choob.support.events.ChannelInvite;
import uk.co.uwcs.choob.support.events.ChannelJoin;
import uk.co.uwcs.choob.support.events.ChannelKick;
import uk.co.uwcs.choob.support.events.ChannelMessage;
import uk.co.uwcs.choob.support.events.ChannelMode;
import uk.co.uwcs.choob.support.events.ChannelModes;
import uk.co.uwcs.choob.support.events.ChannelNotice;
import uk.co.uwcs.choob.support.events.ChannelParamMode;
import uk.co.uwcs.choob.support.events.ChannelPart;
import uk.co.uwcs.choob.support.events.ChannelTopic;
import uk.co.uwcs.choob.support.events.ChannelUserMode;
import uk.co.uwcs.choob.support.events.Event;
import uk.co.uwcs.choob.support.events.IRCEvent;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.NickChange;
import uk.co.uwcs.choob.support.events.PluginLoaded;
import uk.co.uwcs.choob.support.events.PluginReLoaded;
import uk.co.uwcs.choob.support.events.PluginUnLoaded;
import uk.co.uwcs.choob.support.events.PrivateAction;
import uk.co.uwcs.choob.support.events.PrivateMessage;
import uk.co.uwcs.choob.support.events.PrivateNotice;
import uk.co.uwcs.choob.support.events.QuitEvent;
import uk.co.uwcs.choob.support.events.ServerResponse;
import uk.co.uwcs.choob.support.events.UnknownEvent;
import uk.co.uwcs.choob.support.events.UserModes;

import com.google.common.annotations.VisibleForTesting;

/**
 * Core class of the Choob bot, main interaction with IRC.
 */
final class Choob extends PircBot implements Bot
{
	private final ChoobThreadManager ctm;
	private final ChoobDecoderTaskData cdtd;
	private DbConnectionBroker broker;
	private Modules modules;
	private IRCInterface irc;
	private String trigger;
	private List <Interval> intervalList;
	private ChoobWatcherThread watcher;

	private ConfigReader conf;
	private int exitCode;
	private int messageLimit;

	/**
	 * Constructor for Choob, initialises vital variables.
	 */
	Choob() throws ChoobError
	{

		try
		{
			conf=new ConfigReader("bot.conf");
		}
		catch (final IOException e)
		{
			e.printStackTrace();
			System.out.println("\n\nError reading config file, exiting.");
			throw new ExitCodeException(9);
		}

		trigger = conf.getSettingFallback("botTrigger","~");

		messageLimit = Integer.valueOf(conf.getSettingFallback("messageLimit","0"));
		if (messageLimit < 0)
			messageLimit = 0;

		// Create a shiny synchronised (americans--) list
		intervalList = new ArrayList<Interval>();

		// Create a new database connection broker using the MySQL drivers
		PrintWriter logFile;
		try
		{
			logFile=new PrintWriter(new FileOutputStream(ChoobMain.TEMP_FOLDER.getPath() + "/db.log"));
		}
		catch (final IOException e)
		{
			e.printStackTrace();
			System.out.println("Cannot create db log, exiting.");
			throw new ExitCodeException(7);
		}
		try
		{
			broker = new DbConnectionBroker("com.mysql.jdbc.Driver", "jdbc:mysql://"
					+ conf.getSettingFallback("dbServer","localhost")
					+ "/" + conf.getSettingFallback("database","choob") + "?autoReconnect=true&"
					+ "autoReconnectForPools=true&initialTimeout=1&"
					+ "useUnicode=true&characterEncoding=UTF-8&characterSetResults=UTF-8",
						conf.getSettingFallback("dbUser","choob"),
						conf.getSettingFallback("dbPass",""), 10, 20, logFile, 60);
		}
		catch (final SQLException e)
		{
			e.printStackTrace();
			System.out.println("Unexpected error in DbConnectionBroker setup, exiting.");
			throw new ExitCodeException(5);
		}

		// Use sensible charset, ignoring the platform-default.
		try
		{
			setEncoding(conf.getSettingFallback("botEncoding", "UTF-8"));
		}
		catch(final UnsupportedEncodingException e)
		{
			// Chances are the user entered a crap encoding here, fall back to UTF-8
			try {
				setEncoding("UTF-8");
			} catch (final UnsupportedEncodingException ex) {
				// Really broken
				System.out.println("Could not set a sensible encoding, exiting.");
				throw new ExitCodeException(6);
			}
		}

		// Install security manager etc.
		setupSecurity();

		// Create a new IRC interface
		irc = new IRCInterface( this );

		// This is needed to properly initialise a ChoobProtectionDomain.
		final ChoobPluginManagerState state = new ChoobPluginManagerState(irc);

		ctm = new ChoobThreadManager();

		// Initialise our modules.
		modules = new Modules(broker, intervalList, this, irc, state, ctm);

		ctm.setMods(modules);

		// Set the name from the config file.
		this.setName(conf.getSettingFallback("botName", randomName()));

		// Set the bot's hostname.
		this.setLogin(conf.getSettingFallback("botIdent", "choob"));

		// Name changed now...
		modules.util.updateTrigger();

		// Get the modules.
		irc.grabMods();

		// Create our list of threads
		watcher = new ChoobWatcherThread(intervalList, irc, modules, ctm);

		watcher.start();

		cdtd = new ChoobDecoderTaskData(modules, irc, ctm);

		loadCorePlugins();

		// Set PircBot's flood protection
		this.setMessageDelay(messageLimit);

		// Set Version (the comment).
		this.setVersion("Choob SVN - http://svn.uwcs.co.uk/repos/choob/");

		// Enable debugging output.
		setVerbose(true);

		try
		{
			doConnect();
		}
		catch (final IOException e)
		{
			e.printStackTrace();
			System.out.println("Connection Error, exiting: " + e);
			throw new ExitCodeException(2);
		}
		catch (final IrcException e)
		{
			e.printStackTrace();
			System.out.println("Unhandled IRC Error on connect, exiting: ." + e);
			throw new ExitCodeException(3);
		}
	}

	public static String randomName() {
		return "Choob" + new Random().nextInt(10000);
	}

	//* Connect the initialised bot to IRC, and do hard-coded post-connection stuff. */
	void doConnect() throws IOException, IrcException
	{
		exitCode = -1;

		// Connect to the IRC server.
		connect(conf.getSettingFallback("server","irc.uwcs.co.uk"),
			Integer.parseInt(conf.getSettingFallback("port", "6667")),
			conf.getSettingFallback("password", null));

		// Set mode +B (is a bot)
		sendRawLineViaQueue("MODE " + getName() + " +B");

		final String[] commands = conf.getSettingFallback("connectstring","").split("\\|\\|\\|");
		for (final String command : commands)
			sendRawLineViaQueue(command);

		// Get Autojoin to join us some channels.
		try
		{
			modules.plugin.callAPI("Autojoin", "Join");
		}
		catch (ChoobNoSuchCallException ignored)
		{
			// No plugin, no joining.
			ignored.printStackTrace(System.err);
		}
	}

	public IRCInterface getIRC()
	{
		return irc;
	}

	@Override
	public Modules getMods()
	{
		return modules;
	}

	private void loadCorePlugins() {
		try
		{
			// We need to have an initial set of plugins that ought to be loaded as core.

			final Connection dbConnection = broker.getConnection();
			final PreparedStatement coreplugSmt = dbConnection.prepareStatement("SELECT * FROM Plugins WHERE CorePlugin = 1;");
			final ResultSet coreplugResults = coreplugSmt.executeQuery();
			if ( coreplugResults.first() )
				do
				{
					System.out.print("Loading core plugin " + coreplugResults.getString("PluginName") + " from <" + coreplugResults.getString("URL") + ">... ");
					modules.plugin.addPlugin(coreplugResults.getString("PluginName"), coreplugResults.getString("URL"));
					System.out.println("done.");
				}
				while ( coreplugResults.next() );

			coreplugSmt.close();

			broker.freeConnection(dbConnection);
		}
		catch (final Throwable t)
		{
			t.printStackTrace();
			// If we failed to load the core plugins, we've got issues.
			throw new ChoobError("Failed to load core plugin list!", t);
		}
	}

	@VisibleForTesting static void setupSecurity()
	{
		// TODO - make this a proper class
		java.security.Policy.setPolicy( new java.security.Policy()
		{
			// I think this is all that's ever really needed...
			@Override
			public synchronized boolean implies(final java.security.ProtectionDomain d, final java.security.Permission p)
			{
				return !(d instanceof ChoobProtectionDomain);
			}
			@Override
			public synchronized java.security.PermissionCollection getPermissions(final java.security.ProtectionDomain d)
			{
				final java.security.PermissionCollection p = new java.security.Permissions();
				if ( !(d instanceof ChoobProtectionDomain) )
					p.add( new java.security.AllPermission() );
				return p;
			}
			@Override
			public synchronized java.security.PermissionCollection getPermissions(final java.security.CodeSource s)
			{
				final java.security.PermissionCollection p = new java.security.Permissions();
				//if ( !(d instanceof ChoobCodeSource) )
				//	p.add( new java.security.AllPermission() );
				return p;
			}
			@Override
			public void refresh() {}
		});

		// Now we've finished most of the stuff we need high access priviledges
		// to do, we can set up our security manager that checks all priviledged
		// accesses from a Beanshell plugin with their permissions in the MySQL
		// table.

		// Note to self: Install security AFTER making sure we have permissions
		// to grant ourselves permissions. :( -- bucko
		if ( System.getSecurityManager() == null )
			System.setSecurityManager(new SecurityManager());
	}

	/**
	 * Return a regex that can (and should) be used to match lines for a
	 * prefix indicating that the line is a command.
	 */
	@Override
	public String getTriggerRegex()
	{
		return "^(?i:" + trigger +
				"|" + getName().replaceAll("([^a-zA-Z0-9-])", "\\\\$1") + "[,:]\\s*" +
				"|bot[,:]\\s*)";
	}

	/**
	 * Return a string which can (and should) be prepended onto a line to
	 * make it into a command.
	 */
	@Override
	public String getTrigger()
	{
		return trigger;
	}

	@Override
	public void setExitCode(final int newExitCode) {
		exitCode = newExitCode;
	}

	@Override
	public void onSyntheticMessage(final Event mes) {
		spinThreadSynthetic( mes );
	}

	@Override
	protected void onDisconnect()
	{
		if (exitCode >= 0) {
			System.out.println("Disconnected as planned.");
			System.exit(exitCode);
		}

		// Exit code 1 (technically, any non-zero code) will cause the wrapper
		// to restart us.
		System.out.println("Connection lost!");
		System.exit(1);
	}

	/*
	 * DO NOT EDIT THE CODE IN THE PASTE BLOCK MANUALLY
	 *
	 * This code is automatically replaced by HorriblePerlScript when it is
	 * run. Make your modifications there.
	 */

	// BEGIN PASTE!

	public void onPluginLoaded(final String pluginName) {
		spinThread(new PluginLoaded("onPluginLoaded", pluginName, 1));
	}

	@Override
	public void onPluginReLoaded(final String pluginName) {
		spinThread(new PluginReLoaded("onPluginReLoaded", pluginName, 0));
	}

	@Override
	public void onPluginUnLoaded(final String pluginName) {
		spinThread(new PluginUnLoaded("onPluginUnLoaded", pluginName, -1));
	}

	@Override
	protected void onNotice(final String nick, final String login, final String hostname, final String target, final String message) {
		if (target.indexOf('#') == 0)
			spinThread(new ChannelNotice("onNotice", System.currentTimeMillis(), ((int)(Math.random()*127)), message, nick, login, hostname, target, target));
		else
			spinThread(new PrivateNotice("onPrivateNotice", System.currentTimeMillis(), ((int)(Math.random()*127)), message, nick, login, hostname, target));
	}

	@Override
	protected void onMessage(final String target, final String nick, final String login, final String hostname, final String message) {
		spinThread(new ChannelMessage("onMessage", System.currentTimeMillis(), ((int)(Math.random()*127)), message, nick, login, hostname, target, target));
	}

	@Override
	protected void onPrivateMessage(final String nick, final String login, final String hostname, final String message) {
		spinThread(new PrivateMessage("onPrivateMessage", System.currentTimeMillis(), ((int)(Math.random()*127)), message, nick, login, hostname, null));
	}

	@Override
	protected void onAction(final String nick, final String login, final String hostname, final String target, final String message) {
		if (target.indexOf('#') == 0)
			spinThread(new ChannelAction("onAction", System.currentTimeMillis(), ((int)(Math.random()*127)), message, nick, login, hostname, target, target));
		else
			spinThread(new PrivateAction("onPrivateAction", System.currentTimeMillis(), ((int)(Math.random()*127)), message, nick, login, hostname, target));
	}

	@Override
	protected void onChannelInfo(final String channel, final int userCount, final String message) {
		spinThread(new ChannelInfo("onChannelInfo", System.currentTimeMillis(), ((int)(Math.random()*127)), message, channel));
	}

	@Override
	protected void onDeVoice(final String channel, final String nick, final String login, final String hostname, final String target) {
		spinThread(new ChannelUserMode("onDeVoice", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "v", false, target));
	}

	@Override
	protected void onDeop(final String channel, final String nick, final String login, final String hostname, final String target) {
		spinThread(new ChannelUserMode("onDeop", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "o", false, target));
	}

	@Override
	protected void onInvite(final String target, final String nick, final String login, final String hostname, final String channel) {
		spinThread(new ChannelInvite("onInvite", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, nick, login, hostname, target));
	}

	@Override
	protected void onJoin(final String channel, final String nick, final String login, final String hostname) {
		spinThread(new ChannelJoin("onJoin", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, nick, login, hostname));
	}

	@Override
	protected void onKick(final String channel, final String nick, final String login, final String hostname, final String target, final String message) {
		spinThread(new ChannelKick("onKick", System.currentTimeMillis(), ((int)(Math.random()*127)), message, channel, nick, login, hostname, target));
	}

	@Override
	protected void onMode(final String channel, final String nick, final String login, final String hostname, final String modes) {
		spinThread(new ChannelModes("onMode", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, modes));
	}

	@Override
	protected void onNickChange(final String nick, final String login, final String hostname, final String newNick) {
		// Force update of name to match nick, as PircBot confuses the two.
		this.setName(this.getNick());
		spinThread(new NickChange("onNickChange", System.currentTimeMillis(), ((int)(Math.random()*127)), nick, login, hostname, newNick));
	}

	@Override
	protected void onOp(final String channel, final String nick, final String login, final String hostname, final String target) {
		spinThread(new ChannelUserMode("onOp", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "o", true, target));
	}

	@Override
	protected void onPart(final String channel, final String nick, final String login, final String hostname) {
		spinThread(new ChannelPart("onPart", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, nick, login, hostname));
	}

	@Override
	protected void onQuit(final String nick, final String login, final String hostname, final String message) {
		spinThread(new QuitEvent("onQuit", System.currentTimeMillis(), ((int)(Math.random()*127)), message, nick, login, hostname));
	}

	@Override
	protected void onRemoveChannelBan(final String channel, final String nick, final String login, final String hostname, final String param) {
		spinThread(new ChannelParamMode("onRemoveChannelBan", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "b", false, param));
	}

	@Override
	protected void onRemoveChannelKey(final String channel, final String nick, final String login, final String hostname, final String param) {
		spinThread(new ChannelParamMode("onRemoveChannelKey", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "k", false, param));
	}

	@Override
	protected void onRemoveChannelLimit(final String channel, final String nick, final String login, final String hostname) {
		spinThread(new ChannelMode("onRemoveChannelLimit", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "l", false));
	}

	@Override
	protected void onRemoveInviteOnly(final String channel, final String nick, final String login, final String hostname) {
		spinThread(new ChannelMode("onRemoveInviteOnly", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "i", false));
	}

	@Override
	protected void onRemoveModerated(final String channel, final String nick, final String login, final String hostname) {
		spinThread(new ChannelMode("onRemoveModerated", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "m", false));
	}

	@Override
	protected void onRemoveNoExternalMessages(final String channel, final String nick, final String login, final String hostname) {
		spinThread(new ChannelMode("onRemoveNoExternalMessages", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "n", false));
	}

	@Override
	protected void onRemovePrivate(final String channel, final String nick, final String login, final String hostname) {
		spinThread(new ChannelMode("onRemovePrivate", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "p", false));
	}

	@Override
	protected void onRemoveSecret(final String channel, final String nick, final String login, final String hostname) {
		spinThread(new ChannelMode("onRemoveSecret", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "s", false));
	}

	@Override
	protected void onRemoveTopicProtection(final String channel, final String nick, final String login, final String hostname) {
		spinThread(new ChannelMode("onRemoveTopicProtection", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "t", false));
	}

	@Override
	protected void onSetChannelBan(final String channel, final String nick, final String login, final String hostname, final String param) {
		spinThread(new ChannelParamMode("onSetChannelBan", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "b", true, param));
	}

	@Override
	protected void onSetChannelKey(final String channel, final String nick, final String login, final String hostname, final String param) {
		spinThread(new ChannelParamMode("onSetChannelKey", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "k", true, param));
	}

	@Override
	protected void onSetChannelLimit(final String channel, final String nick, final String login, final String hostname, final int prm) {
		spinThread(new ChannelParamMode("onSetChannelLimit", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "l", true, String.valueOf(prm)));
	}

	@Override
	protected void onSetInviteOnly(final String channel, final String nick, final String login, final String hostname) {
		spinThread(new ChannelMode("onSetInviteOnly", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "i", true));
	}

	@Override
	protected void onSetModerated(final String channel, final String nick, final String login, final String hostname) {
		spinThread(new ChannelMode("onSetModerated", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "m", true));
	}

	@Override
	protected void onSetNoExternalMessages(final String channel, final String nick, final String login, final String hostname) {
		spinThread(new ChannelMode("onSetNoExternalMessages", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "n", true));
	}

	@Override
	protected void onSetPrivate(final String channel, final String nick, final String login, final String hostname) {
		spinThread(new ChannelMode("onSetPrivate", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "p", true));
	}

	@Override
	protected void onSetSecret(final String channel, final String nick, final String login, final String hostname) {
		spinThread(new ChannelMode("onSetSecret", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "s", true));
	}

	@Override
	protected void onSetTopicProtection(final String channel, final String nick, final String login, final String hostname) {
		spinThread(new ChannelMode("onSetTopicProtection", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "t", true));
	}

	@Override
	protected void onTopic(final String channel, final String message, final String nick, final long date, final boolean changed) {
		spinThread(new ChannelTopic("onTopic", System.currentTimeMillis(), ((int)(Math.random()*127)), message, channel));
	}

	@Override
	protected void onUnknown(final String line) {
		spinThread(new UnknownEvent("onUnknown", System.currentTimeMillis(), ((int)(Math.random()*127))));
	}

	@Override
	protected void onServerResponse(final int code, final String response) {
		spinThread(new ServerResponse("onServerResponse", System.currentTimeMillis(), ((int)(Math.random()*127)), code, response));
	}

	@Override
	protected void onUserMode(final String targetNick, final String nick, final String login, final String hostname, final String modes) {
		spinThread(new UserModes("onUserMode", System.currentTimeMillis(), ((int)(Math.random()*127)), modes));
	}

	@Override
	protected void onVoice(final String channel, final String nick, final String login, final String hostname, final String target) {
		spinThread(new ChannelUserMode("onVoice", System.currentTimeMillis(), ((int)(Math.random()*127)), channel, "v", true, target));
	}

	// END PASTE!

	private void spinThread(final Event ev)
	{
		spinThreadInternal(ev, true);
	}

	private void spinThreadSynthetic(final Event ev)
	{
		spinThreadInternal(ev, false);
	}

	private synchronized void spinThreadInternal(final Event ev, final boolean securityOK)
	{
		spinThread(ctm, modules, cdtd, ev, securityOK);
	}

	public static void spinThread(final ChoobThreadManager ctm, final Modules modules,
			final ChoobDecoderTaskData cdtd, final Event ev, final boolean securityOK) {
		// synthLevel is also checked in addLog();
		if (ev instanceof Message && ((Message)ev).getSynthLevel() == 0)
			modules.history.addLog((Message)ev);

		if (ev instanceof ChannelKick)
			modules.history.addLog(ev);

		if (securityOK && ev instanceof IRCEvent)
			((IRCEvent)ev).getFlags().put("_securityOK", "true");

		final ChoobTask task = new ChoobDecoderTask(cdtd, ev);

		ctm.queueTask(task);
	}
}
