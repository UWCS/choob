import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jibble.pircbot.Colors;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.ObjectDBTransaction;
import uk.co.uwcs.choob.support.events.ChannelJoin;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.support.events.PrivateEvent;

class ActiveVote {
	public int id;
	public String text;
	public String responses; // comma-seperated
	public String results; // same as responses
	public String caller;
	public String channel;
	public boolean finished;
	public long startTime;
	public long finishTime;
	public boolean nickserv; //Does the user need to be nickserv identified to vote?
}

class Voter {
	public int id;
	public int voteID;
	public String nick;
	public int response;
}

/**
 * Voting plugin for Choob!
 *
 * @author bucko
 */
public class Vote {
	public String[] info() {
		return new String[] {
			"Allows users to create polls to query about stuff.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private final Modules mods;
	private final IRCInterface irc;
	private final Map<String,Integer> activeVotes = new HashMap<String,Integer>();

	public Vote(final Modules mods, final IRCInterface irc) {
		this.mods = mods;
		this.irc = irc;

		// Reload all old queued objects...
		final long time = System.currentTimeMillis();
		final List<ActiveVote> votes = mods.odb.retrieve(ActiveVote.class, "WHERE finished = 0");
		for(final ActiveVote vote: votes) {
			mods.interval.callBack( vote, vote.finishTime - time, vote.id );
		}
	}

	//Options
	public String[] optionsUser = { "VoteResultMessage", "VoteJoinNotify" };
	public String[] optionsUserDefaults = { "1", "1" };
	public boolean optionCheckUserVoteResultMessage(final String value, final String nick) {
		return value.equals("0") || value.equals("1");
	}
	public String[] helpOptionVoteResultMessage = {
		"Choose to be notified of the result of a vote.",
		"Set this to \"0\" to not have the bot send a message with the result of the vote upon its completion.",
	};

	public boolean optionCheckUserVoteJoinNotify(final String value, final String nick) {
		return value.equals("0") || value.equals("1");
	}
	public String[] helpOptionVoteJoinNotify = {
		"Choose to be notified of votes you have yet to vote on upon joining a channel with active votes.",
		"Set this to \"0\" to disable these notifications."
	};

	public String[] optionsGeneral = { "VoteVerbose" };
	public String[] optionsGeneralDefaults = {"1"};
	public boolean optionCheckGeneralVoteVerbose(final String value) {
		return value.equals("0") || value.equals("1");
	}
	public String[] helpOptionVoteVerbose = {
		"Determine the amount of output generated when a user calls a vote.",
		"If set to \"0\" the output will be limited to 2 lines.",
		"If set to \"1\" the output will place each option for the vote on a new line."
	};

	//Additional help topics
	public String[] helpTopics = { "Examples" };
	public String[] helpExamples = {
		"Examples of CallVote:",
		"'Vote.Call (Bananas, Oranges)' to ask people to choose the best out"
			+ " of bananas and oranges.",
		"'Vote.Call Do you like George Bush?' to ask for opinions on our"
			+ " favourite US president.",
		"'Vote.Call Longest? (String, Snake, Worm)' to ask which people"
			+ " think is the longest.",
		"'Vote.Call \"Democracy sucks?\" 1d' to call a one day vote on the"
			+ " state of democracy.",
		"'Vote.Call Best Regime? (Totalitarianism, Communism) 10s' to call a"
			+ " lightning poll on the virtues of extremes."
	};

	//Start a vote
	public String[] helpCommandCall = {
		"Call a vote for channel members to vote on. See Vote.Examples.",
		"[ <Question> ] [ ( <Responses> ) ] [ <Duration> ]",
		"<Question> is an optional question (default: \"Which is best?\")",
		"<Responses> is an optional comma seperated list of responses (default: Yes, No)",
		"<Duration> is an optional duration to run the vote of the form [<Days>d][<Hours>h][<Minutes>m][<Seconds>s] (default: 60s)",
		"Note that if you are authenticated with nickserv, voters must authenticate themselves before being allowed to vote."
	};
	public synchronized void commandCall( final Message mes ) {
		final String paramString = mods.util.getParamString( mes ).trim();

		//Check the parameters
		if (paramString.length()==0) {
			irc.sendContextReply(mes, "Sorry, you're missing a few parameters. Syntax: 'Vote.Call " + helpCommandCall[1] + "'. See Help.Help Vote.Examples." );
			return;
		}

		//Default parameters for name and options
		String question = "Which is best?";
		String[] options = new String[] { "Yes", "No" };


		//Parse in the parameters to get the title, options and time
		int pos = -1;
		if (paramString.charAt(0) == '"') {
			// Has question in "s.
			final int endPos = paramString.indexOf('"', 1);
			if (endPos != -1) {
				question = paramString.substring(1, endPos);
				pos = endPos + 1;
			} else {
				question = paramString.trim();
				pos = paramString.length();
				System.out.println(pos);
			}
		} else if (paramString.charAt(0) != '(') {
			// Has a question
			final int endPos = paramString.indexOf('(');
			if (endPos == -1) {
				question = paramString.trim();
			} else {
				question = paramString.substring(0, endPos).trim();
				pos = endPos;
			}
		} else if (paramString.indexOf('(') == -1) {
			// Has neither
			irc.sendContextReply(mes, "Sorry, you need either a question or some options. Syntax: 'Vote.Call " + helpCommandCall[1] + "'. See Help.Help Vote.Examples." );
			return;
		} else {
			// Has some options
			pos = paramString.indexOf('(');
		}

		// Parse the options.
		if (pos != -1) {
			final int startPos = paramString.indexOf('(', pos);
			int endPos = -1;
			if (startPos != -1) {
				endPos = paramString.indexOf(')', startPos);
			}
			if (endPos != -1) {
				options = paramString.substring(startPos + 1, endPos).split("\\s*,\\s*");
			}

			if ( options.length == 0 || endPos == -1 ) {
				if (startPos != -1) {
					irc.sendContextReply(mes, "Invalid option string! Syntax: 'Vote.Call " + helpCommandCall[1] + "'. See Help.Help Vote.Examples." );
					return;
				}
			} else {
				// Remove trailing/leading spaces.
				options[0] = options[0].trim();
				options[options.length - 1] = options[options.length - 1].trim();

				pos = endPos + 1;
			}
		} else {
			pos = paramString.length();
		}

		// Anything after the end?
		final String remain = paramString.substring(pos).trim();
		long duration = 60 * 1000;
		String durationString = "60s";
		if (remain.length() != 0) {
			try {
				duration = apiDecodePeriod(remain) * 1000;
				if (duration > 300000) // 5 mins
					durationString = "until " + new Date(System.currentTimeMillis() + duration);
				else
					durationString = remain;
			} catch (final NumberFormatException e) {
				irc.sendContextReply(mes, "Invalid duration string! Syntax: 'Vote.Call " + helpCommandCall[1] + "'. See Help.Help Vote.Examples." );
				return;
			}
		}

		//Create the vote
		final ActiveVote vote = new ActiveVote();
		vote.caller = mes.getNick();
		vote.channel = mes.getContext();
		vote.text = question;
		vote.finished = false;
		vote.results = "";
		vote.startTime = System.currentTimeMillis();
		vote.finishTime = System.currentTimeMillis() + duration;
		//Determine if the user is nickserv authed, if so then the vote will be open to nickserv authed voters only
		vote.nickserv = mods.security.hasAuth(mes);

		final StringBuilder responseString = new StringBuilder("Abstain");
		for (final String option : options)
			responseString.append("," + option);
		vote.responses = responseString.toString();

		mods.odb.save(vote);
		mods.interval.callBack( vote, duration, vote.id );

		activeVotes.put(mes.getContext(), Integer.valueOf(vote.id));

		if (checkOption("","VoteVerbose",true))	{
			irc.sendContextReply(mes, "OK, called vote #" + vote.id + " on \"" + question + "\"! You have " + durationString + ".");
			final String trigger = irc.getTrigger();
			irc.sendContextReply(mes, trigger + "Vote.Vote 0  ==>  Abstain");
			for(int i=0; i<options.length; i++)
				irc.sendContextReply(mes, trigger + "Vote.Vote " + (i + 1) + "  ==>  " + options[i]);
		} else {
			final StringBuilder responseOutput = new StringBuilder("0 for Abstain");
			for(int i=0; i<options.length; i++)
				responseOutput.append(", " + (i + 1) + " for " + options[i]);

			irc.sendContextReply(mes, "OK, called vote on \"" + question + "\"! You have " + durationString + " to use 'Vote.Vote <Number>' here or 'Vote.Vote " + vote.id + " <Number>' elsewhere, where <Number> is one of:");
			irc.sendContextReply(mes, responseOutput + ".");
		}
	}

	public String[] helpCommandActiveVotes = {
		"List all active votes."
	};
	public void commandActiveVotes(final Message mes) {
		List<ActiveVote> votes;
		//Only retrieve votes that the user can vote on.
		if (mods.security.hasAuth(mes)) {
			votes = mods.odb.retrieve(ActiveVote.class, "WHERE finished = 0");
		} else {
			votes = mods.odb.retrieve(ActiveVote.class, "WHERE finished = 0 AND nickserv = 0");
		}

		final Map<String,List<ActiveVote>> map = new HashMap<String,List<ActiveVote>>();
		final List<String> channels = new ArrayList<String>();
		for(final ActiveVote vote: votes) {
			List<ActiveVote> chanVotes = map.get(vote.channel);
			if (chanVotes == null) {
				chanVotes = new ArrayList<ActiveVote>();
				map.put(vote.channel, chanVotes);
				channels.add(vote.channel);
			}
			chanVotes.add(vote);
		}

		if (channels.size() == 0) {
			if (mods.security.hasAuth(mes)) {
				irc.sendContextReply(mes, "Sorry, there are no active votes!");
			} else {
				irc.sendContextReply(mes, "Sorry, there are no active votes! Note that some votes are only available for nickserv authed users.");
			}
		} else if (votes.size() == 1 || mes instanceof PrivateEvent) {
			//Extended version for private messages
			final StringBuilder buf = new StringBuilder();
			buf.append("Active votes: ");
			for(int i=0; i<channels.size(); i++) {
				buf.append(channels.get(i) + ": ");
				final List<ActiveVote> chanVotes = map.get(channels.get(i));
				for(int j=0; j<chanVotes.size(); j++) {
					final ActiveVote vote = chanVotes.get(j);
					buf.append("\"" + vote.text + "\" (Vote ID: " + vote.id + ")");
					if (j != chanVotes.size() - 1)
						buf.append(", ");
				}
				if (i != channels.size() - 1)
					buf.append("; ");
			}
			buf.append(".");
			irc.sendContextReply(mes, buf.toString());
		} else {
			// Shorter form for not spamming channels
			final StringBuilder buf = new StringBuilder();
			buf.append("Too many active votes; listing only IDs: ");
			for(int i=0; i<channels.size(); i++) {
				buf.append(channels.get(i) + ": ");
				final List<ActiveVote> chanVotes = map.get(channels.get(i));
				for(int j=0; j<chanVotes.size(); j++) {
					final ActiveVote vote = chanVotes.get(j);
					buf.append("" + vote.id);
					if (j != chanVotes.size() - 1)
						buf.append(", ");
				}
				if (i != channels.size() - 1)
					buf.append("; ");
			}
			buf.append(".");
			irc.sendContextReply(mes, buf.toString());
		}
	}

	public String[] helpCommandInfo = {
		"Get info on a vote.",
		"[<VoteID>]",
		"<VoteID> is the ID of a vote to query, if this isn't specified a list of active votes will be provided."
	};
	public void commandInfo(final Message mes) {
		final List<String> params = mods.util.getParams(mes, 1);

		if (params.size() == 1) {
			commandActiveVotes(mes);
			return;
		}

		int voteID;
		try {
			voteID = Integer.parseInt(params.get(1));
		} catch (final NumberFormatException e) {
			irc.sendContextReply(mes, "Sorry, " + params.get(1) + " is not a valid vote ID!");
			return;
		}

		final List<ActiveVote> votes = mods.odb.retrieve(ActiveVote.class, "WHERE id = " + voteID);

		if (votes.size() == 0) {
			irc.sendContextReply(mes, "Sorry, that vote doesn't exist!");
			return;
		}

		final ActiveVote vote = votes.get(0);
		if (vote.finished) {
			// Vote finished.
			final StringBuilder output = new StringBuilder();
			output.append("Vote " + vote.id + " (\"" + vote.text + "\") finished. It was called by " + vote.caller);
			output.append(" " + apiEncodePeriod(vote.startTime) + " and finished " + apiEncodePeriod(vote.finishTime));
			output.append(". Responses: ");
			final String[] responses = vote.responses.split(",");
			final String[] results = vote.results.split(",");
			for(int i=0; i<responses.length; i++) {
				output.append(responses[i] + " with " + results[i]);
				if (i == responses.length - 2)
					output.append(" and ");
				else if (i != responses.length - 1)
					output.append(", ");
			}
			output.append(".");
			irc.sendContextReply(mes, output.toString());
		} else {
			//Vote is still active.
			if (mes instanceof PrivateEvent) {
				//Determine the conext of the command, and if a PM be more verbose
				irc.sendContextReply(mes, "Vote #" + vote.id + " on \"" + vote.text + "\"! Finishes " + apiEncodePeriod(vote.finishTime) + ".");
				final String trigger = irc.getTrigger();
				final String[] options = vote.responses.split(",");
				for (int i=0;i<options.length;i++) {
					irc.sendContextReply(mes, trigger + "Vote.Vote " + i + "  ==>  " + options[i]);
				}
			} else {
				//Don't spam out the channel.
				final StringBuilder output = new StringBuilder();
				output.append("Vote " + vote.id + " (\"" + vote.text + "\") is still running. It was called by " + vote.caller);
				output.append(" " + apiEncodePeriod(vote.startTime) + " and finishes " + apiEncodePeriod(vote.finishTime));
				output.append(". Responses: ");
				final String[] responses = vote.responses.split(",");
				for(int i=0; i<responses.length; i++) {
					output.append(i).append(") \"").append(responses[i]).append("\"");
					if (i == responses.length - 2)
						output.append(" and ");
					else if (i != responses.length - 1)
						output.append(", ");
				}
				output.append(". Use ").append(Colors.BOLD).append(irc.getTrigger()).append("vote.vote ").append(vote.id).append(" {choice}").append(Colors.BOLD).append(" to vote!");
				irc.sendContextReply(mes, output.toString());
			}
		}
	}

	// TODO: export this.
	public long apiDecodePeriod(final String time) throws NumberFormatException {
		int period = 0;

		int currentPos = -1;
		int lastPos = 0;

		if ( (currentPos = time.indexOf('d', lastPos)) >= 0 ) {
			period += 60 * 60 * 24 * Integer.parseInt(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if ( (currentPos = time.indexOf('h', lastPos)) >= 0 ) {
			period += 60 * 60 * Integer.parseInt(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if ( (currentPos = time.indexOf('m', lastPos)) >= 0 ) {
			period += 60 * Integer.parseInt(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if ( (currentPos = time.indexOf('s', lastPos)) >= 0 ) {
			period += Integer.parseInt(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if (lastPos != time.length())
			throw new NumberFormatException("Invalid time format: " + time);

		return period;
	}

	public String apiEncodePeriod(final long time) {
		final int TOOLONG = 60 * 60 * 1000; // One hour
		long remain = time - System.currentTimeMillis();
		boolean past;
		if (remain < 0) {
			past = true;
			remain = -remain;
		} else {
			past = false;
		}

		if (remain > TOOLONG)
			return "at " + new Date(time);

		final StringBuilder out = new StringBuilder();

		if (!past)
			out.append("in ");

		int got = 0; // Number of fields we've got.
		remain /= 1000;
		if (remain > 60 * 60 * 24) {
			final long days = remain / (60 * 60 * 24);
			got++;
			out.append("" + days + " days").append(days==1 ? "" : "s").append(", ");
			remain = remain % (60 * 60 * 24);
		}
		if (remain > 60 * 60) {
			final long hours = remain / (60 * 60);
			if (got > 0)
				out.append(", ");
			got++;
			out.append("" + hours + " hour").append(hours==1 ? "" : "s").append(", ");
			remain = remain % (60 * 60);
		}
		if (remain > 60 && got < 2) {
			final long mins = remain / 60;
			if (got > 0)
				out.append(", ");
			got++;
			out.append("" + mins + " min").append(mins==1 ? "" : "s").append(", ");
			remain = remain % 60;
		}
		if (got == 0 || got == 1 && remain > 0)
			out.append("" + remain + " sec").append(remain==1 ? "" : "s");

		if (past)
			out.append(" ago");

		return out.toString();
	}

	public String[] helpCommandVote = {
		"Vote on an existing vote.",
		"[<VoteID>] <Response>",
		"<VoteID> is the optional vote ID - if the vote was called in the current context, this is not required",
		"<Response> is what to vote for; either the response number or name"
	};
	public synchronized void commandVote( final Message mes ) {
		final List<String> params = mods.util.getParams(mes, 2);

		int voteID;
		String response;
		if (params.size() == 2) {
			// Get the Vote ID...
			final Integer voteIDInt = activeVotes.get(mes.getContext());
			if (voteIDInt == null) {
				irc.sendContextReply(mes, "Sorry, no active vote here! You'll need to specify a vote ID.");
				return;
			}
			voteID = voteIDInt.intValue();
			response = params.get(1);
		} else if (params.size() == 3) {
			try {
				voteID = Integer.parseInt(params.get(1));
				response = params.get(2);
			} catch (final NumberFormatException e) {
				final Integer voteIDInt = activeVotes.get(mes.getContext());
				if (voteIDInt == null) {
					irc.sendContextReply(mes, "Sorry, " + params.get(1) + " is not a valid vote ID!");
					return;
				}
				voteID = voteIDInt.intValue();
				response = params.get(1) + " " + params.get(2);
			}
		} else {
			irc.sendContextReply(mes, "Syntax: 'Vote.Vote " + helpCommandVote[1] + "'.");
			return;
		}

		// OK, try to retrieve the vote.
		final List<ActiveVote> matching = mods.odb.retrieve(ActiveVote.class, "WHERE id = " + voteID + " AND finished = 0");

		if (matching.size() == 0) {
			irc.sendContextReply(mes, "Sorry, that vote seems to have expired.");
			return;
		}

		final ActiveVote vote = matching.get(0);

		if (vote.nickserv && !mods.security.hasAuth(mes)) {
			irc.sendContextReply(mes, "You must be authenticated with nickserv in order to cast a vote on this vote!");
			return;
		}

		final String[] responses = vote.responses.split(",");

		int responseID = -1;
		try {
			responseID = Integer.parseInt(response);
		} catch (final NumberFormatException e) {
			for(int i=0; i<responses.length; i++) {
				if (responses[i].equalsIgnoreCase(response))
					responseID = i;
			}
		}

		if (responseID < 0 || responseID >= responses.length) {
			irc.sendContextReply(mes, "Sorry, " + response + " is not a valid response for this vote!");
			return;
		}

		// Have they already voted?
		final List<Voter> voted = mods.odb.retrieve(Voter.class, "WHERE voteID = " + voteID + " AND nick = \"" + mods.odb.escapeString(mods.nick.getBestPrimaryNick(mes.getNick())) + "\"");

		if (voted.size() == 1) {
			final Voter existing = voted.get(0);
			existing.response = responseID;
			mods.odb.update(existing);
			irc.sendContextReply(mes, "OK, changed your vote to " + responses[responseID]);
		} else {
			final Voter voter = new Voter();
			voter.nick = mods.nick.getBestPrimaryNick(mes.getNick());
			voter.response = responseID;
			voter.voteID = voteID;
			mods.odb.save(voter);
			irc.sendContextReply(mes, "OK, you've voted for " + responses[responseID]);
		}
	}

	public String[] helpCommandAbstainAll = {
		"Abstain on all active votes you have yet to vote upon."
	};
	public synchronized void commandAbstainAll(final Message mes) {
		//The user doesn't care about any of the votes they haven't voted on, so abstain them in all of them.

		//First extract all the votes that the user can abstain in.
		List<ActiveVote> votes;
		if (mods.security.hasAuth(mes)) {
			votes = mods.odb.retrieve(ActiveVote.class, "WHERE finished = 0");
		} else {
			votes = mods.odb.retrieve(ActiveVote.class, "WHERE finished = 0 AND nickserv = 0");
		}

		//Iterate over all the votes.
		final StringBuilder buf = new StringBuilder();
		int abstainedVotes = 0;
		for (final ActiveVote vote: votes) {
			final List<Voter> voted = mods.odb.retrieve(Voter.class, "WHERE voteID = " + vote.id + " AND nick = \"" + mods.odb.escapeString(mods.nick.getBestPrimaryNick(mes.getNick())) + "\"");
			if (voted.size() != 1) {
				//The user hasn't voted... abstain!
				final Voter voter = new Voter();
				voter.nick = mods.nick.getBestPrimaryNick(mes.getNick());
				voter.response = 0;
				voter.voteID = vote.id;
				mods.odb.save(voter);
				buf.append(vote.id + ", ");
				abstainedVotes++;
			}
		}
		if (abstainedVotes == 0) {
			irc.sendContextReply(mes, "There were no active votes that you had yet to vote on.");
		} else {
			buf.append("there are now no active votes that you have yet to vote on.");
			if (abstainedVotes == 1) {
				irc.sendContextReply(mes, "Ok, abstained on vote " + buf.toString());
			} else {
				irc.sendContextReply(mes, "Ok, abstained on votes: " + buf.toString());
			}
		}
	}

	public synchronized void interval( final Object parameter ) {
		if (parameter != null && parameter instanceof ActiveVote) {
			// It's a vote ending.
			final ActiveVote vote = (ActiveVote)parameter;

			final List<Voter> votes = mods.odb.retrieve(Voter.class, "WHERE voteID = " + vote.id);

			final String[] responses = vote.responses.split(",");
			final int[] counts = new int[responses.length];

			for(final Voter voter: votes)
				counts[voter.response]++;

			// Gah! Inefficient, but Java doesn't provide a useful enough sort method!
			int max = 0;
			for (final int count : counts)
			{
				if (max < count)
					max = count;
			}

			final List<String> results = new ArrayList<String>();
			boolean first1 = true, first0 = true;
			List<String> winners = new ArrayList<String>();
			final StringBuilder newResponses = new StringBuilder();
			final StringBuilder newResults = new StringBuilder();
			for(int i = max; i >= 0; i--) {
				final List<String> elts = new ArrayList<String>();
				for(int j=0; j<counts.length; j++)
					if (counts[j] == i)
						elts.add(responses[j]);

				if (elts.size() > 0) {
					if (first1)
						winners = elts;
					first1 = false;

					final StringBuilder thisResult = new StringBuilder();
					boolean first2 = true;
					for(final String name: elts) {
						if (!first0) {
							newResponses.append(",");
							newResults.append(",");
						}
						first0 = false;
						newResponses.append(name);
						newResults.append("" + i);

						if (!first2)
							thisResult.append(", ");
						first2 = false;
						thisResult.append(name);
					}
					thisResult.append(" with " + i);
					results.add(thisResult.toString());
				}
			}
			vote.finished = true;
			vote.responses = newResponses.toString();
			vote.results = newResults.toString();

			// Delete all the buggers!
			mods.odb.runTransaction( new ObjectDBTransaction() {
				@Override
				public void run() {
					update(vote);
					for(final Voter voter: votes)
						delete(voter);
				}
			});

			//Check the option for verbose (i.e. spammy) output.
			if (checkOption("","VoteVerbose",true)) {
				irc.sendMessage(vote.channel, "Vote on \"" + vote.text + "\" has ended! Results:");
				for(final String result: results)
					irc.sendMessage(vote.channel, result);
			} else {
				final StringBuilder resultText = new StringBuilder();
				boolean first2 = true;
				for(final String result: results) {
					if (!first2)
						resultText.append("; ");
					first2 = false;
					resultText.append(result);
				}
				resultText.append(".");
				irc.sendMessage(vote.channel, "Vote on \"" + vote.text + "\" has ended! Results: " + resultText);
			}

			if (winners.size() == 1)
				irc.sendMessage(vote.channel, "Democracy has spoken; " + winners.get(0) + " is the winner!");
			else {
				final StringBuilder output = new StringBuilder();
				for(int i=0; i<winners.size(); i++) {
					output.append(winners.get(i));
					if (i != winners.size() - 1)
						output.append(", ");
					if (i == winners.size() - 2)
						output.append("and ");
				}
				if (max == 0) {
					irc.sendMessage(vote.channel, "Result is a draw, no options got any votes! What a waste of time!");
				} else if (max == 1) {
					irc.sendMessage(vote.channel, "Result is a draw: " + output + " all got 1 vote each!");
				} else {
					irc.sendMessage(vote.channel, "Result is a draw: " + output + " all got " + max + " votes!");
				}
			}

			//Send the user who called the vote a message with the results
			if (checkOption(vote.caller, "VoteResultMessage", false)) {
				irc.sendMessage(vote.caller, "Vote on \"" + vote.text + "\" has ended! Results: " + results);
				if (winners.size() == 1)
					irc.sendMessage(vote.caller, "The powers that be have picked " + winners.get(0) + " as the winner!");
				else {
					final StringBuilder output = new StringBuilder();
					for(int i=0; i<winners.size(); i++) {
						output.append(winners.get(i));
						if (i != winners.size() - 1)
							output.append(", ");
						if (i == winners.size() - 2)
							output.append("and ");
					}
					if (max == 0)
						irc.sendMessage(vote.caller, "There were no votes, what a waste of time!");
					else
						irc.sendMessage(vote.caller, "Result is a draw: " + output + " all got " + max + " votes!");
				}
			}
		}
	}

	/**
	 * Method in order to inform a user of new votes that they may wish to vote on that they have yet to do so.
	 */
	public synchronized void onJoin(final ChannelJoin ev) {
		if (ev.getLogin().equalsIgnoreCase("Choob")) {
			// XXX : Ignore bots, the quick and hacky way
			return;
		}

		//Check if the user has specified the option to have the notification enabled.
		if (checkOption(ev.getNick(),"VoteJoinNotify", false)) {
			//Get the active votes
			List<ActiveVote> votes;
			if (mods.security.hasAuth(ev)) {
				votes = mods.odb.retrieve(ActiveVote.class, "WHERE finished = 0");
			} else {
				votes = mods.odb.retrieve(ActiveVote.class, "WHERE finished = 0 AND nickserv = 0");
			}

			final Map<String,List<ActiveVote>> map = new HashMap<String,List<ActiveVote>>();
			final List<String> channels = new ArrayList<String>();
			//For each of the active votes, get it's channel and stick it in the arraylist
			for(final ActiveVote vote: votes) {
				List<ActiveVote> chanVotes = map.get(vote.channel);
				if (chanVotes == null) {
					chanVotes = new ArrayList<ActiveVote>();
					map.put(vote.channel, chanVotes);
					channels.add(vote.channel);
				}
				chanVotes.add(vote);
			}

			final StringBuilder buf = new StringBuilder();
			//Loop through the channels
			for(int i=0; i<channels.size(); i++) {
				//If the channel is the one the user just joined... let them know what votes there are
				if (channels.get(i).equals(ev.getChannel())) {
					buf.append("There are votes you have yet to vote in on " + channels.get(i) + ": ");
					final List<ActiveVote> chanVotes = map.get(channels.get(i));
					int voteSpamCount = 0; //Keep track of how many votes there are that we let the user know about
					for(int j=0; j<chanVotes.size(); j++) {
						final ActiveVote vote = chanVotes.get(j);
						final int voteID = vote.id;
						//But only if they've not voted
						final List<Voter> voted = mods.odb.retrieve(Voter.class, "WHERE voteID = " + voteID + " AND nick = \"" + mods.odb.escapeString(mods.nick.getBestPrimaryNick(ev.getNick())) + "\"");
						if (voted.size() != 1) {
							buf.append("\"" + vote.text + "\" (Vote ID: " + vote.id + ")");
							if (j != chanVotes.size() - 1)
								buf.append(", ");
							voteSpamCount++;
						}
					}
					//Only send the message if there are votes to inform the user of
					if (voteSpamCount != 0) {
						buf.append(". To stop this message appearing, please either vote or abstain on " +
							"these matters using the vote.vote command, or disable this feature using " +
							"'options.set vote VoteJoinNotify=0'.");
						irc.sendMessage(ev.getNick(), buf.toString());
					}
				}
			}
		}
	}

	private boolean checkOption(final String userNick, final String option, final boolean global) {
		try {
			String value;
			if (global) {
				value = (String)mods.plugin.callAPI("Options", "GetGeneralOption", option, "1");
			} else {
				value = (String)mods.plugin.callAPI("Options", "GetUserOption", userNick, option, "1");
			}
			final String[] parts = value.split(":", -1);
			return parts[0].equals("1");
		} catch (final ChoobNoSuchCallException e) {
			return true;
		}
	}
}
