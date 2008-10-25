import java.awt.Color;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

/**
 * Random command implementations from myself. Quality not certain. Use at own risk.
 *
 * @author MFJ
 */

public class MFJ
{
	final static Map<Color, String> colours;

	private final Modules mods;
	private final IRCInterface irc;
	public MFJ(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	/**
	 * Implements JB's !dance command, with different outputs
	 */
	public String[] helpCommandDance = { "Makes the bot dance with you. If you don't want the bot to dance with you, don't use it." };
	public void commandDance(final Message con)
	{
		final int type = (int) (10 * Math.random());
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


	static
	{
		// http://www.w3schools.com/html/html_colornames.asp
		colours = new HashMap<Color, String>();
		colours.put(new Color(0xF0, 0xF8, 0xFF), "Alice Blue");
		colours.put(new Color(0xFA, 0xEB, 0xD7), "Antique White");
		colours.put(new Color(0x00, 0xFF, 0xFF), "Aqua");
		colours.put(new Color(0x7F, 0xFF, 0xD4), "Aquamarine");
		colours.put(new Color(0xF0, 0xFF, 0xFF), "Azure");
		colours.put(new Color(0xF5, 0xF5, 0xDC), "Beige");
		colours.put(new Color(0xFF, 0xE4, 0xC4), "Bisque");
		colours.put(new Color(0x00, 0x00, 0x00), "Black");
		colours.put(new Color(0xFF, 0xEB, 0xCD), "Blanched Almond");
		colours.put(new Color(0x00, 0x00, 0xFF), "Blue");
		colours.put(new Color(0x8A, 0x2B, 0xE2), "Blue Violet");
		colours.put(new Color(0xA5, 0x2A, 0x2A), "Brown");
		colours.put(new Color(0xDE, 0xB8, 0x87), "Burly Wood");
		colours.put(new Color(0x5F, 0x9E, 0xA0), "Cadet Blue");
		colours.put(new Color(0x7F, 0xFF, 0x00), "Chartreuse");
		colours.put(new Color(0xD2, 0x69, 0x1E), "Chocolate");
		colours.put(new Color(0xFF, 0x7F, 0x50), "Coral");
		colours.put(new Color(0x64, 0x95, 0xED), "Cornflower Blue");
		colours.put(new Color(0xFF, 0xF8, 0xDC), "Cornsilk");
		colours.put(new Color(0xDC, 0x14, 0x3C), "Crimson");
		colours.put(new Color(0x00, 0xFF, 0xFF), "Cyan");
		colours.put(new Color(0x00, 0x00, 0x8B), "Dark Blue");
		colours.put(new Color(0x00, 0x8B, 0x8B), "Dark Cyan");
		colours.put(new Color(0xB8, 0x86, 0x0B), "Dark Golden Rod");
		colours.put(new Color(0xA9, 0xA9, 0xA9), "Dark Gray");
		colours.put(new Color(0x00, 0x64, 0x00), "Dark Green");
		colours.put(new Color(0xBD, 0xB7, 0x6B), "Dark Khaki");
		colours.put(new Color(0x8B, 0x00, 0x8B), "Dark Magenta");
		colours.put(new Color(0x55, 0x6B, 0x2F), "Dark Olive Green");
		colours.put(new Color(0xFF, 0x8C, 0x00), "Darkorange");
		colours.put(new Color(0x99, 0x32, 0xCC), "Dark Orchid");
		colours.put(new Color(0x8B, 0x00, 0x00), "Dark Red");
		colours.put(new Color(0xE9, 0x96, 0x7A), "Dark Salmon");
		colours.put(new Color(0x8F, 0xBC, 0x8F), "Dark Sea Green");
		colours.put(new Color(0x48, 0x3D, 0x8B), "Dark Slate Blue");
		colours.put(new Color(0x2F, 0x4F, 0x4F), "Dark Slate Gray");
		colours.put(new Color(0x00, 0xCE, 0xD1), "Dark Turquoise");
		colours.put(new Color(0x94, 0x00, 0xD3), "Dark Violet");
		colours.put(new Color(0xFF, 0x14, 0x93), "Deep Pink");
		colours.put(new Color(0x00, 0xBF, 0xFF), "Deep Sky Blue");
		colours.put(new Color(0x69, 0x69, 0x69), "Dim Gray");
		colours.put(new Color(0x1E, 0x90, 0xFF), "Dodger Blue");
		colours.put(new Color(0xD1, 0x92, 0x75), "Feldspar");
		colours.put(new Color(0xB2, 0x22, 0x22), "Fire Brick");
		colours.put(new Color(0xFF, 0xFA, 0xF0), "Floral White");
		colours.put(new Color(0x22, 0x8B, 0x22), "Forest Green");
		colours.put(new Color(0xFF, 0x00, 0xFF), "Fuchsia");
		colours.put(new Color(0xDC, 0xDC, 0xDC), "Gainsboro");
		colours.put(new Color(0xF8, 0xF8, 0xFF), "Ghost White");
		colours.put(new Color(0xFF, 0xD7, 0x00), "Gold");
		colours.put(new Color(0xDA, 0xA5, 0x20), "Golden Rod");
		colours.put(new Color(0x80, 0x80, 0x80), "Gray");
		colours.put(new Color(0x00, 0x80, 0x00), "Green");
		colours.put(new Color(0xAD, 0xFF, 0x2F), "Green Yellow");
		colours.put(new Color(0xF0, 0xFF, 0xF0), "Honey Dew");
		colours.put(new Color(0xFF, 0x69, 0xB4), "Hot Pink");
		colours.put(new Color(0xCD, 0x5C, 0x5C), "Indian Red ");
		colours.put(new Color(0x4B, 0x00, 0x82), "Indigo ");
		colours.put(new Color(0xFF, 0xFF, 0xF0), "Ivory");
		colours.put(new Color(0xF0, 0xE6, 0x8C), "Khaki");
		colours.put(new Color(0xE6, 0xE6, 0xFA), "Lavender");
		colours.put(new Color(0xFF, 0xF0, 0xF5), "Lavender Blush");
		colours.put(new Color(0x7C, 0xFC, 0x00), "Lawn Green");
		colours.put(new Color(0xFF, 0xFA, 0xCD), "Lemon Chiffon");
		colours.put(new Color(0xAD, 0xD8, 0xE6), "Light Blue");
		colours.put(new Color(0xF0, 0x80, 0x80), "Light Coral");
		colours.put(new Color(0xE0, 0xFF, 0xFF), "Light Cyan");
		colours.put(new Color(0xFA, 0xFA, 0xD2), "Light Golden Rod Yellow");
		colours.put(new Color(0xD3, 0xD3, 0xD3), "Light Grey");
		colours.put(new Color(0x90, 0xEE, 0x90), "Light Green");
		colours.put(new Color(0xFF, 0xB6, 0xC1), "Light Pink");
		colours.put(new Color(0xFF, 0xA0, 0x7A), "Light Salmon");
		colours.put(new Color(0x20, 0xB2, 0xAA), "Light Sea Green");
		colours.put(new Color(0x87, 0xCE, 0xFA), "Light Sky Blue");
		colours.put(new Color(0x84, 0x70, 0xFF), "Light Slate Blue");
		colours.put(new Color(0x77, 0x88, 0x99), "Light Slate Gray");
		colours.put(new Color(0xB0, 0xC4, 0xDE), "Light Steel Blue");
		colours.put(new Color(0xFF, 0xFF, 0xE0), "Light Yellow");
		colours.put(new Color(0x00, 0xFF, 0x00), "Lime");
		colours.put(new Color(0x32, 0xCD, 0x32), "Lime Green");
		colours.put(new Color(0xFA, 0xF0, 0xE6), "Linen");
		colours.put(new Color(0xFF, 0x00, 0xFF), "Magenta");
		colours.put(new Color(0x80, 0x00, 0x00), "Maroon");
		colours.put(new Color(0x66, 0xCD, 0xAA), "Medium Aqua Marine");
		colours.put(new Color(0x00, 0x00, 0xCD), "Medium Blue");
		colours.put(new Color(0xBA, 0x55, 0xD3), "Medium Orchid");
		colours.put(new Color(0x93, 0x70, 0xD8), "Medium Purple");
		colours.put(new Color(0x3C, 0xB3, 0x71), "Medium Sea Green");
		colours.put(new Color(0x7B, 0x68, 0xEE), "Medium Slate Blue");
		colours.put(new Color(0x00, 0xFA, 0x9A), "Medium Spring Green");
		colours.put(new Color(0x48, 0xD1, 0xCC), "Medium Turquoise");
		colours.put(new Color(0xC7, 0x15, 0x85), "Medium Violet Red");
		colours.put(new Color(0x19, 0x19, 0x70), "Midnight Blue");
		colours.put(new Color(0xF5, 0xFF, 0xFA), "Mint Cream");
		colours.put(new Color(0xFF, 0xE4, 0xE1), "Misty Rose");
		colours.put(new Color(0xFF, 0xE4, 0xB5), "Moccasin");
		colours.put(new Color(0xFF, 0xDE, 0xAD), "Navajo White");
		colours.put(new Color(0x00, 0x00, 0x80), "Navy");
		colours.put(new Color(0xFD, 0xF5, 0xE6), "Old Lace");
		colours.put(new Color(0x80, 0x80, 0x00), "Olive");
		colours.put(new Color(0x6B, 0x8E, 0x23), "Olive Drab");
		colours.put(new Color(0xFF, 0xA5, 0x00), "Orange");
		colours.put(new Color(0xFF, 0x45, 0x00), "Orange Red");
		colours.put(new Color(0xDA, 0x70, 0xD6), "Orchid");
		colours.put(new Color(0xEE, 0xE8, 0xAA), "Pale Golden Rod");
		colours.put(new Color(0x98, 0xFB, 0x98), "Pale Green");
		colours.put(new Color(0xAF, 0xEE, 0xEE), "Pale Turquoise");
		colours.put(new Color(0xD8, 0x70, 0x93), "Pale Violet Red");
		colours.put(new Color(0xFF, 0xEF, 0xD5), "Papaya Whip");
		colours.put(new Color(0xFF, 0xDA, 0xB9), "Peach Puff");
		colours.put(new Color(0xCD, 0x85, 0x3F), "Peru");
		colours.put(new Color(0xFF, 0xC0, 0xCB), "Pink");
		colours.put(new Color(0xDD, 0xA0, 0xDD), "Plum");
		colours.put(new Color(0xB0, 0xE0, 0xE6), "Powder Blue");
		colours.put(new Color(0x80, 0x00, 0x80), "Purple");
		colours.put(new Color(0xFF, 0x00, 0x00), "Red");
		colours.put(new Color(0xBC, 0x8F, 0x8F), "Rosy Brown");
		colours.put(new Color(0x41, 0x69, 0xE1), "Royal Blue");
		colours.put(new Color(0x8B, 0x45, 0x13), "Saddle Brown");
		colours.put(new Color(0xFA, 0x80, 0x72), "Salmon");
		colours.put(new Color(0xF4, 0xA4, 0x60), "Sandy Brown");
		colours.put(new Color(0x2E, 0x8B, 0x57), "Sea Green");
		colours.put(new Color(0xFF, 0xF5, 0xEE), "Sea Shell");
		colours.put(new Color(0xA0, 0x52, 0x2D), "Sienna");
		colours.put(new Color(0xC0, 0xC0, 0xC0), "Silver");
		colours.put(new Color(0x87, 0xCE, 0xEB), "Sky Blue");
		colours.put(new Color(0x6A, 0x5A, 0xCD), "Slate Blue");
		colours.put(new Color(0x70, 0x80, 0x90), "Slate Gray");
		colours.put(new Color(0xFF, 0xFA, 0xFA), "Snow");
		colours.put(new Color(0x00, 0xFF, 0x7F), "Spring Green");
		colours.put(new Color(0x46, 0x82, 0xB4), "Steel Blue");
		colours.put(new Color(0xD2, 0xB4, 0x8C), "Tan");
		colours.put(new Color(0x00, 0x80, 0x80), "Teal");
		colours.put(new Color(0xD8, 0xBF, 0xD8), "Thistle");
		colours.put(new Color(0xFF, 0x63, 0x47), "Tomato");
		colours.put(new Color(0x40, 0xE0, 0xD0), "Turquoise");
		colours.put(new Color(0xEE, 0x82, 0xEE), "Violet");
		colours.put(new Color(0xD0, 0x20, 0x90), "Violet Red");
		colours.put(new Color(0xF5, 0xDE, 0xB3), "Wheat");
		colours.put(new Color(0xFF, 0xFF, 0xFF), "White");
		colours.put(new Color(0xF5, 0xF5, 0xF5), "White Smoke");
		colours.put(new Color(0xFF, 0xFF, 0x00), "Yellow");
		colours.put(new Color(0x9A, 0xCD, 0x32), "Yellow Green");
	}


	public static String colourForToday()
	{
		final GregorianCalendar cal = new GregorianCalendar();
		final int type = (cal.get(Calendar.DAY_OF_MONTH) + 31 * cal.get(Calendar.MONTH) + 12 * cal.get(Calendar.YEAR)) % colours.size();

		final String[] col = colours.values().toArray(new String[] {});

		return col[type];
	}


	private void /*inline*/ invalidArgument(final Message mes)
	{
		irc.sendContextMessage(mes, "Invalid argument, expecting: [#]rrggbb, rgb(255,255,255) or a named colour.");
	}

	// http://mindprod.com/jgloss/hex.html
	public static String /*inline*/ byteToHex(final int b)
	{
		return Integer.toString( ( b & 0xff ) + 0x100, 16 /* radix */ ).substring( 1 );
	}


	/**
	 * Implements JB's !colour command
	 */
	public String[] helpCommandColour = { "Lets you know what today's colour is. Optionally, given a css colour, it will attempt to guess what it looks like." };
	public void commandColour(final Message con)
	{
		String parm = mods.util.getParamString(con);
		if (parm.length() < 3)
		{
			irc.sendContextMessage( con, "Today's colour is " + colourForToday() + ".");
			return;
		}

		for (final Color c : colours.keySet())
			if (colours.get(c).equalsIgnoreCase(parm))
			{
				irc.sendContextReply(con, "'" + parm + "' is #" + byteToHex(c.getRed()) + byteToHex(c.getGreen()) + byteToHex(c.getBlue()) + ".");
				return;
			}

		Color toFind;
		final String number = "((?:2[0-5][0-9])|(?:1?[0-9]?[0-9]))";
		//final String number = "([0-9]+)";
		final Matcher ma = Pattern.compile("\\s*rgb\\s*\\(\\s*" + number + ",\\s*" + number + ",\\s*" + number + "\\s*\\)\\s*").matcher(parm);
		//Matcher ma = Pattern.compile("rgb\\(([0-9]+),([0-9]+),([0-9]+)\\)").matcher(parm);

		try
		{
			if (ma.find())
			{
				System.out.println("Matches!");
				toFind = new Color(Integer.parseInt(ma.group(1)), Integer.parseInt(ma.group(2)), Integer.parseInt(ma.group(3)));
			}
			else
			{
				if (parm.length() == 7 || parm.length() == 4)
					parm = parm.substring(1);

				// rst -> rrsstt
				if (parm.length() == 3)
					parm = parm.substring(0,1) + parm.substring(0,1) +
						   parm.substring(1,2) + parm.substring(1,2) +
						   parm.substring(2,3) + parm.substring(2,3);

				if (parm.length() != 6)
				{
					invalidArgument(con);
					return;
				}


				toFind = new Color(	Integer.parseInt(parm.substring(0, 1), 16) * 16 + Integer.parseInt(parm.substring(1, 2), 16),
									Integer.parseInt(parm.substring(2, 3), 16) * 16 + Integer.parseInt(parm.substring(3, 4), 16),
									Integer.parseInt(parm.substring(4, 5), 16) * 16 + Integer.parseInt(parm.substring(5, 6), 16)
								);
			}
		}
		catch (final NumberFormatException e)
		{
			e.printStackTrace();
			invalidArgument(con);
			return;
		}
		catch (final IllegalArgumentException e)
		{
			invalidArgument(con);
			return;
		}


		final float[] rgbtarget = new float[] { toFind.getRed()/255.0f, toFind.getGreen()/255.0f, toFind.getBlue()/255.0f };

		double bestMatch = 500;
		Color closest = null;
		for (final Color c : colours.keySet())
		{
			final double match = Math.pow(c.getRed()/255.0f - rgbtarget[0], 2) +
						  Math.pow(c.getGreen()/255.0f - rgbtarget[1], 2) +
						  Math.pow(c.getBlue()/255.0f - rgbtarget[2], 2);

			if (match < bestMatch)
			{
				bestMatch = match;
				closest = c;
			}
		}

		if (closest != null)
			irc.sendContextReply(con, "That looks " + (bestMatch != 0 ? "roughly (" + Math.round((1-bestMatch)*100000.0)/1000.0 + "%)" : "exactly") + " like " + colours.get(closest) + " to me.");
		else
			irc.sendContextReply(con, "No match.");
	}

	/**
	 * Implement JB's !year command
	 */
	public String[] helpCommandYear = { "Outputs the current year." };
	public void commandYear(final Message con)
	{
		final GregorianCalendar cal = new GregorianCalendar();

		irc.sendContextMessage( con, "It is the year " + cal.get(Calendar.YEAR) + ".");
	}

	/**
	 * Implement JB's !month command
	 */
	public String[] helpCommandMonth = { "Outputs the current month." };
	public void commandMonth(final Message con)
	{
		final GregorianCalendar cal = new GregorianCalendar();
		final DateFormatSymbols dfc = new DateFormatSymbols();

		final String[] months = dfc.getMonths();

		irc.sendContextMessage( con, "It is " + months[cal.get(Calendar.MONTH)] + ".");
	}

	/**
	 * Implement JB's !day command
	 */
	public String[] helpCommandDay = { "Outputs the current day." };
	public void commandDay(final Message con)
	{
		final GregorianCalendar cal = new GregorianCalendar();
		final DateFormatSymbols dfc = new DateFormatSymbols();

		final String[] days = dfc.getWeekdays();

		irc.sendContextMessage( con, "It is " + days[cal.get(Calendar.DAY_OF_WEEK)] + ".");
	}
}
