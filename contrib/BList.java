import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;
import java.io.*;

public class ListItem
{
	public int id;

	public String key;
	public String content;

	public ListItem()
	{
	}

	public ListItem(String key, String content)
	{
		this.key = key.toLowerCase();
		this.content = content;
	}
}

public class BList
{
	public String[] info()
	{
		return new String[] {
			"Plugin to store lists of associated items.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}
	private Modules mods;
	private IRCInterface irc;

	public BList(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public String[] helpCommandGet = {
		"Returns a random item from specified list",
		"<ListName> [<Regex>]",
		"<ListName> is the name of the list to look in.",
		"<Regex> is a regex to restrict matching items in the list"
	};
	public void commandGet(Message mes)
	{
		List<String> params = mods.util.getParams(mes,2);
		if ((params.size() < 2) || (params.size() > 3))
		{
			irc.sendContextReply(mes,"Usage: Get <ListName> [<Regex>]");
			return;
		}
		List thisList = null;
		String key = params.get(1).toLowerCase();
		if (params.size() == 2)
		{
			thisList = get(key);
		}
		if (params.size() == 3)
		{
			thisList = get(key,params.get(2));
		}
		if ((thisList == null) || (thisList.size() == 0))
		{
			irc.sendContextReply(mes,"Could not find an item matching your criteria");
			return;
		}
		ListItem item = (ListItem)thisList.get(0);
		String permStr = key + "." + mes.getTarget();

		if ((mes.getTarget() == null) || (mods.security.hasPluginPerm(new ChoobPermission(permStr), "BList")))
			irc.sendContextReply(mes,item.content);
		else
		{
// 			!security.grant plugin.blist Choob listname.#channelname
			irc.sendMessage(mes.getNick(),"Sorry, you may not use that command in " + mes.getTarget() + ", if appropriate ask an admin to grant: " + permStr + ". Private messaging output to you instead.");
			irc.sendMessage(mes.getNick(),item.content);
		}
	}

	public String[] helpCommandCount = {
		"Counts matching items from specified list",
		"<ListName> [<Regex>]",
		"<ListName> is the name of the list to look in.",
		"<Regex> is a regex to restrict matching items in the list"
	};
	public void commandCount(Message mes)
	{
		List<String> params = mods.util.getParams(mes,2);
		if ((params.size() < 2) || (params.size() > 3))
		{
			irc.sendContextReply(mes,"Usage: Count <ListName> [<Regex>]");
			return;
		}
		List thisList = null;
		String key = params.get(1).toLowerCase();
		if (params.size() == 2)
		{
			thisList = get(key);
		}
		if (params.size() == 3)
		{
			thisList = get(key,params.get(2));
		}
		if (thisList == null)
			irc.sendContextReply(mes,"There are 0 items in specified list");
		else
			irc.sendContextReply(mes,"There are " + thisList.size() + " items in specified list");
	}

	private List<ListItem> get(String key)
	{
		return get(key,null);
	}

	private List<ListItem> get(String key, String regex)
	{
		if (regex == null)
		{
			return mods.odb.retrieve( ListItem.class , "SORT RANDOM WHERE key = \"" + key + "\"");
		} else
		{
			return mods.odb.retrieve( ListItem.class , "SORT RANDOM WHERE key = \"" + key + "\" AND content REGEXP'.*" + regex + ".*'");
		}
	}

	private HashSet<String> search(String term)
	{
		HashSet<String> toReturn = new HashSet<String>();
		for (Object item : (mods.odb.retrieve( ListItem.class , "WHERE key REGEXP '.*" + term + ".*'")))
		{
			toReturn.add(((ListItem)item).key);
		}
		return toReturn;
	}


	private boolean dupe(String key, String content)
	{
		for (ListItem item : get(key))
		{
			if (item.content.equals(content)) return true;
		}
		return false;
	}

	public String[] helpCommandAdd = {
		"Adds a string to a list",
		"<ListName> <String>",
		"<ListName> is the name of the list to add this string to.",
		"<String> is the string to add."
	};
	public void commandAdd(Message mes)
	{
		mods.security.checkNickPerm(new ChoobPermission("plugins.blist.add"), mes);
		List<String> params = mods.util.getParams(mes,2);

		if (params.size()<3)
		{
			irc.sendContextReply(mes, "Please specify both item and list.");
			return;
		}
		String key = params.get(1);
		String content = params.get(2);
		if (dupe(key,content))
		{
			irc.sendContextReply(mes,"That item already exists within this list");
			return;
		}
		try
		{
 			ListItem listItem = new ListItem(key,content);
			mods.odb.save( listItem );
			irc.sendContextReply(mes, "Ok, added item to list");
		}
		catch( IllegalStateException e )
		{	}
	}

	public String[] helpCommandAddFromFile = {
		"Adds all the lines in a file to a specified list",
		"<ListName> <URL>",
		"<ListName> is the name of the list to add this string to.",
		"<URL> is the URL to the file containing the strings to add."
	};
    public void commandAddFromFile(Message mes)
    {
		mods.security.checkNickPerm(new ChoobPermission("plugins.blist.addfromfile"), mes);
		List<String> params = mods.util.getParams(mes,2);
		if (params.size() < 3)
		{
			irc.sendContextReply(mes,"Usage: AddFromFile <ListName> <URL>");
			return;
		}
		String key = params.get(1);
		String url = params.get(2);
		String[] content = new String[0];
		int added = 0;
		try
		{
			URL thisUrl = new URL(url);
			URLConnection urlConnection = thisUrl.openConnection();
			InputStreamReader inputStream = new InputStreamReader(urlConnection.getInputStream());
			BufferedReader inputBuffer= new BufferedReader(inputStream);
			String nextLine = "";
			while (nextLine != null)
			{
				nextLine = inputBuffer.readLine();
				if ((nextLine != null) && (nextLine.length() > 0))
				{
					if (!(dupe(key,nextLine)))
					{
						try
						{
							ListItem listItem = new ListItem(key,nextLine);
							mods.odb.save( listItem );
							added++;
						}
						catch( IllegalStateException e )
						{	}
					}
				}
			}
		} catch(MalformedURLException e)
		{
			System.out.println("The url you specified appears to be invalid " + e.toString() );
			return;
		} catch(IOException  e)
		{
			System.out.println("Error reading specified file " + e.toString() );
			return;
		}

		irc.sendContextReply(mes,"Successfully added " + added + " items to list: " + key);
    }

	public String[] helpCommandDeleteList = {
		"Deletes all the data in specified list",
		"<ListName>",
		"<ListName> is the name of the list to delete."
	};
	public void commandDeleteList(Message mes)
	{
		mods.security.checkNickPerm(new ChoobPermission("plugins.blist.deletelist"), mes);
		List<String> params = mods.util.getParams(mes,2);
		if (params.size()<2)
		{
			irc.sendContextReply(mes, "Delete what list?");
			return;
		}

		String key = params.get(1).toLowerCase();
		try
		{
			List thisList = mods.odb.retrieve( ListItem.class , "WHERE key =\"" + key + "\"");
			for (Object item : thisList)
			{
				mods.odb.delete(item);
			}
		}
		catch( IllegalStateException e )
		{	}
		irc.sendContextReply(mes,"Ok, deleted specified list");
	}

	public String[] helpCommandDeleteMatching = {
		"Deletes all the matching lines from specified list",
		"<ListName> <Regex>",
		"<ListName> is the name of the list to delete items from.",
		"<Regex> is the pattern to match strings to be deleted with."
	};
	public void commandDeleteMatching(Message mes)
	{
		mods.security.checkNickPerm(new ChoobPermission("plugins.blist.deletematching"), mes);
		List<String> params = mods.util.getParams(mes,2);
		if (params.size() != 3)
		{
			irc.sendContextReply(mes,"Usage: DeleteMatching <ListName> <Regex>");
			return;
		}

		String key = params.get(1).toLowerCase();
		String regex = params.get(2);
		int deleted = 0;
		try
		{
			List thisList = mods.odb.retrieve( ListItem.class , "WHERE key =\"" + key + "\" AND content REGEXP'.*" + regex + ".*'");
			for (Object item : thisList)
			{
				mods.odb.delete(item);
				deleted++;
			}
		}
		catch( IllegalStateException e )
		{	}

		irc.sendContextReply(mes,"Ok, deleted " + deleted + " matching items");
	}

	public String[] helpCommandList = {
		"Lists all matching lists",
		"[<Regex>]",
		"<Regex> is the pattern to match when searching for lists, leave blank to search for all lists."
	};
	public void commandList(Message mes)
	{
		List<String> params = mods.util.getParams(mes,1);
		HashSet<String> lists = new HashSet<String>();
		if (params.size() < 2)
		{
			lists = search("");
		} else
		{
			lists = search(params.get(1));
		}
		if (lists.size() == 0)
		{
			irc.sendContextReply(mes,"No lists found");
			return;
		}
		String toMsg = "Matching lists: ";
		int maxNo = 50;
		for (String str : lists)
		{
			if (maxNo > 0)
			{
				toMsg = toMsg + str + " ";
			}
			else
			{
				toMsg = toMsg + "... Specify a search term to narrow results";
				break;
			}
			maxNo--;
		}
		irc.sendContextReply(mes,toMsg);
	}
}
