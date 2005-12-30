/** @author Faux */

import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;

public class HelloWorld
{
	public void commandOne( Message mes )
	{
		irc.sendContextReply(mes, "Hello world!");
	}
}
