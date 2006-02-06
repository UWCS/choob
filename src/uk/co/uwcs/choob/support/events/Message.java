/**
 *
 * @author Horrible Perl Script. Ewwww.
 */

package uk.co.uwcs.choob.support.events;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
public class Message extends IRCEvent implements MessageEvent, ContextEvent, UserEvent, AimedEvent
{
	/**
	 * message
	 */
	private final String message;

	private boolean requiresPrefix;
	private boolean isError;
	private boolean isAction;

	/**
	 * Get the value of message
	 * @return The value of message
	 */
	public String getMessage() {
		 return message;
	}

	/**
	 * nick
	 */
	private final String nick;

	/**
	 * Get the value of nick
	 * @return The value of nick
	 */
	public String getNick() {
		 return nick;
	}

	/**
	 * login
	 */
	private final String login;

	/**
	 * Get the value of login
	 * @return The value of login
	 */
	public String getLogin() {
		 return login;
	}

	/**
	 * hostname
	 */
	private final String hostname;

	/**
	 * Get the value of hostname
	 * @return The value of hostname
	 */
	public String getHostname() {
		 return hostname;
	}

	/**
	 * target
	 */
	private final String target;

	/**
	 * Get the value of target
	 * @return The value of target
	 */
	public String getTarget() {
		 return target;
	}

	/**
	 * Get the reply context in which this event resides
	 * @return The context
	 */
	public String getContext() {
		return getNick();
	}

	public boolean getPrefix() {
		 return requiresPrefix;
	}

	public boolean getError() {
		 return isError;
	}

	public boolean getAction() {
		 return isAction;
	}

	/**
	 * Construct a new Message.
	 */
	public Message(String methodName, long millis, int random, String message, String nick, String login, String hostname, String target)
	{
		super(methodName, millis, random);
		this.message = message;
		this.nick = nick;
		this.login = login;
		this.hostname = hostname;
		this.target = target;
		this.requiresPrefix = false;
		this.isError = false;
		this.isAction = false;
	}

	/**
	 * Construct a new Message.
	 */
	public Message(String methodName, long millis, int random, String message, String nick, String login, String hostname, String target, boolean reply, boolean error , boolean action)
	{
		super(methodName, millis, random);
		this.message = message;
		this.nick = nick;
		this.login = login;
		this.hostname = hostname;
		this.target = target;
		this.requiresPrefix = reply;
		this.isError = error;
		this.isAction = action;
	}

	/**
	 * Synthesize a new Message from an old one.
	 */
	public Message(Message old, String message)
	{
		super(old);
		this.message = message;
		this.nick = old.nick;
		this.login = old.login;
		this.hostname = old.hostname;
		if (old.target == null) target = nick; else this.target = old.target;
		this.requiresPrefix = old.requiresPrefix;
		this.isError = old.isError;
		this.isAction = old.isAction;
	}

	/**
	 * Synthesize a new Message from an old one with new replytype / error .
	 */
	public Message(Message old, String message,boolean reply, boolean error, boolean action)
	{
		super(old);
		this.message = message;
		this.nick = old.nick;
		this.login = old.login;
		this.hostname = old.hostname;
		if (old.target != null) this.target = old.target; else this.target = this.nick;
		this.requiresPrefix = reply;
		this.isError = error;
		this.isAction = action;
	}

	/**
	 * Synthesize a new Message from an old one with new replytype / error and new target
	 */
	public Message(Message old, String message,boolean reply, boolean error,boolean action, String target)
	{
		super(old);
		this.message = message;
		this.nick = old.nick;
		this.login = old.login;
		this.hostname = old.hostname;
		this.target = target;
		this.requiresPrefix = reply;
		this.isError = error;
		this.isAction = action;
	}

	/**
	 * Synthesize a new Message from this one.
	 * @return The new Message object.
	 */
	public Event cloneEvent(String message)
	{
		return new Message(this, message);
	}

	/**
	 * LoopBack!
	 * @return The new Message object.
	 */

	public Message duplicate(String message)
	{
		return new Message(this, message);
	}

	public Message[] contextMessage(String message)
	{
		Message[] returnMessage = new Message[1];
		returnMessage[0] = new Message(this, message,false,false,false);		
		return returnMessage;
	}

	public Message[] contextMessage(String[] messages)
	{
		Message[] returnMessages = new Message[messages.length];

		for (int i = 0; i < messages.length ; i++)
			returnMessages[i] = new Message(this, messages[i],false,false,false);


		return returnMessages;
	}

	public Message[] contextMessage(List<String> messages)
	{
		Message[] returnMessages = new Message[messages.size()];

		for(int i = 0; i < messages.size(); i++)
			returnMessages[i] = new Message(this, messages.get(i),false,false,false);

		return returnMessages;
	}

	public Message[] contextReply(String message)
	{
		Message[] returnMessage = new Message[1];
		returnMessage[0] = new Message(this, message,true,false,false);
		return returnMessage;
	}

	public Message[] contextReply(String[] messages)
	{
		Message[] returnMessages = new Message[messages.length];

		for (int i = 0; i < messages.length ; i++)
			returnMessages[i] = new Message(this, messages[i],true,false,false);

		return returnMessages;
	}

	public Message[] contextReply(List<String> messages)
	{
		Message[] returnMessages = new Message[messages.size()];

		for(int i = 0; i < messages.size(); i++)
			returnMessages[i] = new Message(this, messages.get(i),true,false,false);

		return returnMessages;
	}


	public Message[] targetedMessage(String message, String target)
	{
		Message[] returnMessage = new Message[1];
		returnMessage[0] = new Message(this, message,false,false,false,target);
		return returnMessage;
	}

	public Message[] targetedMessage(String[] messages, String target)
	{
		Message[] returnMessages = new Message[messages.length];

		for (int i = 0; i < messages.length ; i++)
			returnMessages[i] = new Message(this, messages[i],false,false,false,target);

		return returnMessages;
	}

	public Message[] targetedMessage(List<String> messages, String target)
	{
		Message[] returnMessages = new Message[messages.size()];

		for(int i = 0; i < messages.size(); i++)
			returnMessages[i] = new Message(this, messages.get(i),false,false,false,target);

		return returnMessages;
	}


	public Message[] contextAction(String message)
	{
		Message[] returnMessage = new Message[1];
		returnMessage[0] = new Message(this, message,false,false,true);
		return returnMessage;
	}

	public Message[] contextAction(String[] messages)
	{
		Message[] returnMessages = new Message[messages.length];

		for (int i = 0; i < messages.length ; i++)
			returnMessages[i] = new Message(this, messages[i],false,false,true);

		return returnMessages;
	}

	public Message[] contextAction(List<String> messages)
	{
		Message[] returnMessages = new Message[messages.size()];

		for(int i = 0; i < messages.size(); i++)
			returnMessages[i] = new Message(this, messages.get(i),false,false,true);

		return returnMessages;
	}


	public Message[] targetedAction(String message, String target)
	{
		Message[] returnMessage = new Message[1];
		returnMessage[0] = new Message(this, message,false,false,true,target);
		return returnMessage;
	}

	public Message[] targetedAction(String[] messages, String target)
	{
		Message[] returnMessages = new Message[messages.length];

		for (int i = 0; i < messages.length ; i++)
			returnMessages[i] = new Message(this, messages[i],false,false,true,target);

		return returnMessages;
	}

	public Message[] targetedAction(List<String> messages, String target)
	{
		Message[] returnMessages = new Message[messages.size()];

		for(int i = 0; i < messages.size(); i++)
			returnMessages[i] = new Message(this, messages.get(i),false,false,true,target);

		return returnMessages;
	}

	public boolean equals(Object obj)
	{
		if (obj == null || !(obj instanceof Message))
			return false;
		if ( !super.equals(obj) )
			return false;
		Message thing = (Message)obj;
		if ( true && message.equals(thing.message) && nick.equals(thing.nick) && login.equals(thing.login) && hostname.equals(thing.hostname) && target.equals(thing.target) )
			return true;
		return false;
	}

	public String toString()
	{
		StringBuffer out = new StringBuffer("Message(");
		out.append(super.toString());
		out.append(", message = " + message);
		out.append(", nick = " + nick);
		out.append(", login = " + login);
		out.append(", hostname = " + hostname);
		out.append(", target = " + target);
		out.append(")");
		return out.toString();
	}

}
