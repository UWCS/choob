import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.support.events.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;


public class Test
{
	public String[] info()
	{
		return new String[] {
			"Plugin containing various test routines.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	private Modules mods;
	private IRCInterface irc;
	public Test(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public void commandSecurity( Message con )
	{
		String priv = mods.util.getParamString( con );

		if (priv.trim().equals(""))
		{
			irc.sendContextReply(con, "You have to specify an argument.");
			return;
		}

		if ( mods.security.hasNickPerm( new ChoobPermission(priv), con ) )
			irc.sendContextReply(con, "You do indeed have " + priv + "!" );
		else
			irc.sendContextReply(con, "You don't have " + priv + "!" );
	}

	public void commandJoin( Message con ) throws ChoobException
	{
		irc.join(mods.util.getParamString(con));
		irc.sendContextReply(con, "Okay!");
	}

	public void commandPart( Message con ) throws ChoobException
	{
		irc.part(mods.util.getParamString(con));
		irc.sendContextReply(con, "Okay!");
	}


	public void commandPirate( Message con )
	{
		irc.sendContextReply(con, "Yarr!");
	}

	public void commandPiratey( Message con )
	{
		irc.sendContextReply(con, "(:;test.piratey:)");
	}

	public void commandInMy( Message con )
	{
		irc.sendContextMessage(con, "..Pants!");
	}

	public void commandExit( Message con ) throws ChoobException
	{
		List<String> params = mods.util.getParams( con );
		if (params.size() > 1) {
			System.exit(new Integer(params.get(1)));
		} else {
			irc.quit("Bye bye!");
		}
	}

	public void commandRestart( Message con ) throws ChoobException
	{
		irc.restart("Restarting...");
	}

	// Define the regex for the KarmaPlus filter.
	public String filterFauxRegex = "Faux sucks";

	public void filterFaux( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextMessage( con, "No, I disagree, " + con.getNick() + " is the one that is the suck.");
	}

	public String filterBouncyRegex = "^bouncy bouncy";

	public void filterBouncy( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextReply( con, "Ooh, yes please.");
	}

/*	public void onJoin( ChannelJoin ev )
	{
		if (ev.getLogin().equals("Choob"))
			return;

		String quote=null;
		try
		{
			quote=(String)mods.plugin.callAPI( "Quote", "SingleLineQuote", ev.getNick(), ev.getContext());
		}
		catch (ChoobNoSuchCallException e)
		{
		}

		if ( !ev.getNick().toLowerCase().startsWith("murph") )
		{
			if (quote == null)
				irc.sendContextMessage( ev, "Hello, " + ev.getNick() + "!");
			else
				irc.sendContextMessage( ev, "Hello, " + ev.getNick() + ": \"" + quote + "\"");
		}
	}*/

	public void onPart( ChannelPart ev, Modules mod, IRCInterface irc )
	{
		//irc.sendContextMessage( ev, "Bye, " + ev.getNick() + "!");
	}

	public void commandAPI ( Message mes ) throws ChoobException
	{
		List<String> params = mods.util.getParams( mes );
		irc.sendContextReply(mes, mods.plugin.callAPI( params.get(1), params.get(2), params.get(3) ).toString());
	}

	public void commandGeneric ( Message mes ) throws ChoobException
	{
		List<String> params = mods.util.getParams( mes );
		if (params.size() == 5)
			irc.sendContextReply(mes, mods.plugin.callGeneric( params.get(1), params.get(2), params.get(3), params.get(4) ).toString());
		else
			irc.sendContextReply(mes, mods.plugin.callGeneric( params.get(1), params.get(2), params.get(3) ).toString());
	}

	public void commandWait (Message mes)
	{
		Object test = new Object();
		synchronized(test)
		{
			try
			{
				test.wait(3000);
			}
			catch (InterruptedException e)
			{
				irc.sendContextReply(mes, "Interrupted!");
			}
			irc.sendContextReply(mes, "OK, waited.");
		}
	}

	// Test the stack is working.
/*	public void commandStack ( Message mes ) throws ChoobNoSuchCallException
	{
		int i = 0;
		for(String s = ChoobThread.getPluginName(0); s != null; s = ChoobThread.getPluginName(++i))
			irc.sendContextReply( mes, "Command stack: " + ChoobThread.currentThread().toString() + "(" + i + "): " + s );
		apiStack(mes, mods, irc, 5);
	}

	public void apiStack ( Message mes, Integer j ) throws ChoobNoSuchCallException
	{
		System.out.println("Stacking!");
		if ( j == 0 )
		{
			int i = 0;
			// Should be 6: One for command, then 6 API calls, but one of which direct.
			for(String s = ChoobThread.getPluginName(0); s != null; s = ChoobThread.getPluginName(++i))
				irc.sendContextReply( mes, "API Stack: " + ChoobThread.currentThread().toString() + "(" + i + "): " + s );
		}
		else
		{
			mods.plugin.callAPI("Test", "Stack", mes, mods, irc, new Integer(j - 1));
		}
	}*/

/*	public void onPluginLoaded (PluginLoaded mes)
	{
		irc.sendMessage("#bots", "Yay! Plugin loaded! Name is: " + mes.getPluginName());
	}

	public void onPluginUnLoaded (PluginUnLoaded mes)
	{
		irc.sendMessage("#bots", "Boo! Plugin unloaded! Name is: " + mes.getPluginName());
	}

	public void onPluginReLoaded (PluginReLoaded mes)
	{
		irc.sendMessage("#bots", "Boo! Plugin reloaded! Name is: " + mes.getPluginName());
	}*/

	/*public void commandWhoreApi ( Message mes ) throws ChoobException
	{
		for(int i=0; i < 100000; i++)
			try { mods.plugin.callAPI("Test", "Whore", "Iteration" + i, mes); } catch ( ChoobNoSuchCallException e ) { }
		irc.sendContextReply(mes, "OK, API whored!");
	}

	public void apiWhore(String text, Message mes)
	{
		//System.out.println("Whorage called by " + mes.getNick() + ": " + text);
	}*/

	/*public void commandWhoreODB ( final Message mes, final Modules mods, final IRCInterface irc ) throws ChoobException
	{
		mods.odb.runTransaction( new ObjectDBTransaction() { public void run() {
		int n = 1000;
		int count;
		irc.sendContextReply(mes, "Beginning whorage. n = " + n);
		long time1, time2, time3;
		List results = null;

		TestObj1 t1 = new TestObj1();
		for(int i=1; i<n+10; i++)
		{
			t1.id = i;
			//try { mods.odb.delete(t1); } catch (Exception e) { }
			try { delete(t1); } catch (Exception e) { }
		}
		time1 = System.currentTimeMillis();
		for(int i=1; i<n+1; i++)
		{
			t1.id = i;
			//try { mods.odb.save(t1); } catch (Exception e) { }
			try { save(t1); } catch (Exception e) { }
		}
		time2 = System.currentTimeMillis();
		//try { results = mods.odb.retrieve(TestObj1.class, null); } catch ( Exception e ) { };
		try { results = retrieve(TestObj1.class, null); } catch ( Exception e ) { };
		time3 = System.currentTimeMillis();

		irc.sendContextReply(mes, "End of stage 1. Time differences: " + (time2 - time1) + ", " + (time3 - time2) + ", size is " + results.size() + ".");

		TestObj2 t2 = new TestObj2();
		t2.var1 = "Supacalafragalistic";
		t2.var2 = "Supacalafragalistic";
		t2.var3 = "Supacalafragalistic";
		t2.var4 = "Supacalafragalistic";
		t2.var5 = "Supacalafragalistic";
		t2.var6 = "Supacalafragalistic";
		t2.var7 = "Supacalafragalistic";
		t2.var8 = "Supacalafragalistic";
		t2.var9 = "Supacalafragalistic";
		t2.var10 = "Supacalafragalistic";
		for(int i=1; i<n+10; i++)
		{
			t2.id = i;
			//try { mods.odb.delete(t2); } catch (Exception e) { }
			try { delete(t2); } catch (Exception e) { }
		}
		time1 = System.currentTimeMillis();
		for(int i=1; i<n+1; i++)
		{
			t2.id = i;
			//try { mods.odb.save(t2); } catch (Exception e) { }
			try { save(t2); } catch (Exception e) { }
		}
		time2 = System.currentTimeMillis();
		//try { results = mods.odb.retrieve(TestObj2.class, null); } catch ( Exception e ) { };
		try { results = retrieve(TestObj2.class, null); } catch ( Exception e ) { };
		time3 = System.currentTimeMillis();

		irc.sendContextReply(mes, "End of stage 2. Time differences: " + (time2 - time1) + ", " + (time3 - time2) + ", size is " + results.size() + ".");

		TestObj3 t3 = new TestObj3();
		t3.var1 = 9999;
		t3.var2 = 9999;
		t3.var3 = 9999;
		t3.var4 = 9999;
		t3.var5 = 9999;
		t3.var6 = 9999;
		t3.var7 = 9999;
		t3.var8 = 9999;
		t3.var9 = 9999;
		t3.var10 = 9999;
		for(int i=1; i<n+10; i++)
		{
			t3.id = i;
			//try { mods.odb.delete(t3); } catch (Exception e) { }
			try { delete(t3); } catch (Exception e) { }
		}
		time1 = System.currentTimeMillis();
		for(int i=1; i<n+1; i++)
		{
			t3.id = i;
			//try { mods.odb.save(t3); } catch (Exception e) { }
			try { save(t3); } catch (Exception e) { }
		}
		time2 = System.currentTimeMillis();
		//try { results = mods.odb.retrieve(TestObj3.class, null); } catch ( Exception e ) { };
		count = 0;
		for(int i=1; i<n+1; i++)
		{
			t3.id = i;
			//try { mods.odb.save(t3); } catch (Exception e) { }
			try { retrieve(TestObj3.class, "WHERE id = " + i); count++; } catch (Exception e) { }
		}
		//try { results = retrieve(TestObj3.class, null); } catch ( Exception e ) { };
		time3 = System.currentTimeMillis();

		irc.sendContextReply(mes, "End of stage 3. Time differences: " + (time2 - time1) + ", " + (time3 - time2) + ", size is " + count + ".");
		} } );
	} */

	// These commands attempt to create a deadlock situation and ensure it's corrently dealt with.
	// You can also get a deadlock by setting the id to 0 in all 4 locations.
	/*private Object waitObj = new Object();
	public void commandDeadLock1( final Message mes, final Modules mods, final IRCInterface irc ) throws ChoobNoSuchCallException
	{
		for(int i=1;i<3; i++)
		{
			TestObj3 obj = new TestObj3();
			obj.id = i;
			try { mods.odb.delete(obj); } catch (Exception e) { }
		}
		mods.plugin.queueCommand( "Test", "DeadLock2", mes );
		mods.odb.runTransaction( new ObjectDBTransaction() { public void run() {
			irc.sendContextReply(mes, "Setting up deadlock (1)!");
			TestObj3 obj = new TestObj3();
			obj.var1 = 1;
			obj.id = 1;
			save( obj );
			synchronized(waitObj)
			{
				try { waitObj.wait(5000); } catch ( InterruptedException e ) { }
			}
			obj.id = 2;
			save( obj );
			irc.sendContextReply(mes, "Returned alive (1) with ID " + obj.id + "!");
		} } );
	}

	public void commandDeadLock2( final Message mes, final Modules mods, final IRCInterface irc )
	{
		mods.odb.runTransaction( new ObjectDBTransaction() { public void run() {
			irc.sendContextReply(mes, "Setting up deadlock (2)!");
			TestObj3 obj = new TestObj3();
			obj.var1 = 2;
			obj.id = 2;
			save( obj );
			synchronized(waitObj)
			{
				try { waitObj.wait(3000); } catch ( InterruptedException e ) { }
				waitObj.notifyAll();
			}
			obj.id = 1;
			save( obj );
			irc.sendContextReply(mes, "Returned alive (2) with ID " + obj.id + "!");
		} } );
	}*/
}

public class TestObj1
{
	public int id;
}

public class TestObj2
{
	public int id;
	public String var1;
	public String var2;
	public String var3;
	public String var4;
	public String var5;
	public String var6;
	public String var7;
	public String var8;
	public String var9;
	public String var10;
}

public class TestObj3
{
	public int id;
	public int var1;
	public int var2;
	public int var3;
	public int var4;
	public int var5;
	public int var6;
	public int var7;
	public int var8;
	public int var9;
	public int var10;
}
