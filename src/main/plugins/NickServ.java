import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.ChannelJoin;
import uk.co.uwcs.choob.support.events.ChannelKick;
import uk.co.uwcs.choob.support.events.ChannelPart;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.NickChange;
import uk.co.uwcs.choob.support.events.PrivateNotice;
import uk.co.uwcs.choob.support.events.QuitEvent;
import uk.co.uwcs.choob.support.events.ServerResponse;

/**
 * Provides authentication for users based on their nickname.
 *
 * A configurable authentication mechanism is available.
 * UWCS NickServ and File based authentication are currently supported.
 *
 * @author benji
 */
public class NickServ
{
	private final Modules mods;
	private final IRCInterface irc;

	private static final int TIMEOUT_SECONDS = 20;
	private static final int CACHE_TIMEOUT_SECONDS = 600;

	public String[] info()
	{
		return new String[] {
			"Authentication checker plugin.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"0.2"
		};
	}

	//List of providers that can be configured to provider authentication
	private Map<String, CanProvideAuth> authProviders = new HashMap<String, CanProvideAuth>();
	
	public NickServ(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
		authProviders.put("ALL", new AllAuthMethods(mods,irc));
		authProviders.put("UWCS", new UWCSNickServInterpreter(mods, irc));
		authProviders.put("FILE", new UserFileAuthProvider(mods, irc));
		authProviders.put("MOZNET", new MoznetNickServInterpreter(mods, irc));
	}

	/**
	 * Check the authentication status for a user.
	 * @param nick	The nick of the user to check
	 * @return	-1 if unknown, 0 if not registered, 1 if not identified, 3 if identified
	 */
	public int apiStatus(final String nick)
	{
		return checkCached(nick).getId();
	}

	/**
	 * Check the authentication status for a user.
	 * @param nick	The nick of the user to check
	 * @return	true if authenticated, false otherwise.
	 */
	public boolean apiCheck( final String nick )
	{
		return apiCheck( nick, false );
	}

	/**
	 * Check the authentication status for a user.
	 * @param nick	The nick of the user to check
	 * @param assumption The authentication status to assume
	 * @return	true if authenticated, assumption otherwise
	 */
	public boolean apiCheck(final String nick, boolean assumption)
	{
		AuthStatus status = checkCached(nick);
		if (status.getId() == -1)
			return assumption;
		
		return status.getId() >= 3;
	}

	/**
	 * Check the authentication status for either the user specified or the requestor.
	 * @param mes	The request message
	 */
	public void commandCheck(final Message mes)
	{
		// Clear the cache, makes the command appear to behave as expected
		// and the side effects will be hillarious when people try and debug.
		// ¬_¬
		final String nick = getNick(mes);
		clearCache(nick);
		final AuthStatus status = checkCached(nick);
		irc.sendContextReply(mes, nick + " " + status.getExplanation());
	}

	/**
	 * Clear the authentication cache for either the user specified or the requestor.
	 * @param mes The request message.
	 */
	public void commandClearCache(final Message mes)
	{
		String nick = getNick(mes);
		clearCache(nick);
		irc.sendContextReply(mes, "Ok, cleared cache for " + nick);
	}

	private void clearCache(final String nick)
	{
		cache.remove(nick);
	}
	/**
	 * Obtains the nick to check from a message.
	 * @param mes	The message requesting a check
	 * @return	Either the paramstring, or the requestor's nick if no parameters were supplied.
	 */
	private final String getNick(Message mes)
	{
		final String nick = mods.util.getParamString( mes );
		if (nick.length() == 0)
			return mes.getNick();
		else
			return nick;
	}

	/**
	 * Perform a check for the authentication status of a particular user.
	 * Utilising the cache.
	 * @param nick	The nick to check the authentication status of
	 * @return
	 */
	private AuthStatus checkCached(final String nick)
	{
		//First check the cache
		return cache.get(nick, new Invokable<AuthStatus>()
		{
			//If we missed te cache we'll...
			@Override
			public AuthStatus invoke()
			{
				//Update the cache with the directly retrieved result
				return cache.put(nick,checkDirectly(nick), new InvokableI<AuthStatus, Boolean>()
				{
					//Except where the result is unknown
					@Override
					public Boolean invoke(AuthStatus t)
					{
						return t instanceof UnknownStatus;
					}	
				});
			}
		});
	}


	// Options - Allow the authentication provider to be changed at runtime
	// to switch to userip list if nickserv is not available
	// or to switch to another provider on another network
	public String[] optionsGeneral = { "AuthProvider" };
	public String[] optionsGeneralDefaults = { "ALL" };
	/**
	 * Ensure the new option is a valid configured provider
	 * @param optionValue	The option value to check
	 * @return	true if it is a valid value, false otherwise
	 */
	public boolean optionCheckGeneralAuthProvider( final String optionValue ) { return authProviders.containsKey(optionValue); }

	/**
	 * Looks up the currently configured authentication provider via the options plugin
	 * @return
	 */
	private CanProvideAuth getCurrentAuthProvider()
	{
		try
		{
			CanProvideAuth configured = authProviders.get(mods.plugin.callAPI("Options", "GetGeneralOption", optionsGeneral[0], optionsGeneralDefaults[0]).toString());
			if (configured == null)
				return authProviders.get(optionsGeneralDefaults[0]);

			return configured;
		} catch (ChoobNoSuchCallException e)
		{
			return authProviders.get(optionsGeneralDefaults[0]);
		}
	}

	/**
	 * A queue for pending nick checks. If we only allow one concurrent check then we avoid the problem of
	 * matching up replies with requests with insufficient state information.
	 *
	 * [Authentication Request]   [Multiple server responses]
	 *         ^  |                            |
	 *         |  |                            v
	 *         |  +----------------> [Request Queue]-[Result or Timeout]--+
	 *         |                                                          |
	 *         +-----------------------[Response]-------------------------+
	 */
	private final ChoobSucksScope.SingleBlockingQueueWithTimeOut<AbstractReplyHandler> commands = new ChoobSucksScope().new SingleBlockingQueueWithTimeOut<AbstractReplyHandler>(TIMEOUT_SECONDS);

	/**
	 * A cache with fixed timeout to cache authentication results in.
	 */
	private final ChoobSucksScope.CacheWithTimeout<AuthStatus> cache = new ChoobSucksScope().new CacheWithTimeout<AuthStatus>(CACHE_TIMEOUT_SECONDS);


	/**
	 * Check the authentication status of a user directly, without going through the cache
	 * @param nick	The nick to check the status of
	 * @return	The determined authentication status
	 */
	private AuthStatus checkDirectly(final String nick)
	{
		final CanProvideAuth nickserv = getCurrentAuthProvider();

		try
		{
			return commands.put(new CanProvideAuthReplyHandler(nickserv)).doThis(new Action<Void>()
			{
				@Override
				public void doWith(Void t)
				{
					nickserv.sendRequest(nick);
					System.out.println("Sent request to auth handler");
				}
			}).get(TIMEOUT_SECONDS, TimeUnit.SECONDS, new UnknownStatus());

		} catch (InterruptedException e)
		{
			return new UnknownStatus();
		} catch (ExecutionException e)
		{
			throw new RuntimeException(e);
		} catch (TimeoutException e)
		{
			return new UnknownStatus();
		}
		
	}


	/**
	 * When we recieve a private notice from the bot we want to pass it off to any pending authentication requests.
	 * @param mes	The notice we recieved
	 */
	public void onPrivateNotice( final Message mes )
	{
		AbstractReplyHandler handler = commands.peek();
		if (handler != null)
		{
			try
			{
				handler.handle(mes);
				commands.remove(handler);
			} catch (NotInterestedInReplyException e)
			{
				//wait for next private notice.
			}
		}
	}

	/**
	 * When we recieve a server response from the bot we want to pass it off to any pending authentication requests.
	 * @param resp	The response we recieved
	 */
	public void onServerResponse(final ServerResponse resp)
	{
		AbstractReplyHandler handler = commands.peek();
		if (handler != null)
		{
			try
			{
				handler.handle(resp);
				commands.remove(handler);
			} catch (NotInterestedInReplyException e)
			{
				//wait for next private notice.
			}
		}
	}

	// Expire old cache when appropriate...
	public void onNickChange( final NickChange nc )
	{
		cache.remove(nc.getNick());
		cache.remove(nc.getNewNick());
	}

	public void onJoin( final ChannelJoin cj )
	{
		cache.remove(cj.getNick());
	}

	public void onKick( final ChannelKick ck )
	{
		cache.remove(ck.getTarget());
	}

	public void onPart( final ChannelPart cp )
	{
		cache.remove(cp.getNick());
	}

	public void onQuit( final QuitEvent qe )
	{
		cache.remove(qe.getNick());
	}
}

/**
 * Interface auth providers need to implement.
 * sendRequest will be called to send message off to nickserv/userip/whatever
 * recieveReply overloads will be called with responses.
 * 
 * @author benji
 */
interface CanProvideAuth
{
	/**
	 * Sends a request to whatever providers your authentication on the IRC server
	 * @param nick
	 */
	public void sendRequest(String nick);

	/**
	 * Recieve a private message reply
	 * @param mes	The message recieved
	 * @return	The authstatus if we could determine it.
	 * @throws NotInterestedInReplyException	If this reply does not allow us to determine the authstatus of the user.
	 */
	public AuthStatus receiveReply(Message mes) throws NotInterestedInReplyException;

	/**
	 * Recieve a server-response reply
	 * @param resp	The response recieved
	 * @return	The authstatus if we could determine it.
	 * @throws NotInterestedInReplyException	If this reply does not allow us to determine the authstatus of the user.
	 */
	public AuthStatus receiveReply(ServerResponse resp) throws NotInterestedInReplyException;
}

/**
 * An exception to indicate that we're not interested
 * in a particular server reply or message. So our handler
 * should stay in the queue
 * 
 * @author benji
 */
class NotInterestedInReplyException extends Exception
{
	
}


/**
 * Choob appears to dislike enums.
 *
 * An authentication status type.
 *
 * We need integer ids for backwards compatibility.
 * We also need a human readable description.
 */
abstract class AuthStatus
{
	private final int id;
	private final String name;

	protected AuthStatus(int id, String name)
	{
		this.id = id;
		this.name = name;
	}

	public int getId()
	{
		return id;
	}

	public String getExplanation()
	{
		return "is " + name + " (" + id + ")!";
	}

}


class UnknownStatus extends AuthStatus
{
	public UnknownStatus()
	{
		super(-1,"unknown");
	}
}

class NotRegisteredStatus extends AuthStatus
{
	public NotRegisteredStatus()
	{
		super(0, "not registered");
	}
}

class NotIdentifiedStatus extends AuthStatus
{
	public NotIdentifiedStatus()
	{
		super(1,"not identified");
	}
}

class AuthenticatedStatus extends AuthStatus
{
	public AuthenticatedStatus()
	{
		super(3,"authed");
	}
}


interface Action<T>
{
	public void doWith(T t);
}

interface Invokable<T>
{
	public T invoke();
}

interface InvokableI<T,R>
{
	public R invoke(T t);
}

interface ReplyHandler extends DefaultFuture<AuthStatus>
{
	public void handle(Message reply) throws NotInterestedInReplyException;
}

interface CanGetStatus
{
	public AuthStatus replyToStatus(Message reply) throws NotInterestedInReplyException;
}

/**
 * A Future that allows us to specify a default value instead of null when the timeout expires.
 * @author benji
 */
interface DefaultFuture<T> extends Future<T>
{
	public T get(long timeout, TimeUnit unit, T defaultValue) throws InterruptedException, ExecutionException, TimeoutException;
}

/**
 * Handle replies from the server, delegating to abstract methods.
 *
 * Implements Future so that we can retreive the result of the authentication.
 * 
 * @author benji
 */
abstract class AbstractReplyHandler implements ReplyHandler, CanGetStatus
{
	private boolean canceled = false;
	private final Object cancelledLock = new Object();

	//We can only have one Future result
	private final BlockingQueue<AuthStatus> queue = new ArrayBlockingQueue<AuthStatus>(1);

	@Override
	public abstract AuthStatus replyToStatus(Message reply) throws NotInterestedInReplyException;
	public abstract AuthStatus replyToStatus(ServerResponse response) throws NotInterestedInReplyException;

	/**
	 * Handles a message reply, converting the implemented handler to a Future result.
	 * @param reply	The reply to handle
	 * @throws NotInterestedInReplyException	If the implementation is not interested in this reply.
	 */
	@Override
	public void handle(Message reply) throws NotInterestedInReplyException
	{
		try
		{
			queue.put(replyToStatus(reply));
		} catch (InterruptedException e)
		{
			//meh
		}
	}
	
	/**
	 * Handles a message reply, converting the implemented handler to a Future result.
	 * @param resp	The reply to handle
	 * @throws NotInterestedInReplyException	If the implementation is not interested in this reply.
	 */
	public void handle(ServerResponse resp) throws NotInterestedInReplyException
	{
		try
		{
			queue.put(replyToStatus(resp));
		} catch (InterruptedException e)
		{
			//meh
		}
	}

	/**
	 * @{@inheritDoc}
	 */
	@Override
	public boolean cancel(boolean mayInterrupt)
	{
		if (this.isDone())
			return false;

		synchronized(cancelledLock)
		{
			return this.canceled = true;
		}
	}

	/**
	 * @{@inheritDoc}
	 */
	@Override
	public boolean isCancelled()
	{
		synchronized(cancelledLock)
		{
			return this.canceled;
		}
	}

	/**
	 * @{@inheritDoc}
	 */
	@Override
	public boolean isDone()
	{
		return queue.peek() != null;
	}

	/**
	 * @{@inheritDoc}
	 */
	@Override
	public AuthStatus get() throws InterruptedException, ExecutionException
	{
		return queue.poll();
	}

	/**
	 * @{@inheritDoc}
	 */
	@Override
	public AuthStatus get(long timeout , TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
	{
		return queue.poll(timeout, unit);
	}
	
	/**
	 * @{@inheritDoc}
	 */
	@Override
	public AuthStatus get(long timeout, TimeUnit unit, AuthStatus defaultStatus) throws InterruptedException, ExecutionException, TimeoutException
	{
		AuthStatus status = get(timeout,unit);
		if (status != null)
			return status;

		return defaultStatus;
	}

}


/**
 * Implementation of AbstractReplyHandler that delegates
 * authentication to an auth provider
 * 
 * @author benji
 */
class CanProvideAuthReplyHandler extends AbstractReplyHandler
{
	private final CanProvideAuth nickserv;

	public CanProvideAuthReplyHandler(CanProvideAuth nickserv)
	{
		this.nickserv = nickserv;
	}

	@Override
	public AuthStatus replyToStatus(Message reply) throws NotInterestedInReplyException
	{
		return nickserv.receiveReply(reply);
	}

	@Override
	public AuthStatus replyToStatus(ServerResponse reply) throws NotInterestedInReplyException
	{
		return nickserv.receiveReply(reply);
	}
}

class PatternStatusPair
{
	private final Pattern pattern;
	private final AuthStatus status;

	public PatternStatusPair(String pattern, AuthStatus status)
	{
		this.pattern = Pattern.compile(pattern);
		this.status = status;
	}

	public Pattern getPattern()
	{
		return pattern;
	}

	public AuthStatus getStatus()
	{
		return status;
	}
}

/**
 * Default implementation of CanProvideAuth.
 *
 * Talks to UWCS Nickserv or Freenode Nickserv
 * 
 * @author benji
 */
class UWCSNickServInterpreter implements CanProvideAuth
{
	private final Modules mods;
	private final IRCInterface irc;

	/**
	 * Mappings of replies to statuses
	 */
	private final List<PatternStatusPair> statuses = Arrays.asList
	(
		new PatternStatusPair("^([^\\s]+?) is not registered.$", new NotRegisteredStatus()),
		new PatternStatusPair("^Last seen.*?: now$", new AuthenticatedStatus()),
		new PatternStatusPair("^Last seen.*?:.*", new NotIdentifiedStatus()),
		new PatternStatusPair("^\\*\\*\\* End of Info \\*\\*\\*$", new UnknownStatus())
	);

	public UWCSNickServInterpreter(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	/**
	 * Ask Nickserv for info on a user.
	 * @param nick	The user to request info for
	 */
	@Override
	public void sendRequest(final String nick)
	{
		irc.sendMessage("NickServ", "INFO " + nick);
	}

	/**
	 * Tries to convert nickserv replies to an Authentication Status
	 * @param mes	The message recieved back from the server
	 * @return	The determined authentication status
	 * @throws NotInterestedInReplyException	If we couldn't determine the authentication status from this message.
	 */
	@Override
	public AuthStatus receiveReply(final Message mes) throws NotInterestedInReplyException
	{
		if ( ! (mes instanceof PrivateNotice) )
			throw new NotInterestedInReplyException(); // Only interested in private notices

		if ( ! mes.getNick().toLowerCase().equals( "nickserv" ) )
			throw new NotInterestedInReplyException(); // Not from NickServ --> also don't care

		final String reply = mes.getMessage();

		System.out.println("receiveReply " + reply);

		for (PatternStatusPair status : statuses)
		{
			System.out.println("Checking " + status.getPattern().pattern());
			if (status.getPattern().matcher(reply).matches())
			{	
				System.out.println("Matched! returning " + status.getStatus().getId());
				return status.getStatus();
			}
		}

		System.out.println("None matched, ignoring");
		throw new NotInterestedInReplyException();
	}

	/**
	 * For Nickserv we're not interested in server responses
	 * @param resp	The response from the server
	 * @return	Never
	 * @throws NotInterestedInReplyException	Always
	 */
	@Override
	public AuthStatus receiveReply(ServerResponse resp) throws NotInterestedInReplyException
	{
		throw new NotInterestedInReplyException();
	}
}

/**
 * Tries all authentication methods, to allow you to choose the one that works.
 */
class AllAuthMethods implements CanProvideAuth
{
	private final Modules mods;
	private final IRCInterface irc;

	public AllAuthMethods(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
		this.authMethods = Arrays.asList
		(
			new UWCSNickServInterpreter(mods,irc),
			new MoznetNickServInterpreter(mods,irc),
			new UserFileAuthProvider(mods,irc)
		);
	}

	private final List<CanProvideAuth> authMethods;

	@Override
	public void sendRequest(final String nick)
	{
		for (CanProvideAuth auth : authMethods)
			auth.sendRequest(nick);
	}

	@Override
	public AuthStatus receiveReply(final Message mes) throws NotInterestedInReplyException
	{
		return new WithMultipleAuthProviders(authMethods)
		{
			@Override protected AuthStatus checkEach(CanProvideAuth auth) throws NotInterestedInReplyException
			{
				return auth.receiveReply(mes);
			}
		}.getResult();
	}

	@Override
	public AuthStatus receiveReply(final ServerResponse resp) throws NotInterestedInReplyException
	{
		return new WithMultipleAuthProviders(authMethods)
		{
			@Override protected AuthStatus checkEach(CanProvideAuth auth) throws NotInterestedInReplyException
			{
				return auth.receiveReply(resp);
			}
		}.getResult();
	}

	abstract class WithMultipleAuthProviders
	{
		private final List<CanProvideAuth> authProviders;
		private AuthStatus result = null;

		public WithMultipleAuthProviders(List<CanProvideAuth> authProviders)
		{
			this.authProviders = authProviders;
			for (CanProvideAuth auth : authMethods)
			{
				try
				{
					// continue until one provider claims it knows the status.
					if (!((result = checkEach(auth)) instanceof UnknownStatus))
						break;
				} catch (NotInterestedInReplyException ex)
				{
					// ignore
				}
			}
		}

		public AuthStatus getResult() throws NotInterestedInReplyException
		{
			if (result == null)
				throw new NotInterestedInReplyException();

			return result;
		}

		protected abstract AuthStatus checkEach(CanProvideAuth auth) throws NotInterestedInReplyException;
	}
}

/**
 * Mozilla implementation of CanProvideAuth.
 *
 * Talks to Mozilla Nickserv
 * 
 * @author benji
 */
class MoznetNickServInterpreter implements CanProvideAuth
{
	private final Modules mods;
	private final IRCInterface irc;

	public MoznetNickServInterpreter(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	/**
	 * Mappings of replies to statuses
	 */
	private final List<PatternStatusPair> statuses = Arrays.asList
	(
		new PatternStatusPair("^STATUS.*0$", new NotRegisteredStatus()),
		new PatternStatusPair("^STATUS.*3$", new AuthenticatedStatus()),
		new PatternStatusPair("^STATUS.*2$", new NotIdentifiedStatus()),
		new PatternStatusPair("^STATUS.*1$", new UnknownStatus())
	);

	/**
	 * Ask Nickserv for info on a user.
	 * @param nick	The user to request info for
	 */
	@Override
	public void sendRequest(final String nick)
	{
		irc.sendMessage("NickServ", "STATUS " + nick);
	}

	/**
	 * Tries to convert nickserv replies to an Authentication Status
	 * @param mes	The message recieved back from the server
	 * @return	The determined authentication status
	 * @throws NotInterestedInReplyException	If we couldn't determine the authentication status from this message.
	 */
	@Override
	public AuthStatus receiveReply(final Message mes) throws NotInterestedInReplyException
	{
		if ( ! (mes instanceof PrivateNotice) )
			throw new NotInterestedInReplyException(); // Only interested in private notices

		if ( ! mes.getNick().toLowerCase().equals( "nickserv" ) )
			throw new NotInterestedInReplyException(); // Not from NickServ --> also don't care

		final String reply = mes.getMessage();

		System.out.println("receiveReply " + reply);

		for (PatternStatusPair status : statuses)
		{
			System.out.println("Checking " + status.getPattern().pattern());
			if (status.getPattern().matcher(reply).matches())
			{	
				System.out.println("Matched! returning " + status.getStatus().getId());
				return status.getStatus();
			}
		}

		System.out.println("None matched, ignoring");
		throw new NotInterestedInReplyException();
	}

	/**
	 * For Nickserv we're not interested in server responses
	 * @param resp	The response from the server
	 * @return	Never
	 * @throws NotInterestedInReplyException	Always
	 */
	@Override
	public AuthStatus receiveReply(ServerResponse resp) throws NotInterestedInReplyException
	{
		throw new NotInterestedInReplyException();
	}
}

/**
 * Implementation of authentication provider backed onto a userip.list file.
 *
 * Useful when Nickserv is unavailable.
 * 
 * @author benji
 */
class UserFileAuthProvider implements CanProvideAuth
{
	private final Modules mods;
	private final IRCInterface irc;

	public UserFileAuthProvider(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	/**
	 * Request both the USERIP and USERHOST of the user
	 * @param nick	The user to request details for
	 */
	@Override
	public void sendRequest(final String nick)
	{
		irc.sendRawLine("USERIP " + nick);
		irc.sendRawLine("USERHOST " + nick);
	}

	/**
	 * We're not interested in message replies
	 * @param mes	The message recieved
	 * @return	Never
	 * @throws NotInterestedInReplyException	Always
	 */
	@Override
	public AuthStatus receiveReply(final Message mes) throws NotInterestedInReplyException
	{
		throw new NotInterestedInReplyException();
	}

	/**
	 * Converts a server response into an Authentication Status
	 * @param resp	The response from the server
	 * @return	The determined authentication status
	 * @throws NotInterestedInReplyException	If this server response does not allow us to determine the authentication status
	 */
	@Override
	public AuthStatus receiveReply(ServerResponse resp) throws NotInterestedInReplyException
	{
		if (!(resp.getCode()==340 || resp.getCode()==302)) // USERIP (and USERHOST) response, not avaliable through PircBOT, gogo magic numbers.
			throw new NotInterestedInReplyException();

		/*
		 * General response ([]s as quotes):
		 * [Botnick] :[Nick]=+[User]@[ip, or, more likely, hash]
		 *
		 * for (a terrible) example:
		 * Choobie| :Faux=+Faux@87029A85.60BE439B.C4C3F075.IP
		 */

		final Matcher ma=Pattern.compile("^[^ ]+ :([^=]+)=(.*)").matcher(resp.getResponse().trim());
		if (!ma.find())
			throw new NotInterestedInReplyException();

		System.out.println("Checking the file for \"" + ma.group(2) + "\".");

		try
		{
			String line;
			// Looks in src/ directory for file called userip.list with lines in form +<hostmask> e.g. +~benji@benjiweber.co.uk
			final BufferedReader allowed = new BufferedReader(new FileReader("userip.list"));

			while((line=allowed.readLine())!=null)
			{
				if (ma.group(2).equals(line))
				{
					return new AuthenticatedStatus();
				}
			}

			return new UnknownStatus();
		}
		catch (final IOException e)
		{
			throw new NotInterestedInReplyException();
		}
	}
}

/***
 * Interface to perform an action while still waiting on a blocking method.
 *
 * @author benji
 */
interface AndThen<T>
{
	public T doThis(Action<Void> action);
	public T value();
}

/**
 * Can't have top level generic types in choob
 * @author benji
 */
class ChoobSucksScope
{
	/**
	 * A blocking queue with a capacity of 1
	 * Items in the queue time out and are removed after the specified timeout.
	 * 
	 * @param <T>
	 */
	class SingleBlockingQueueWithTimeOut<T>
	{
		private final ArrayBlockingQueue<T> queue = new ArrayBlockingQueue<T>(1);

		private final int timeout;

		public SingleBlockingQueueWithTimeOut(int timeoutInSeconds)
		{
			this.timeout = timeoutInSeconds;
		}

		public AndThen<T> put(final T t) throws InterruptedException
		{

			queue.put(t);
			new Timer().schedule(new TimerTask()
			{
				@Override
				public void run()
				{
					//Remove this object, if it still exists in the queue.
					queue.remove(t);
				}
			}, timeout * 1000);

			return new AndThen<T>()
			{

				@Override
				public T doThis(Action<Void> action)
				{
					action.doWith(null);
					return t;
				}

				@Override
				public T value()
				{
					return t;
				}

			};
		}

		public T peek()
		{
			return queue.peek();
		}

		public T poll()
		{
			return queue.poll();
		}

		public boolean remove(T o)
		{
			return queue.remove(o);
		}
	}

	/**
	 * Exception thrown when the specified item is not in the cache.
	 */
	class ItemNotCachedException extends Exception
	{

	}

	/**
	 * A cache that caches items for the fixed time, with no sliding window.
	 * 
	 */
	class CacheWithTimeout<T>
	{
		private ConcurrentHashMap<String,T> map = new ConcurrentHashMap<String, T>();

		private int timeout;

		public CacheWithTimeout(int timeoutInSeconds)
		{
			this.timeout = timeoutInSeconds;
		}

		/**
		 * remove the item with specified key from the cache
		 * @param key	The key of the item to remove
		 * @return	The removed item.
		 */
		public T remove(final String key)
		{
			return map.remove(key);
		}

		/**
		 * Put the specified key/value pair into the cache
		 * @param key	The key
		 * @param t	The value
		 * @return	The added value, for method chaining
		 */
		public T put(final String key, final T t)
		{
			map.put(key, t);
			new Timer().schedule(new TimerTask()
			{
				@Override
				public void run()
				{
					//Remove this object, if it still exists in the cache.
					map.remove(key,t);
				}
			}, timeout * 1000);
			return t;
		}

		/**
		 * Put the specified key/value pair in the cache, except when the supplied condition returns true.
		 * @param key	The key
		 * @param t	The value
		 * @param exceptWhen	A condition to check on the added item. If this returns true it will NOT add it to the cache.
		 * @return	The possibly-added item for method chaining.
		 */
		public T put(final String key, final T t, final InvokableI<T,Boolean> exceptWhen)
		{
			if (!(exceptWhen.invoke(t)))
				return put(key,t);

			return t;
		}

		/**
		 * Put the specified key/value pair into the cache.
		 * @param key	The key to add
		 * @param t	An invokable that when evaluated will yeild an item to add to the cache.
		 * @return	The added item
		 */
		public T put(final String key, final Invokable<T> t)
		{
			return put(key,t.invoke());
		}

		/**
		 * Put the specified key/value pair in the cache, except when the supplied condition returns true.
		 * @param key	The key
		 * @param t	An invokable that when evaluated will yeild an item to add to the cache.
		 * @param exceptWhen	A condition to check on the added item. If this returns true it will NOT add it to the cache.
		 * @return	The possibly-added item for method chaining.
		 */
		public T put(final String key, final Invokable<T> t, final InvokableI<T,Boolean> exceptWhen)
		{
			T value = t.invoke();
			if (!(exceptWhen.invoke(value)))
				return put(key,t.invoke());

			return value;
		}

		/**
		 * Get the item with the specified key from the cache
		 * @param key	The key to look up
		 * @return	The cached item
		 * @throws ChoobSucksScope.ItemNotCachedException if there is no item with this key in the cache.
		 */
		public T get(final String key) throws ItemNotCachedException
		{
			T value = map.get(key);
			if (value == null)
				throw new ItemNotCachedException();

			return value;
		}

		/**
		 * Get the item with the specified key from the cache
		 * @param key	The key to look up
		 * @param defaultValue The value to return if the item is not in the cache.
		 * @return	The cached item
		 * @throws ChoobSucksScope.ItemNotCachedException if there is no item with this key in the cache.
		 */
		public T get(final String key, T defaultValue)
		{
			try
			{
				return get(key);
			} catch (ItemNotCachedException e)
			{
				return defaultValue;
			}
		}

		/**
		 * Get the item with the specified key from the cache
		 * @param key	The key to look up
		 * @param defaultValueObtainer The invokable to obtain the default value to return if the item is not in the cache.
		 * @return	The cached item
		 * @throws ChoobSucksScope.ItemNotCachedException if there is no item with this key in the cache.
		 */
		public T get(final String key, Invokable<T> defaultValueObtainer)
		{
			try
			{
				return get(key);
			} catch (ItemNotCachedException e)
			{
				return defaultValueObtainer.invoke();
			}
		}
	}
}


