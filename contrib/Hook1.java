import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;

class Hook1
{
	public void apiHooker( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextMessage(con, modules.util.getParamString(con));
	}
}
