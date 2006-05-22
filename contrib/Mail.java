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
			"$Rev:$$Date$"
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

	private String sanitize(String message)
	{
		return message.replaceAll("[^ \\w\\.!\\(\\)\\?,;'\"£$%&\\*@#]","");
	}

	public String[] helpCommandTechteam = {
		"Sends a short message to the techteam mailing list",
		"<Message>",
		"<Message> is the content of the message to post to the mailing list."
	};
	public void commandTechteam(Message mes)
	{
		List<String> params = mods.util.getParams(mes,1);
		if (params.size() < 2)
		{
			irc.sendContextReply(mes,"You need to supply a message");
			return;
		}
		if (mes.getTarget() == null)
		{
			irc.sendContextReply(mes,"To reduce abuse you may only use this command in public channels");
			return;
		}
		if ((params.get(1)).length() < 20)
		{
			irc.sendContextReply(mes,"Message is too short - try putting more detail in!");
			return;
		}

		try
		{
			int ret = (Integer)mods.plugin.callAPI("Flood", "IsFlooding", "techteam", 300000, 2);
			if (ret != 0)
			{
				irc.sendContextReply(mes, "This command may only be used once every 5 mins");
				return;
			}
		}
		catch (ChoobNoSuchCallException e){ }

		try
		{
			send("techteam@warwickcompsoc.co.uk",sanitize(params.get(1)),mes.getNick(),mes.getTarget(),mes.getLogin() +"@"+mes.getHostname());
		} catch (MailException e)
		{
			irc.sendContextReply(mes,e.toString());
			return;
		}
		irc.sendContextReply(mes,"Message sent");
	}

	private void valid(String response,int code) throws MailException
	{
		if (response.matches("^" + code + ".*")) return;
		else throw new MailException("Error sending message: " + response);
	}

	private void send(String to, String message,String nick,String channel,String hostmask) throws MailException
	{
		try
		{
			String greeting = "HELO uwcs.co.uk";
			String from = "choob@uwcs.co.uk";
			Socket socket = new Socket("127.0.0.1",25);
			DataOutputStream outgoing = new DataOutputStream(socket.getOutputStream());
			BufferedReader incoming = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			valid(incoming.readLine(),220);
			outgoing.writeBytes("HELO uwcs.co.uk\r\n");
			valid(incoming.readLine(),250);
			outgoing.writeBytes("MAIL FROM: " + from + "\r\n");
			valid(incoming.readLine(),250);
			outgoing.writeBytes("RCPT TO: " + to + "\r\n");
			valid(incoming.readLine(),250);
			outgoing.writeBytes("DATA" + "\r\n");
			valid(incoming.readLine(),354);
			outgoing.writeBytes("Subject: Message from " + nick + " via BadgerBOT" + "\r\n" + "\r\n");
			outgoing.writeBytes("Message from " + nick + " (" + hostmask + ") in " + channel + ":" + "\r\n" + "\r\n" + message + "\r\n");
			outgoing.writeBytes("." + "\r\n");
			valid(incoming.readLine(),250);
			outgoing.writeBytes("QUIT" + "\r\n");
			socket.close();
		} catch (IOException e)
		{
			throw new MailException("Input Output Error: " + e.toString());
		}
	}
}
