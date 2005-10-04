import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;

public class Hook1
{
	public void apiHooker( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextReply(con, modules.util.getParamString(con));
	}
}
