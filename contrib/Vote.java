import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import uk.co.uwcs.choob.modules.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.SimpleDateFormat;

public class ActiveVote
{
	public int id;
	public String text;
	public String responses; // comma-seperated
	public String results; // same as responses
	public String caller;
	public String channel;
	public boolean finished;
	public long startTime;
	public long finishTime;
}

public class Voter
{
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
public class Vote
{
	public String[] info()
	{
		return new String[] {
			"Allows users to create polls to query about stuff.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			mods.util.getVersion()
		};
	}

	private static boolean SPAMMY = true;
	private Modules mods;
	private IRCInterface irc;
	private Map<String,Integer> activeVotes = new HashMap<String,Integer>();

	public Vote(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;

		// Reload all old queued objects...
		long time = System.currentTimeMillis();
		List<ActiveVote> votes = mods.odb.retrieve(ActiveVote.class, "WHERE finished = 0");
		for(ActiveVote vote: votes)
		{
			mods.interval.callBack( vote, vote.finishTime - time, vote.id );
		}
	}

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

	public String[] helpCommandCall = {
		"Call a vote for channel members to vote on. See Vote.Examples.",
		"[ <Question> ] [ ( <Responses> ) ] [ <Duration> ]",
		"<Question> is an optional question (default: \"Which is best?\")",
		"<Responses> is an optional comma seperated list of responses (default: Yes, No)",
		"<Duration> is an optional duration to run the vote of the form [<Days>d][<Hours>h][<Minutes>m][<Seconds>s] (default: 60s)"
	};
	public synchronized void commandCall( Message mes )
	{
		String paramString = mods.util.getParamString( mes ).trim();

		String question = "Which is best?";
		String[] options = new String[] { "Yes", "No" };
		int pos = -1;
		if (paramString.charAt(0) == '"')
		{
			// Has question.
			int endPos = paramString.indexOf('"', 1);
			question = paramString.substring(1, endPos);
			pos = endPos + 1;
		}
		else if (paramString.charAt(0) != '(')
		{
			int endPos = paramString.indexOf('(');
			if (endPos == -1)
			{
				question = paramString.trim();
			}
			else
			{
				question = paramString.substring(0, endPos).trim();
				pos = endPos;
			}
		}
		else if (paramString.indexOf('(') == -1)
		{
			irc.sendContextReply(mes, "Sorry, you need either a question or some options. Syntax: 'Vote.Call " + helpCommandCall[1] + "'. See Help.Help Vote.Examples." );
			return;
		}
		else
		{
			pos = paramString.indexOf('(');
		}

		if (pos != -1)
		{
			int startPos = paramString.indexOf('(', pos);
			int endPos = paramString.indexOf(')', startPos);
			if (endPos != -1)
			{
				options = paramString.substring(startPos + 1, endPos).split("\\s*,\\s*");
			}

			if ( options.length == 0 || endPos == -1 )
			{
				if (startPos != -1)
				{
					irc.sendContextReply(mes, "Invalid option string! Syntax: 'Vote.Call " + helpCommandCall[1] + "'. See Help.Help Vote.Examples." );
					return;
				}
			}
			else
			{
				// Remove trailing/leading spaces.
				options[0] = options[0].trim();
				options[options.length - 1] = options[options.length - 1].trim();

				pos = endPos + 1;
			}
		}
		else
		{
			pos = paramString.length();
		}

		// Anything after the end?
		String remain = paramString.substring(pos).trim();
		long duration = 60 * 1000;
		String durationString = "60s";
		if (remain.length() != 0)
		{
			try
			{
				duration = apiDecodePeriod(remain) * 1000;
				if (duration > 300000) // 5 mins
					durationString = "until " + new Date(System.currentTimeMillis() + duration);
				else
					durationString = remain;
			}
			catch (NumberFormatException e)
			{
				irc.sendContextReply(mes, "Invalid duration string! Syntax: 'Vote.Call " + helpCommandCall[1] + "'. See Help.Help Vote.Examples." );
				return;
			}
		}

		ActiveVote vote = new ActiveVote();
		vote.caller = mes.getNick();
		vote.channel = mes.getContext();
		vote.text = question;
		vote.finished = false;
		vote.results = "";
		vote.startTime = System.currentTimeMillis();
		vote.finishTime = System.currentTimeMillis() + duration;

		StringBuilder responseString = new StringBuilder("Abstain");
		for(int i=0; i<options.length; i++)
			responseString.append("," + options[i]);
		vote.responses = responseString.toString();

		mods.odb.save(vote);
		mods.interval.callBack( vote, duration, vote.id );

		activeVotes.put(mes.getContext(), vote.id);

		if (SPAMMY) // TODO: Make this an option
		{
			irc.sendContextReply(mes, "OK, called vote #" + vote.id + " on \"" + question + "\"! You have " + durationString + ".");
			String trigger = irc.getTrigger();
			irc.sendContextReply(mes, trigger + "Vote.Vote 0  ==>  Abstain");
			for(int i=0; i<options.length; i++)
				irc.sendContextReply(mes, trigger + "Vote.Vote " + (i + 1) + "  ==>  " + options[i]);
		}
		else
		{
			StringBuilder responseOutput = new StringBuilder("0 for Abstain");
			for(int i=0; i<options.length; i++)
				responseOutput.append(", " + (i + 1) + " for " + options[i]);

			irc.sendContextReply(mes, "OK, called vote on \"" + question + "\"! You have " + durationString + " to use 'Vote.Vote <Number>' here or 'Vote.Vote " + vote.id + " <Number>' elsewhere, where <Number> is one of:");
			irc.sendContextReply(mes, responseOutput + ".");
		}
	}

	public String[] helpCommandActiveVotes = {
		"List all active votes."
	};
	public void commandActiveVotes(Message mes)
	{
		List<ActiveVote> votes = mods.odb.retrieve(ActiveVote.class, "WHERE finished = 0");

		Map<String,List<ActiveVote>> map = new HashMap<String,List<ActiveVote>>();
		List<String> channels = new ArrayList<String>();
		for(ActiveVote vote: votes)
		{
			List<ActiveVote> chanVotes = map.get(vote.channel);
			if (chanVotes == null)
			{
				chanVotes = new ArrayList<ActiveVote>();
				map.put(vote.channel, chanVotes);
				channels.add(vote.channel);
			}
			chanVotes.add(vote);
		}

		if (channels.size() == 0)
		{
			irc.sendContextReply(mes, "Sorry, no active votes!");
		}
		else if (votes.size() < 6 || mes instanceof PrivateEvent)
		{
			StringBuilder buf = new StringBuilder();
			buf.append("Active votes: ");
			for(int i=0; i<channels.size(); i++)
			{
				buf.append(channels.get(i) + ": ");
				List<ActiveVote> chanVotes = map.get(channels.get(i));
				for(int j=0; j<chanVotes.size(); j++)
				{
					ActiveVote vote = chanVotes.get(j);
					buf.append("\"" + vote.text + "\" (ID " + vote.id + ")");
					if (j != chanVotes.size() - 1)
						buf.append(", ");
				}
				if (i != channels.size() - 1)
					buf.append("; ");
			}
			buf.append(".");
			irc.sendContextReply(mes, buf.toString());
		}
		else
		{
			// Shorter form for lots of votes...
			StringBuilder buf = new StringBuilder();
			buf.append("Too many active votes; listing only IDs: ");
			for(int i=0; i<channels.size(); i++)
			{
				buf.append(channels.get(i) + ": ");
				List<ActiveVote> chanVotes = map.get(channels.get(i));
				for(int j=0; j<chanVotes.size(); j++)
				{
					ActiveVote vote = chanVotes.get(j);
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
		"<VoteID>",
		"<VoteID> is the ID of a vote to query"
	};
	public void commandInfo(Message mes)
	{
		List<String> params = mods.util.getParams(mes, 1);

		if (params.size() == 1)
		{
			irc.sendContextReply(mes, "Syntax: 'VoteInfo " + helpCommandInfo[1] + "'.");
			return;
		}

		int voteID;
		try
		{
			voteID = Integer.parseInt(params.get(1));
		}
		catch (NumberFormatException e)
		{
			irc.sendContextReply(mes, "Sorry, " + params.get(1) + " is not a valid vote ID!");
			return;
		}

		List<ActiveVote> votes = mods.odb.retrieve(ActiveVote.class, "WHERE id = " + voteID);

		if (votes.size() == 0)
		{
			irc.sendContextReply(mes, "Sorry, that vote doesn't exist!");
			return;
		}

		ActiveVote vote = votes.get(0);
		if (vote.finished)
		{
			// Vote finished.
			StringBuilder output = new StringBuilder();
			output.append("Vote " + vote.id + " (\"" + vote.text + "\") finished. It was called by " + vote.caller);
			output.append(" " + apiEncodePeriod(vote.startTime) + " and finished " + apiEncodePeriod(vote.finishTime));
			output.append(". Responses: ");
			String[] responses = vote.responses.split(",");
			String[] results = vote.results.split(",");
			for(int i=0; i<responses.length; i++)
			{
				output.append(responses[i] + " with " + results[i]);
				if (i == responses.length - 2)
					output.append(" and ");
				else if (i != responses.length - 1)
					output.append(", ");
			}
			output.append(".");
			irc.sendContextReply(mes, output.toString());
		}
		else
		{
			// Vote still running.
			StringBuilder output = new StringBuilder();
			output.append("Vote " + vote.id + " (\"" + vote.text + "\") is still running. It was called by " + vote.caller);
			output.append(" " + apiEncodePeriod(vote.startTime) + " and finishes " + apiEncodePeriod(vote.finishTime));
			output.append(". Responses: ");
			String[] responses = vote.responses.split(",");
			for(int i=0; i<responses.length; i++)
			{
				output.append(responses[i]);
				if (i == responses.length - 2)
					output.append(" and ");
				else if (i != responses.length - 1)
					output.append(", ");
			}
			output.append(".");
			irc.sendContextReply(mes, output.toString());
		}
	}

	// TODO: export this.
	public long apiDecodePeriod(String time) throws NumberFormatException {
		int period = 0;

		int currentPos = -1;
		int lastPos = 0;

		if ( (currentPos = time.indexOf('d', lastPos)) >= 0 ) {
			period += 60 * 60 * 24 * Integer.valueOf(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if ( (currentPos = time.indexOf('h', lastPos)) >= 0 ) {
			period += 60 * 60 * Integer.valueOf(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if ( (currentPos = time.indexOf('m', lastPos)) >= 0 ) {
			period += 60 * Integer.valueOf(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if ( (currentPos = time.indexOf('s', lastPos)) >= 0 ) {
			period += Integer.valueOf(time.substring(lastPos, currentPos));
			lastPos = currentPos + 1;
		}

		if (lastPos != time.length())
			throw new NumberFormatException("Invalid time format: " + time);

		return period;
	}

	public String apiEncodePeriod(long time)
	{
		int TOOLONG = 60 * 60 * 1000; // One hour
		long remain = time - System.currentTimeMillis();
		boolean past;
		if (remain < 0)
		{
			past = true;
			remain = -remain;
		}
		else
		{
			past = false;
		}

		if (remain > TOOLONG)
			return "at " + (new Date(time));

		StringBuilder out = new StringBuilder();

		if (!past)
			out.append("in ");

		int got = 0; // Number of fields we've got.
		remain /= 1000;
		if (remain > 60 * 60 * 24)
		{
			long days = remain / (60 * 60 * 24);
			got++;
			out.append("" + days + " days");
			remain = remain % (60 * 60 * 24);
		}
		if (remain > 60 * 60)
		{
			long hours = remain / (60 * 60);
			if (got > 0)
				out.append(", ");
			got++;
			out.append("" + hours + " hours");
			remain = remain % (60 * 60);
		}
		if (remain > 60 && got < 2)
		{
			long mins = remain / 60;
			if (got > 0)
				out.append(", ");
			got++;
			out.append("" + mins + " mins");
			remain = remain % 60;
		}
		if (got == 0 || (got == 1 && remain > 0))
		{
			out.append("" + remain + " secs");
		}

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
	public synchronized void commandVote( Message mes )
	{
		List<String> params = mods.util.getParams(mes, 2);

		int voteID;
		String response;
		if (params.size() == 2)
		{
			// Get the Vote ID...
			Integer voteIDInt = activeVotes.get(mes.getContext());
			if (voteIDInt == null)
			{
				irc.sendContextReply(mes, "Sorry, no active vote here! You'll need to specify a vote ID.");
				return;
			}
			voteID = voteIDInt;
			response = params.get(1);
		}
		else if (params.size() == 3)
		{
			try
			{
				voteID = Integer.parseInt(params.get(1));
				response = params.get(2);
			}
			catch (NumberFormatException e)
			{
				Integer voteIDInt = activeVotes.get(mes.getContext());
				if (voteIDInt == null)
				{
					irc.sendContextReply(mes, "Sorry, " + params.get(1) + " is not a valid vote ID!");
					return;
				}
				voteID = voteIDInt;
				response = params.get(1) + " " + params.get(2);
			}
		}
		else
		{
			irc.sendContextReply(mes, "Syntax: 'Vote.Vote " + helpCommandVote[1] + "'.");
			return;
		}

		// OK, try to retrieve the vote.
		List<ActiveVote> matching = mods.odb.retrieve(ActiveVote.class, "WHERE id = " + voteID);

		if (matching.size() == 0)
		{
			irc.sendContextReply(mes, "Sorry, that vote seems to have expired.");
			return;
		}

		ActiveVote vote = matching.get(0);
		String[] responses = vote.responses.split(",");

		int responseID = -1;
		try
		{
			responseID = Integer.parseInt(response);
		}
		catch (NumberFormatException e)
		{
			for(int i=0; i<responses.length; i++)
			{
				if (responses[i].equalsIgnoreCase(response))
					responseID = i;
			}
		}

		if (responseID < 0 || responseID >= responses.length)
		{
			irc.sendContextReply(mes, "Sorry, " + response + " is not a valid response for this vote!");
			return;
		}

		// Have they already voted?
		List<Voter> voted = mods.odb.retrieve(Voter.class, "WHERE voteID = " + voteID + " AND nick = \"" + mes.getNick().replaceAll("([\\\\\"])", "\\\\$1") + "\"");

		if (voted.size() == 1)
		{
			Voter existing = voted.get(0);
			existing.response = responseID;
			mods.odb.update(existing);
			irc.sendContextReply(mes, "OK, changed your vote to " + responses[responseID]);
		}
		else
		{
			Voter voter = new Voter();
			voter.nick = mes.getNick();
			voter.response = responseID;
			voter.voteID = voteID;
			mods.odb.save(voter);
			irc.sendContextReply(mes, "OK, you've voted for " + responses[responseID]);
		}
	}

	public synchronized void interval( Object parameter ) throws ChoobException
	{
		if (parameter != null && parameter instanceof ActiveVote) {
			// It's a vote ending.
			final ActiveVote vote = (ActiveVote)parameter;

			final List<Voter> votes = mods.odb.retrieve(Voter.class, "WHERE voteID = " + vote.id);

			String[] responses = vote.responses.split(",");
			int[] counts = new int[responses.length];

			for(Voter voter: votes)
				counts[voter.response]++;

			// Gah! Inefficient, but Java doesn't provide a useful enough sort method!
			int max = 0;
			for(int i=0; i<counts.length; i++)
			{
				if (max < counts[i])
					max = counts[i];
			}

			List<String> results = new ArrayList<String>();
			boolean first1 = true, first0 = true;
			List<String> winners = null;
			StringBuilder newResponses = new StringBuilder();
			StringBuilder newResults = new StringBuilder();
			for(int i = max; i >= 0; i--)
			{
				List<String> elts = new ArrayList<String>();
				for(int j=0; j<counts.length; j++)
					if (counts[j] == i)
						elts.add(responses[j]);

				if (elts.size() > 0)
				{
					if (first1)
						winners = elts;
					first1 = false;

					StringBuilder thisResult = new StringBuilder();
					boolean first2 = true;
					for(String name: elts)
					{
						if (!first0)
						{
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
				public void run() {
					update(vote);
					for(Voter voter: votes)
						delete(voter);
				}
			});

			if (SPAMMY)
			{
				irc.sendMessage(vote.channel, "Vote on \"" + vote.text + "\" has ended! Results:");
				for(String result: results)
					irc.sendMessage(vote.channel, result);
			}
			else
			{
				StringBuilder resultText = new StringBuilder();
				boolean first2 = true;
				for(String result: results)
				{
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
			else
			{
				StringBuilder output = new StringBuilder();
				for(int i=0; i<winners.size(); i++)
				{
					output.append(winners.get(i));
					if (i != winners.size() - 1)
						output.append(", ");
					if (i == winners.size() - 2)
						output.append("and ");
				}
				irc.sendMessage(vote.channel, "Result is a draw: " + output + " all got " + max + " votes!");
			}
			// Should these be enabled?
			//irc.sendMessage(vote.caller, "Vote on \"" + vote.text + "\" has ended! Results: " + results);
			//irc.sendMessage(vote.caller, "The powers that be have picked " + responses[win] + " as the winner!");
		}
	}
}
