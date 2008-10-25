import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.Date;
import java.util.List;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobException;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

public class Mail
{
	public String[] info()
	{
		return new String[] {
			"Plugin to send email.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private final Modules mods;
	private final IRCInterface irc;

	public class MailException extends ChoobException
	{
		private static final long serialVersionUID = 5965468056173754247L;
		public MailException(final String text)
		{
			super(text);
		}
		public MailException(final String text, final Throwable e)
		{
			super(text, e);
		}
		@Override
		public String toString()
		{
			return getMessage();
		}
	}

	public Mail(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	// General options
	public String[] optionsGeneral = { "SMTPHost", "SMTPPort", "From", "FromUser", "FromHost" };
	public String[] optionsGeneralDefaults = { "127.0.0.1", "25", "", "", "" };

	public String[] helpOptionSMTPHost = {
		"The hostname to connect to for sending mail."
	};
	public String[] helpOptionSMTPPort = {
		"The port to connect to for sending mail."
	};
	public String[] helpOptionFrom = {
		"The display name for use in 'From:'."
	};
	public String[] helpOptionFromUser = {
		"The username for use in 'From:'."
	};
	public String[] helpOptionFromHost = {
		"The hostname for use in 'From:'."
	};

	// User options
	public String[] optionsUser = { "Email" };
	public String[] optionsUserDefaults = { "" };

	public String[] helpOptionEmail = {
		"The public e-mail address to show to users requesting it."
	};

	public String[] helpCommandMail = {
		"Displays the public e-mail address of the given person",
		"<Nickname>",
		"<Nickname> is the person you want the public e-mail address of"
	};
	public void commandMail(final Message mes)
	{
		final List<String> params = mods.util.getParams(mes, 1);
		if (params.size() < 2)
		{
			irc.sendContextReply(mes,"You need to supply a nickname.");
			return;
		}

		final String nick = params.get(1);
		String userName = null;
		if (mods.security.hasAuth(nick)) {
			userName = mods.security.getUserAuthName(nick);
		} else {
			userName = mods.security.getRootUser(mods.nick.getBestPrimaryNick(nick));
		}
		if (userName == null)
			userName = mods.nick.getBestPrimaryNick(nick);

		try
		{
			final String email = (String)mods.plugin.callAPI("Options", "GetUserOption", userName, "Email", optionsUserDefaults[0]);

			if (email.length() > 0)
				irc.sendContextReply(mes, "'" + userName + "' has public e-mail address " + email);
			else
				irc.sendContextReply(mes, "No public e-mail address set for '" + userName + "'.");
		}
		catch (final ChoobNoSuchCallException e)
		{
			irc.sendContextReply(mes, "Unable to load mail options. Please load 'Options' plugin.");
		}
	}

	private String sanitize(final String message)
	{
		return message.replaceAll("[^ /\\w\\.!\\(\\)\\?,;'\"£$%&\\*@#]","");
	}

	public String[] helpCommandTechteam = {
		"Sends a short message to the techteam mailing list",
		"<Message>",
		"<Message> is the content of the message to post to the mailing list"
	};
	public void commandTechteam(final Message mes)
	{
		final List<String> params = mods.util.getParams(mes, 1);
		if (params.size() < 2)
		{
			irc.sendContextReply(mes,"You need to supply a message.");
			return;
		}
		if (mes.getTarget() == null)
		{
			irc.sendContextReply(mes,"To reduce abuse you may only use this command in public channels.");
			return;
		}
		if (params.get(1).length() < 20)
		{
			irc.sendContextReply(mes,"Message is too short - try putting more detail in!");
			return;
		}

		try
		{
			final int ret = (Integer)mods.plugin.callAPI("Flood", "IsFlooding", "techteam" + mes.getNick(), 300000, 2);
			if (ret != 0)
			{
				irc.sendContextReply(mes, "This command may only be used once every 5 minutes.");
				return;
			}
		}
		catch (final ChoobNoSuchCallException e)
		{
			// If flood isn't loaded, don't call it, don't care.
		}

		try
		{
			send("techteam@warwickcompsoc.co.uk",
			     "Message from " + mes.getNick() + " via " + irc.getNickname(),
			     "Message from " + mes.getNick() + " (" + mes.getLogin() + "@" + mes.getHostname() + ") in " + mes.getTarget() + ":" + "\r\n" + "\r\n" + sanitize(params.get(1)));
		}
		catch (final MailException e)
		{
			irc.sendContextReply(mes, e.toString());
			return;
		}
		irc.sendContextReply(mes,"Message sent.");
	}

	public void apiSendMail(final String to, final String subject, final String message) throws MailException
	{
		send(to, subject, message);
	}

	private void valid(final String response, final int code) throws MailException
	{
		if (response.matches("^" + code + ".*"))
			return;

		throw new MailException("Error sending message: " + response);
	}

	private void send(final String to, final String subject, final String message) throws MailException
	{
		try
		{
			final String smtpHost    =             (String)mods.plugin.callAPI("Options", "GetGeneralOption", "SMTPHost", optionsGeneralDefaults[0]);
			final int    smtpPort    = Integer.valueOf((String)mods.plugin.callAPI("Options", "GetGeneralOption", "SMTPPort", optionsGeneralDefaults[1]));
			String fromDisplay =             (String)mods.plugin.callAPI("Options", "GetGeneralOption", "From",     optionsGeneralDefaults[2]);
			final String fromUser    =             (String)mods.plugin.callAPI("Options", "GetGeneralOption", "FromUser", optionsGeneralDefaults[3]);
			final String fromHost    =             (String)mods.plugin.callAPI("Options", "GetGeneralOption", "FromHost", optionsGeneralDefaults[4]);

			if (fromUser.length() == 0)
				throw new MailException("No 'From:' username configured.");
			if (fromHost.length() == 0)
				throw new MailException("No 'From:' hostname configured.");

			if (fromDisplay.length() == 0)
				fromDisplay = irc.getNickname();

			//System.out.println("Mail:send:");
			//System.out.println("    SMTP   : " + smtpHost + ":" + smtpPort);
			//System.out.println("    From   : " + fromDisplay + " <" + fromUser + "@" + fromHost + ">");
			//System.out.println("    To     : " + to);
			//System.out.println("    Subject: " + subject);
			//System.out.println("    Message: " + message.replaceAll("\n", "\n           : "));

			final Date now = new Date();
			final java.text.SimpleDateFormat RFC822date = new java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
			final java.text.SimpleDateFormat MIDdate    = new java.text.SimpleDateFormat("yyyyMMddHHmmss");

			final Socket socket = new Socket(smtpHost, smtpPort);
			final DataOutputStream outgoing = new DataOutputStream(socket.getOutputStream());
			final BufferedReader incoming = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			// Connection sequence.
			// Expects a particular server response at every stage, else fails.
			valid(incoming.readLine(), 220);
			outgoing.writeBytes("HELO " + fromHost + "\r\n");
			valid(incoming.readLine(), 250);
			outgoing.writeBytes("MAIL FROM: " + fromUser + "@" + fromHost + "\r\n");
			valid(incoming.readLine(), 250);
			outgoing.writeBytes("RCPT TO: " + to + "\r\n");
			valid(incoming.readLine(), 250);
			outgoing.writeBytes("DATA" + "\r\n");
			valid(incoming.readLine(), 354);
			outgoing.writeBytes("Subject: " + subject + "\r\n");
			outgoing.writeBytes("To: " + to + "\r\n");
			outgoing.writeBytes("From: " + fromDisplay + " <" + fromUser + "@" + fromHost + ">\r\n");
			outgoing.writeBytes("Date: " + RFC822date.format(now) + "\r\n");
			outgoing.writeBytes("Message-ID: <" + MIDdate.format(now) + "." + fromUser + "@" + fromHost + ">" + "\r\n");
			outgoing.writeBytes("\r\n");
			outgoing.writeBytes(message.replaceAll("\n\\.", "\n..") + "\r\n");
			outgoing.writeBytes("." + "\r\n");
			valid(incoming.readLine(), 250);
			outgoing.writeBytes("QUIT" + "\r\n");
			socket.close();
		}
		catch (final ChoobNoSuchCallException e)
		{
			throw new MailException("Unable to load mail options. Please load 'Options' plugin.");
		}
		catch (final IOException e)
		{
			throw new MailException("Input Output Error: " + e.toString());
		}
	}
}
