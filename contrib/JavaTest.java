import uk.co.uwcs.choob.*;
import uk.co.uwcs.choob.modules.*;
import uk.co.uwcs.choob.support.*;
import uk.co.uwcs.choob.event.*
import java.util.regex.*;
import java.util.*;

public class JavaTest
{
	public String[] info()
	{
		return new String[] {
			"Java Test Plugin",
			"James Ross",
			"silver@warwickcompsoc.co.uk",
			"--none--"
		};
	}
	
	private Modules mods;
	private IRCInterface irc;
	private String text;
	
	public JavaTest(Modules mods, IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}
	
	public void commandSecurityStackTest(Message mes)
	{
		mods.security.getPluginNames("JavaTest:commandSecurityStackTest");
		try {
			mods.plugin.callAPI("JSExample", "callapi", "JavaTest", "APIResult", mes, mods.util.getParamString(mes) + "[api]");
		} catch(ChoobNoSuchCallException e) {
			irc.sendContextReply(mes, "Error: " + e);
		}
	}
	
	public void apiAPIResult(Message mes, String text)
	{
		mods.security.getPluginNames("JavaTest:apiAPIResult");
		irc.sendContextReply(mes, "JavaTest result: " + text);
		
		this.text = text + "[interval]";
		mods.interval.callBack(mes, 1000, 1);
	}
	
	public void apiAPIResult2(Message mes, String text)
	{
		mods.security.getPluginNames("JavaTest:apiAPIResult2");
		irc.sendContextReply(mes, "JavaTest result: " + text);
		
		try {
			mods.plugin.callAPI("JSExample", "callapi", "JavaTest", "APIResult3", mes, text + "[api]");
		} catch(ChoobNoSuchCallException e) {
			irc.sendContextReply(mes, "Error: " + e);
		}
	}
	
	public void apiAPIResult3(Message mes, String text)
	{
		mods.security.getPluginNames("JavaTest:apiAPIResult3");
		irc.sendContextReply(mes, "JavaTest result: " + text);
	}
	
	public void interval(Object param)
	{
		mods.security.getPluginNames("JavaTest:interval");
		try {
			mods.plugin.callAPI("JSExample", "callapi", "JavaTest", "APIResult2", (Message)param, text + "[api]");
		} catch(ChoobNoSuchCallException e) {
			irc.sendContextReply((Message)param, "Error: " + e);
		}
		text = null;
	}
}
