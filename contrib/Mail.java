import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;
import java.io.*;

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

	private Modules mods;
	private IRCInterface irc;

	public class MailException extends ChoobException
	{
		public MailException(String text)
		{
			super(text);
		}
		public MailException(String text, Throwable e)
		{
			super(text, e);
		}
		public String toString()
		{
			return getMessage();
		}
	}

	public Mail(Modules mods, IRCInterface irc)
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
	
	public void commandMail(Message mes)
	{
		List<String> params = mods.util.getParams(mes, 1);
		if (params.size() < 2)
		{
			irc.sendContextReply(mes,"You need to supply a nickname.");
			return;
		}
		
		String nick = params.get(1);
		String userName = mods.security.getRootUser(mods.nick.getBestPrimaryNick(nick));
		if (userName == null)
			userName = mods.nick.getBestPrimaryNick(nick);
		
		try
		{
			String email = (String)mods.plugin.callAPI("Options", "GetUserOption", userName, "Email", optionsUserDefaults[0]);
			
			if (email.length() > 0)
				irc.sendContextReply(mes, "'" + userName + "' has public e-mail address " + email);
			else
				irc.sendContextReply(mes, "No public e-mail address set for '" + userName + "'.");
		}
		catch (ChoobNoSuchCallException e)
		{
			irc.sendContextReply(mes, "Unable to load mail options. Please load 'Options' plugin.");
		}
	}
	
	private String sanitize(String message)
	{
		return message.replaceAll("[^ /\\w\\.!\\(\\)\\?,;'\"£$%&\\*@#]","");
	}

	public String[] helpCommandTechteam = {
		"Sends a short message to the techteam mailing list",
		"<Message>",
		"<Message> is the content of the message to post to the mailing list."
	};
	public void commandTechteam(Message mes)
	{
		List<String> params = mods.util.getParams(mes, 1);
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
		if ((params.get(1)).length() < 20)
		{
			irc.sendContextReply(mes,"Message is too short - try putting more detail in!");
			return;
		}	

		try
		{
			int ret = (Integer)mods.plugin.callAPI("Flood", "IsFlooding", "techteam" + mes.getNick(), 300000, 2);
			if (ret != 0)
			{
				irc.sendContextReply(mes, "This command may only be used once every 5 minutes.");
				return;
			}
		}
		catch (ChoobNoSuchCallException e){ }

		try
		{
			send("techteam@warwickcompsoc.co.uk",
			     "Message from " + mes.getNick() + " via " + irc.getNickname(),
			     "Message from " + mes.getNick() + " (" + mes.getLogin() + "@" + mes.getHostname() + ") in " + mes.getTarget() + ":" + "\r\n" + "\r\n" + sanitize(params.get(1)));
		}
		catch (MailException e)
		{
			irc.sendContextReply(mes, e.toString());
			return;
		}
		irc.sendContextReply(mes,"Message sent.");
	}

	public void apiSendMail(String to, String subject, String message) throws MailException
	{
		send(to, subject, message);
	}

	private void valid(String response, int code) throws MailException
	{
		if (response.matches("^" + code + ".*"))
			return;
		
		throw new MailException("Error sending message: " + response);
	}

	private void send(String to, String subject, String message) throws MailException
	{
		try
		{
			String smtpHost    =             (String)mods.plugin.callAPI("Options", "GetGeneralOption", "SMTPHost", optionsGeneralDefaults[0]);
			int    smtpPort    = new Integer((String)mods.plugin.callAPI("Options", "GetGeneralOption", "SMTPPort", optionsGeneralDefaults[1]));
			String fromDisplay =             (String)mods.plugin.callAPI("Options", "GetGeneralOption", "From",     optionsGeneralDefaults[2]);
			String fromUser    =             (String)mods.plugin.callAPI("Options", "GetGeneralOption", "FromUser", optionsGeneralDefaults[3]);
			String fromHost    =             (String)mods.plugin.callAPI("Options", "GetGeneralOption", "FromHost", optionsGeneralDefaults[4]);
			
			if (fromUser.length() == 0)
				throw new MailException("No 'From:' username configured.");
			if (fromHost.length() == 0)
				throw new MailException("No 'From:' hostname configured.");
			
			if (fromDisplay.length() == 0)
				fromDisplay = irc.getNickname();
			
			System.out.println("Mail:send:");
			System.out.println("    SMTP   : " + smtpHost + ":" + smtpPort);
			System.out.println("    From   : " + fromDisplay + " <" + fromUser + "@" + fromHost + ">");
			System.out.println("    To     : " + to);
			System.out.println("    Subject: " + subject);
			System.out.println("    Message: " + message.replaceAll("\n", "\n           : "));
			
			Date now = new Date();
			java.text.SimpleDateFormat RFC822date = new java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
			java.text.SimpleDateFormat MIDdate    = new java.text.SimpleDateFormat("yyyyMMddHHmmss");
			
			Socket socket = new Socket(smtpHost, smtpPort);
			DataOutputStream outgoing = new DataOutputStream(socket.getOutputStream());
			BufferedReader incoming = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			
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
		catch (ChoobNoSuchCallException e)
		{
			throw new MailException("Unable to load mail options. Please load 'Options' plugin.");
		}
		catch (IOException e)
		{
			throw new MailException("Input Output Error: " + e.toString());
		}
	}
}
