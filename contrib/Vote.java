import uk.co.uwcs.choob.*;
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
	public String caller;
	public String channel;
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
	private Modules mods;
	private IRCInterface irc;
	private Map<String,Integer> activeVotes = new HashMap<String,Integer>();

	public Vote(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;

		// Reload all old queued objects...
		long time = System.currentTimeMillis();
		List<ActiveVote> votes = mods.odb.retrieve(ActiveVote.class, "WHERE 1");
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
		  "'Vote.Call \"Do you like George Bush?\"' to ask for opinions on our"
		+ " favourite US president.",
		  "'Vote.Call \"Longest?\" (String, Snake, Worm)' to ask which people"
		+ " think is the longest."
	};

	public String[] helpCommandCall = {
		"Call a vote for channel members to vote on. See Vote.Examples.",
		"[\"<Question>\"] [ ( <Response>, <Response>, ... ) ]",
		"<Quoestion> is an optional question (default: \"Which is best?\")",
		"<Response> is a comma seperated list of responses"
	};
	public synchronized void commandCall( Message mes )
	{
		String paramString = mods.util.getParamString( mes );

		int pos = paramString.indexOf('"');
		String question;
		if (pos == 0 && paramString.charAt(0) == '"')
		{
			// Has question.
			int endPos = paramString.indexOf('"', 1);
			question = paramString.substring(1, endPos);
			pos = paramString.indexOf('(', endPos);
		}
		else if (pos == -1)
		{
			pos = paramString.indexOf('(');
			if (pos == -1)
			{
				irc.sendContextReply(mes, "Sorry, you need either a question or some options. Syntax: 'Vote.Call " + helpCommandCall[1] + "'. See Help.Help Vote.Examples." );
				return;
			}
			question = "Which is best?";
		}
		else
		{
			irc.sendContextReply(mes, "Syntax: 'Vote.Call " + helpCommandCall[1] + "'. See Help.Help Vote.Examples." );
			return;
		}

		String[] options;
		if (pos != -1)
		{
			int endPos = paramString.indexOf(')', pos);
			if (endPos == -1)
			{
				options = new String[0];
			}
			else
			{
				options = paramString.substring(pos + 1, endPos).split("\\s*,\\s*");
			}

			if ( options.length == 0 )
			{
				irc.sendContextReply(mes, "Invalid option string! Syntax: 'Vote.Call " + helpCommandCall[1] + "'. See Help.Help Vote.Examples." );
				return;
			}

			// Remove trailing/leading spaces.
			options[0] = options[0].trim();
			options[options.length - 1] = options[options.length - 1].trim();
		}
		else
		{
			options = new String[] { "Yes", "No" };
		}

		ActiveVote vote = new ActiveVote();
		vote.caller = mes.getNick();
		vote.channel = mes.getContext();
		vote.text = question;
		vote.finishTime = System.currentTimeMillis() + 60 * 1000; // TODO

		StringBuilder responseString = new StringBuilder("Abstain");
		StringBuilder responseOutput = new StringBuilder("0 for Abstain");
		for(int i=0; i<options.length; i++)
		{
			responseString.append("," + options[i]);
			responseOutput.append(", " + (i + 1) + " for " + options[i]);
		}
		vote.responses = responseString.toString();

		mods.odb.save(vote);
		mods.interval.callBack( vote, 60 * 1000, vote.id );

		activeVotes.put(mes.getContext(), vote.id);

		irc.sendContextReply(mes, "OK, called vote on \"" + question + "\"! Use 'Vote.Vote <Number>' here or 'Vote.Vote " + vote.id + " <Number>' elsewhere, where <Number> is: " + responseOutput + ".");
	}

	public String[] helpCommandVote = {
		"Vote on an existing vote.",
		"[<VoteID>] <Response>",
		"<VoteID> is the optional vote ID - if the vote was called in the current context, this is not required",
		"<Response> is what to vote for; either the response number or name"
	};
	public synchronized void commandVote( Message mes )
	{
		List<String> params = mods.util.getParams(mes, 3);

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
			}
			catch (NumberFormatException e)
			{
				irc.sendContextReply(mes, "Sorry, " + params.get(1) + " is not a valid vote ID!");
				return;
			}
			response = params.get(2);
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
		List<Voter> voted = mods.odb.retrieve(Voter.class, "voteID = " + voteID + " AND voter = \"" + mes.getNick().replaceAll("([\\\\\"])", "\\\\$1") + "\"");

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

			// Delete all the buggers!
			mods.odb.runTransaction( new ObjectDBTransaction() {
				public void run() {
					delete(vote);
					for(Voter voter: votes)
						delete(voter);
				}
			});

			// Gah! Inefficient, but Java doesn't proved a useful enough sort method!
			int max = 0;
			for(int i=0; i<counts.length; i++)
			{
				if (max < counts[i])
					max = counts[i];
			}

			StringBuffer results = new StringBuffer();
			int win = -1;
			for(int i = max; i >= 0; i--)
			{
				for(int j=0; j<counts.length; j++)
				{
					if (counts[j] == i)
					{
						if (win == -1)
							win = j;
						results.append(responses[j] + " with " + i + ", ");
					}
				}
			}
			results.append("and so the powers that be have picked " + responses[win] + " as the winner!");

			irc.sendMessage(vote.channel, "Vote on \"" + vote.text + "\" has ended! Results: " + results);
		}
	}
}
