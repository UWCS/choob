import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;

public class MFJ
{
	public void commandDance(Message con, Modules mods, IRCInterface irc)
	{
		int type = (int) (10 * Math.random());		
		String dance;
		
		switch (type)
		{
			case 0: dance = " waltzes to Beautiful Ohio!"; 			break;
			case 1: dance = " tangos to Amigo Cavano!";			break;
			case 2: dance = " dances the Samba away!";			break;
			case 3: dance = " swings gracefully to Billie Holiday.";	break;
			case 4: dance = " jives to the Summertime blues.";		break;
			case 5: dance = " does the Cha Cha!";				break;
			case 6: dance = " dances the Flamenco all night!";		break;
			case 7: dance = " steps into a Vienese Waltz.";			break;
			case 8: dance = " gets on down to The Monkees.";		break;
			case 9: dance = " BADGER BADGER BADGER .... MUSHROOM MUSHROOM!";break;
			default: dance = " carts them off to the police station.";	break;
		}
		
		irc.sendContextAction( con, "grabs " + con.getNick() + " and" + dance );
	}
}
