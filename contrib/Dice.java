import java.util.Arrays;
import java.util.LinkedList;

/**
 * Dice plugin rolls dice!
 * 
 * 
 * @author agaeki/azurit
 */

public class CopyOfMFJ {

	/*-------------------------------
	  Rolls "xdy + z" exploding dice or not
	  ------------------------------*/

	public static LinkedList<Integer> roll(String dice, int explode) {
		String[] numbers = dice.split("d");
		LinkedList<Integer> values = new LinkedList<Integer>();
		int curRoll;
		int die;
		int keep;
		if (numbers[1].contains("k")) keep = Integer.parseInt(numbers[1].split("k")[1]);
		else keep = Integer.parseInt(numbers[1]);
		
		//if dice is to be taken away, set die to be rolled as -ve
		if (!numbers[0].contains("-")) {
			die = Integer.parseInt(numbers[1]);
		} else {
			die = Integer.parseInt(numbers[1]) * - 1;
		}

		// Checks if it should try to explode d1 dice
		if (numbers[1] == "1" && explode != 0) {
			explode = 0;
		}

		// Roll while exploding until counter gets to zero
		for (int counter = Math.abs(Integer.parseInt(numbers[0])); counter > 0; counter--) {
			curRoll = rollOne(die);
			if (curRoll == die && explode == 1) {
				values.add(new Integer(curRoll));
				values.addAll(roll("1d"+numbers[1], 1));
			} else if (curRoll == 1 && explode == -1) {
				values.addAll(roll("1d"+numbers[1], -1));
			} else {
				values.add(new Integer(curRoll));
			}
		}
		
		if (keep < Integer.parseInt(numbers[0])) {
			Object[] temp = values.toArray();
			Arrays.sort(temp);
			values.clear();
			for (int a = keep; a > 0; a--) {
				values.add((Integer) temp[a]);
			}
		}
		
		return values;
	}

	private static int rollOne(int die) {
		int value = (int) Math.ceil(Math.random() * die);
		if (value == 0)
			value = 1;
		return value;
	}

	private static LinkedList<Integer> bonus(int bonus) {
		LinkedList<Integer> out = new LinkedList<Integer>();
		out.add((Integer) bonus); // Add the bonus as the first roll result
		return out;
	}
	
	public String[] helpCommandRoll = { "Rolls x y-sided dice with the option of exploding dice, using syntax <x>d<y>+<z> <yes/no exploding dice>. Can roll more than one die size at a time (i.e 1d6+1d8)" };
	public static String commandRoll(String message) {
		message.replace("-", "+-");
		String[] argument = message.split(" ");
		int total = 0;
		String out = "I rolled: ";
		String[] rolls;
		LinkedList<Integer> dice = new LinkedList<Integer>();
		int expl = argument.length > 1 && argument[1].compareTo("yes") == 0 ? 1
				: 0;
		int neg = 1;
		// Check and transfer all dice specs to be rolled into array "rolls"
		rolls = argument[0].split("+");
		// roll the entire array "rolls"
		for (int h = 0; h < rolls.length; h++) {
			// Check if the argument is dice or flat bonus
			if (rolls[h].contains("d"))
				dice.addAll(roll(rolls[h], expl));
			else
				dice = bonus(Integer.parseInt(rolls[h])*neg);

			// pop all elements of dice into a string while gathering the
			// total
			while (!dice.isEmpty()) {
				int temp = (int) dice.poll();
				total += temp;
				out = out.concat(String.valueOf(temp));
				out = out.concat(" + ");
			}
			out = out.concat(" + ");
		}
		// modify the output string: remove the latest 3 chars (" + "), add
		// " = " and the total
		out = out.substring(0, out.length() - 2);
		out = out.concat("= " + String.valueOf(total));

		return out;
	}


	public String[] helpCommandStats = { "rolls 6 groups of dice (for D&D-style attributes); xdyku+wdz+... <yes/no reroll 1s> where u is an optional number for the number of dice to keep. (i.e. 4d6k3 will roll 4 6-sided dice and use the highest three as the total." };
	public static String commandStats(String message) {
		String[] argument = message.split(" ");
		int[] stats = new int[6];
		int reroll = argument.length > 2 && argument[2].compareTo("yes") == 0 ? -1 : 0;
		String[] rolls;
		int buyout = 0;
		String out;
		int temptotal, temproll = 0;
		LinkedList<Integer> dice;

		argument[0].replace("-", "+-");
		if (argument[0].contains("+")) {
			rolls = argument[0].split("\\+");
		} else {
			rolls = new String[1];
			rolls[0] = argument[0];
		}
		// roll the entire array "rolls"
		for (int a = 0; a < 6; a++) {
			out = "Rolled ";
			temptotal = 0;
			for (int h = 0; h < rolls.length; h++) {
				// Check if the argument is dice or flat bonus
				if (rolls[h].contains("d")) {
					dice = roll(rolls[h], reroll);
				} else {
					dice = bonus(Integer.parseInt(rolls[h]));
				}
				// pop all elements of dice into a string while gathering
				// the total
				while (!dice.isEmpty()) {
					temproll = (int) dice.poll();
					out = out.concat(String.valueOf(temproll));
					out = out.concat(" + ");
				}
				out = out.substring(0, out.length() - 2);
				out = out.concat("= " + String.valueOf(temptotal));
				// Calculate the effective point-buy of the stats (assuming D&D3.5)
				if (temptotal < 14)
					buyout += temptotal - 8;
				else if (temptotal < 17)
					buyout += (temptotal - 11) * 2;
				else if (temptotal == 17)
					buyout += 6;
				else if (temptotal == 18)
					buyout += 10;
				else if (temptotal > 18)
					buyout += 15;
			}
			stats[a] = temptotal;
		}
		Arrays.sort(stats);
		out = "Your stats are ";
		for (int a = 0; a < 6; a++)
			out = out + stats[a] + " ";
		out = out.concat("for a total buyout of "+ buyout);
		return out;
		}

	public String[] helpCommandFullAttack = { "Rolls x dice, each time the bonus decreasing by 5 starting at y; syntax <x> <y>" };
	public String commandFullAttack(String message) {
		String[] argument = message.split(" ");
		try {
		LinkedList<Integer> dice = roll(argument[0] + "d20", 0);
		int mod = Integer.parseInt(argument[1]);
		int temproll;
		String out = "Rolled ";
		String out2 = "Attacks: ";
		while (!dice.isEmpty()) {
			temproll = (int) dice.poll();
			out = out.concat(temproll + ", ");
			temproll += mod;
			if (temproll > 0) {
				out2 = out2.concat(temproll + ", ");
			}
			mod -= 5;
		}
		out = out.substring(0, out.length() - 2);
		out2 = out2.substring(0, out2.length() - 2);
		out += "  ";
		out = out.concat(out2);
		return out;
		}
		catch(Throwable e) {
			return "Don't be a cocker, do it right.";
		}
	}

}
