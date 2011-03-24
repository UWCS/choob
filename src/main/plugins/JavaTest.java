import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

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

	private final Modules mods;
	private final IRCInterface irc;
	private String text;

	public JavaTest(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public void commandSecurityStackTest(final Message mes)
	{
		mods.security.getPluginNames("JavaTest:commandSecurityStackTest");
		try {
			mods.plugin.callAPI("JSExample", "callapi", "JavaTest", "APIResult", mes, mods.util.getParamString(mes) + "[api]");
		} catch(final ChoobNoSuchCallException e) {
			irc.sendContextReply(mes, "Error: " + e);
		}
	}

	public void apiAPIResult(final Message mes, final String text_)
	{
		mods.security.getPluginNames("JavaTest:apiAPIResult");
		irc.sendContextReply(mes, "JavaTest result: " + text_);

		this.text = text_ + "[interval]";
		mods.interval.callBack(mes, 1000, 1);
	}

	public void apiAPIResult2(final Message mes, final String text_)
	{
		mods.security.getPluginNames("JavaTest:apiAPIResult2");
		irc.sendContextReply(mes, "JavaTest result: " + text_);

		try {
			mods.plugin.callAPI("JSExample", "callapi", "JavaTest", "APIResult3", mes, text_ + "[api]");
		} catch(final ChoobNoSuchCallException e) {
			irc.sendContextReply(mes, "Error: " + e);
		}
	}

	public void apiAPIResult3(final Message mes, final String text_)
	{
		mods.security.getPluginNames("JavaTest:apiAPIResult3");
		irc.sendContextReply(mes, "JavaTest result: " + text_);
	}

	public void interval(final Object param)
	{
		mods.security.getPluginNames("JavaTest:interval");
		try {
			mods.plugin.callAPI("JSExample", "callapi", "JavaTest", "APIResult2", param, text + "[api]");
		} catch(final ChoobNoSuchCallException e) {
			irc.sendContextReply((Message)param, "Error: " + e);
		}
		text = null;
	}
}
