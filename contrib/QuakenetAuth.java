import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.security.*;
import org.jibble.pircbot.Colors;
import java.util.regex.*;
import java.io.*;

/**
 * Class to cache results
 *
 */
public class QAuthResult {
	String account;
	long time;
}

/**
 * Choob plugin for performing authentication using Quakenet Q accounts.
 * 
 * @author Blood God
 *
 */
public class QuakenetAuth {
	
	private static int TIMEOUT = 10000;
	private static int CACHE_TIMEOUT = 300000;
	
	private boolean hadToAuth = false;
	
	final Pattern validAuthReply = Pattern.compile("^(.*) is authed as (.*)\\..?$");
	final Pattern notAuthedReply = Pattern.compile("^(.*) is NOT authed.?$");

	private Modules mods;
	private IRCInterface irc;
	private Map<String, QAuthResult> qChecks;
	
	// In case Q auth is not possible.
	private boolean whoisfallback = false;
	
	/**
	 * If enabled all checks will also be confirmed against the user's
	 * hostname.
	 */
	private boolean ipOverride = false;
	
	public String[] info()	{
		return new String[] {
			"Q Auth checker plugin.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}
	
	public QuakenetAuth(Modules mods, IRCInterface irc) {
		this.mods = mods;
		this.irc = irc;
		qChecks = new HashMap<String, QAuthResult>();		
	}
	
	// -- Commands -- //
	
	public String[] helpCommandEnableOverride = {
			"Used in case of Q downtime."
	};
	
	public void commandEnableOverride(Message mes) {
		whoisfallback = true;
		irc.sendContextReply(mes, "Okay.");
	}

	public String[] helpCommandAccount = {
			"Check the account name that a user is authed with.",
			"[<NickName>]",
			"<NickName> is an optional nick to check."
	};
	
	public void commandAccount(Message mes) throws ChoobException {
		String nick = mods.util.getParamString(mes);
		if (nick.length() == 0) {
			nick = mes.getNick();
		}
		
		String account = (String)mods.plugin.callAPI("QuakenetAuth", "Account", nick);
		if (account != null) {
			irc.sendContextReply(mes, nick + " is authed as " + account + ".");
		} else {
			irc.sendContextReply(mes, nick + " is not authed.");
		}
	}
	
	// -- API -- //
	public String[] helpApi = {
			"QuakenetAuth allows your plugin to poke Q and get auth status" +
			"on nicknames. It gives you a single API method: Account, which " +
			"takes a single String Parameter and returns a String. If the " +
			"returned String is not null, that is the account with which the " +
			"user is authenticated."
	};
	
	public String apiAccount(String nick) {
		QAuthResult result = getCachedQCheck(nick.toLowerCase());
		if (result != null)	{
			return result.account;
		}

		result = getNewQCheck(nick.toLowerCase());

		synchronized(result) {
			try {
				result.wait(30000); // Make sure if Q breaks we're not screwed
			} catch (InterruptedException e) {
				// Ooops, timeout
				return "FAIL";
			}
		}
		if (hadToAuth) {
			hadToAuth = false;
			result = getNewQCheck(nick.toLowerCase());

			synchronized(result) {
				try {
					result.wait(30000); // Make sure if Q breaks we're not screwed
				} catch (InterruptedException e) {
					// Ooops, timeout
					return "FAIL";
				}
			}
		}
		return result.account;
	}
	
	// -- Options -- //
	public String[] optionsGeneral = {
			"Qusername",
			"Qpassword"
	};
	
	public boolean optionCheckGeneralQusername(String value) {
		return true;
	}
	
	public String[] helpOptionQusername = {
			"Set this to the bot's Q username. When set in conjunction with" +
			"the password option the bot will authenticate itself."
	};
	
	public boolean optionCheckGeneralQpassword(String value) {
		return true;
	}
	
	public String[] helpOptionQpassword = {
			"Set this to the bot's Q password. When set in conjunction with" +
			"the username option the bot will authenticate itself."
	};
	
	// -- Response Handling -- //
	public void onServerResponse(ServerResponse resp) {
		if (resp.getCode() == 401) {
			// 401 Nick not found.
			Matcher ma = Pattern.compile("^[^ ]+ ([^ ]+) ").matcher(resp.getResponse().trim());
			if (ma.find() && ma.group(1).trim().toLowerCase().equals("q")) {
				// Q is dead.
				// XXX Handle - perhaps using L?
				whoisfallback = true;
			}
		}
		
		if ((resp.getCode() == 330)) {
			// Whois authentication status line
			// This takes the format: <BOTnick> <Usernick> <Account> :is authed as
			Matcher ma = Pattern.compile("^(.*) (.*) (.*) \\:is authed as$").matcher(resp.getResponse().trim());
			if (ma.find()) {
				// Extract the data
				String nick = ma.group(2).trim();
				String account = ma.group(3).trim();
				
				// Store this data
				QAuthResult result = getNickCheck(nick.toLowerCase());
				if (result == null) {
					result = new QAuthResult();
					result.account = account;
					result.time = System.currentTimeMillis();
					synchronized(qChecks) {
						qChecks.put(nick.toLowerCase(), result);
					}
					return;
				}
				synchronized(result) {
					result.account = account;
					result.time = System.currentTimeMillis();
					
					// Only notify if explicitly using the whois method
					if (whoisfallback) {
						result.notifyAll();
					}
				}
			}
		}
	}
	
	public void onPrivateNotice(Message mes) {
		if (!(mes instanceof PrivateNotice)) {
			// We only care about private notices
			return;
		}
		
		if (!mes.getNick().toLowerCase().equals("q")) {
			// Not from Q, therefore irrelevant
			return;
		}
		
		List<String> params = mods.util.getParams(mes);
		
		String nick = null;
		String account = null;
		
		if (mes.getMessage().matches(".*(?i:/msg Q@Cserve.quakenet.org auth).*")) {
			// Oh dear. We need to log in.
			String user = null;
			String pass = null;
			try {
				user = (String)mods.plugin.callAPI("Options", "GetGeneralOption", "qusername");
				pass = (String)mods.plugin.callAPI("Options", "GetGeneralOption", "qpassword");
			} catch (ChoobNoSuchCallException e) {
				System.err.println("Options plugin not loaded; can't get Q auth details.");
				whoisfallback = true;
				return;
			}
			
			if ((user != null) && (pass != null)) {
				final String authmessage = "auth " + user + " " + pass;
				AccessController.doPrivileged(new PrivilegedAction() {
					public Object run() {
						irc.sendMessage("Q@CServe.quakenet.org", authmessage);
						return null;
					}
				});
				
				hadToAuth = true;
			} else {
				System.err.println("Q auth details not set...");
				whoisfallback = true;
			}
			
			return;
		}
		
		if (!whoisfallback) {
					
			if (mes.getMessage().indexOf("authed") == -1) {
				// Not what we were looking for
				return;
			}
			Matcher ma = validAuthReply.matcher(Colors.removeFormattingAndColors(mes.getMessage()));
			if (!ma.matches()) {
				// User is not authed get their username and set this
				Matcher ma2 = notAuthedReply.matcher(Colors.removeFormattingAndColors(mes.getMessage()));
				if (ma2.matches()) {
					nick = ma2.group(1);
					QAuthResult result = getNickCheck(nick.toLowerCase());
					if (result == null) {
						// Hmm, this shouldn't have happened. May be that the reply wasn't
						// what we wanted or the user is not authed
						System.err.println("Can't find nick check. :(");
						return;
					}
					synchronized(result) {
						result.account = null;
						result.time = System.currentTimeMillis();
						result.notifyAll();
					}
				} else {
					// Fuck knows what we've got then
					return;
				}
			} else {
				nick = ma.group(1);
				account = ma.group(2);
				if ((nick == null) || (account == null)) {
					// Erm.. whoops?
					System.out.println("Null Q account... hmmmmmmm");
				} else {
					
					QAuthResult result = getNickCheck(nick.toLowerCase());
					if (result == null) {
						// Hmm, this shouldn't have happened. May be that the reply wasn't
						// what we wanted or the user is not authed
						System.err.println("Can't find nick check. :(");
						return;
					}
					synchronized(result) {
						result.account = account;
						result.time = System.currentTimeMillis();
						result.notifyAll();
					}
				}
			}
		}
	}
	
	// Expire checks as appropriate
	public void onNickChange(NickChange nc) {
		synchronized(qChecks) {
			qChecks.remove(nc.getNick());
			qChecks.remove(nc.getNewNick());
		}
	}
	
	public void onJoin(ChannelJoin cj) {
		synchronized(qChecks) {
			qChecks.remove(cj.getNick());
		}
	}
	
	public void onPart(ChannelPart cp) {
		synchronized(qChecks) {
			qChecks.remove(cp.getNick());
		}
	}
	
	public void onQuit(QuitEvent qe) {
		synchronized(qChecks) {
			qChecks.remove(qe.getNick());
		}
	}
	
	public void onKick(ChannelKick ck) {
		synchronized(qChecks) {
			qChecks.remove(ck.getNick());
		}
	}
	
	// -- Private methods -- //
	private QAuthResult getNewQCheck(final String nick) {
		QAuthResult result;
		synchronized(qChecks) {
			result = qChecks.get(nick);
			String accountStored = null;
			if (result != null) {
				accountStored = result.account;
			}
			if ((result == null) || (accountStored == null)) {
				// Need to perform a check
				result = new QAuthResult();
				result.account = null;
				
				AccessController.doPrivileged(new PrivilegedAction() {
					public Object run() {
						irc.sendMessage("Q", "whois " + nick);
						return null;
					}
				});
				
				if(whoisfallback) {
					// Perform a whois of the account too
					irc.sendRawLine("WHOIS " + nick);
				}
				qChecks.put(nick, result);
			}
		}
		return result;
	}
	
	private QAuthResult getNickCheck(String nick) {
		synchronized(qChecks) {
			return (QAuthResult)qChecks.get(nick);
		}
	}
	
	private QAuthResult getCachedQCheck(String nick) {
		QAuthResult result;
		synchronized(qChecks) {
			result = (QAuthResult)qChecks.get(nick);
			if (result == null) {
				// Shouldn't happen
				return null;
			}
			
			if (result.account == null) {
				return null;
			}
			
			if (result.time + TIMEOUT < System.currentTimeMillis()) {
				// OOoooold
				qChecks.remove(nick);
				return null;
			}
		}
		return result;
	}

}
