/** @author Faux */

import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.text.*;
import java.io.*;

import rath.msnm.*;
import rath.msnm.event.*;
import rath.msnm.entity.*;
import rath.msnm.msg.*;

public class Msn
{
	private IRCInterface irc;
	private Modules mods;

	public Msn (Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;


		MSNMessenger msn = new MSNMessenger("choobchoobchoobchoobmsndetailssnaaake@hotmail.com", "passwordpasswordpasswordkeyphrasesnaaaake");

		// set the initial status to online.

		msn.setInitialStatus(UserStatus.ONLINE);

		// register your pre-created adapter to msn

		MsnAdapter ym=new MsnAdapter();
		ym.setupMSNAdapter(msn, irc);
		msn.addMsnListener(ym);

		// login into the msn network

		msn.login();

		System.out.println("Waiting for the response....");


	}
}
/*
class MsnAdapter extends MsnAdapter
{

	MSNMessenger msn;

	IRCInterface irc;

	public void setupMsnAdapter(MSNMessenger msn, IRCInterface irc)
	{
		this.msn = msn;
		this.irc=irc;
	}

	public void instantMessageReceived(SwitchboardSession ss, MsnFriend friend, MimeMessage mime)
	{

		System.out.println(friend.getFriendlyName()+" send me some msg:"+mime.getMessage());

		irc.sendMessage("#bots", friend.getLoginName() + " says: " + mime.getMessage());

		try
		{
			// create a new message

			MimeMessage mm = new MimeMessage();

			mm.setMessage("Pang!");

			mm.setKind(mm.KIND_MESSAGE);

			if (msn.sendMessage(mm,ss))
				System.out.println("send msg ok!");
			else
				System.out.println("send msg faild!");

		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
}
*/