import org.uwcs.choob.*;
import org.uwcs.choob.modules.*;
import org.uwcs.choob.support.*;
import org.uwcs.choob.support.events.*;

//Calendar support
import java.util.GregorianCalendar;
import java.text.DateFormatSymbols;

/**
 * Random command implementations from myself. Quality not certain. Use at own risk.
 * 
 * @author MFJ
 */

public class MFJ
{
	public String filterBeardRegex = ".*beard.*";

	public void filterBeard( Message con, Modules modules, IRCInterface irc )
	{
		irc.sendContextAction( con, "points at " + con.getNick() + "'s beard.");
	}
	
	/**
	 * Implements JB's !dance command, with different outputs
	 */
	public void commandDance(Message con, Modules mods, IRCInterface irc)
	{
		int type = (int) (10 * Math.random());		
		String dance;
		
		switch (type)
		{
			case 0: dance = " waltzes to Beautiful Ohio!"; 					break;
			case 1: dance = " tangos to Amigo Cavano!";						break;
			case 2: dance = " dances the Samba away!";						break;
			case 3: dance = " swings gracefully to Billie Holiday.";		break;
			case 4: dance = " jives to the Summertime blues.";				break;
			case 5: dance = " does the Cha Cha!";							break;
			case 6: dance = " dances the Flamenco all night!";				break;
			case 7: dance = " steps into a Vienese Waltz.";					break;
			case 8: dance = " gets on down to The Monkees.";				break;
			case 9: dance = " BADGER BADGER BADGER .... MUSHROOM MUSHROOM!";break;
			default: dance = " carts them off to the police station.";		break;
		}
		
		irc.sendContextAction( con, "grabs " + con.getNick() + " and" + dance );
	}
	
	/**
	 * Implements JB's !colour command
	 */
	public void commandColour(Message con, Modules mods, IRCInterface irc)
	{
		GregorianCalendar cal = new GregorianCalendar();
		
		int type = cal.get(cal.DAY_OF_MONTH);
		String colour;
		
		switch (type)
		{
			case 1: colour = "Slate Blue";		break;
			case 2: colour = "Indian Red";		break; 	 
			case 3: colour = "Pale Turquoise";	break;	 
			case 4: colour = "Cornsilk";		break;	 
			case 5: colour = "Spring Green";	break;	 
			case 6: colour = "Burly Wood";		break;	 
			case 7: colour = "Lime Green";		break; 
			case 8: colour = "Linen";			break;	 
			case 9: colour = "Magenta";			break;	 
			case 10: colour = "Maroon";			break;	 
			case 11: colour = "Aqua Marine";	break; 	 	 
			case 12: colour = "Salmon";			break;	 
			case 13: colour = "Saddle Brown";	break;	 
			case 14: colour = "Midnight Blue";	break;	 
			case 15: colour = "Feldspar";		break;	 
			case 16: colour = "Misty Rose";		break; 	 
			case 17: colour = "Moccasin";		break; 	 
			case 18: colour = "Navajo White";	break;	 
			case 19: colour = "Navy";			break;	 
			case 20: colour = "Old Lace";		break; 	 
			case 21: colour = "Olive";			break; 	 
			case 22: colour = "Golden Rod";		break; 	 
			case 23: colour = "Orange";			break; 	 
			case 24: colour = "White Smoke";	break; 	 
			case 25: colour = "Orchid";			break; 	 
			case 26: colour = "Tomato";			break; 	 
			case 27: colour = "Snow";			break; 	 
			case 28: colour = "lemon Chiffon";	break; 	 
			case 29: colour = "Pale Violet Red";break; 	 
			case 30: colour = "Mint Cream";		break; 	 
			case 31: colour = "PeachPuff";		break; 	 
			default: colour = "Black"; 			break;
		}
		
		irc.sendContextMessage( con, "Today's colour is " + colour + ".");
	}
	
	/**
	 * Americans--
	 */
	public void commandColor(Message con, Modules mods, IRCInterface irc)
	{
		commandColour(con, mods, irc);
	}
	
	/**
	 * Implement JB's !year command
	 */
	public void commandYear(Message con, Modules mods, IRCInterface irc)
	{
		GregorianCalendar cal = new GregorianCalendar();
		
		irc.sendContextMessage( con, "It is the year " + cal.get(cal.YEAR) + ".");
	}
	
	/**
	 * Implement JB's !month command
	 */
	public void commandMonth(Message con, Modules mods, IRCInterface irc)
	{
		GregorianCalendar cal = new GregorianCalendar();
		DateFormatSymbols dfc = new DateFormatSymbols();
		
		String[] months = dfc.getMonths();
				
		irc.sendContextMessage( con, "It is " + months[cal.get(cal.MONTH)] + ".");
	}
	
	/**
	 * Implement JB's !day command
	 */
	public void commandDay(Message con, Modules mods, IRCInterface irc)
	{
		GregorianCalendar cal = new GregorianCalendar();
		DateFormatSymbols dfc = new DateFormatSymbols();
		
		String[] days = dfc.getWeekdays();
				
		irc.sendContextMessage( con, "It is " + days[cal.get(cal.DAY_OF_WEEK)] + ".");
	}
}
