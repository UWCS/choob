import java.util.HashMap;

import org.jibble.pircbot.Colors;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

/**
 * @author Chris Hawley
 */
public class Conversions {

	public String[] info() {
		return new String[] { "Conversions plugin.", "The Choob Team",
				"choob@uwcs.co.uk", "$Rev$$Date$" };
	}

	enum conversionTypes {
		LENGTH, VOLUME, MASS, VELOCITY, TEMPERATURE
	};

	// TODO: Having all of this data just hard coded is dirty.
	private static final HashMap<String, conversionTypes> typeMapping = new HashMap<String, conversionTypes>() {
		/**
		 * 
		 */
		private static final long serialVersionUID = -8050801112451734011L;

		{
			// Metric lengths
			put("millimetre", conversionTypes.LENGTH);
			put("centimetre", conversionTypes.LENGTH);
			put("metre", conversionTypes.LENGTH);
			put("kilometre", conversionTypes.LENGTH);

			// Imperial lengths
			put("inch", conversionTypes.LENGTH);
			put("foot", conversionTypes.LENGTH);
			put("yard", conversionTypes.LENGTH);
			put("fathom", conversionTypes.LENGTH);
			put("mile", conversionTypes.LENGTH);
			put("nauticalmile", conversionTypes.LENGTH);

			// Metric volumes
			put("millilitre", conversionTypes.VOLUME);
			put("litre", conversionTypes.VOLUME);

			// Imperial volumes
			put("fluidounce", conversionTypes.VOLUME);
			put("pint", conversionTypes.VOLUME);
			put("gallon", conversionTypes.VOLUME);

			// US volumes
			put("usfluidounce", conversionTypes.VOLUME);
			put("uspint", conversionTypes.VOLUME);
			put("usgallon", conversionTypes.VOLUME);

			// Metric masses
			put("gram", conversionTypes.MASS);
			put("kilogram", conversionTypes.MASS);
			put("tonne", conversionTypes.MASS);

			// Imperial masses
			put("ounce", conversionTypes.MASS);
			put("pound", conversionTypes.MASS);
			put("stone", conversionTypes.MASS);
			put("hundredweight", conversionTypes.MASS);
			put("ton", conversionTypes.MASS);

			// Metric velocities
			put("metrespersecond", conversionTypes.VELOCITY);
			put("kilometresperhour", conversionTypes.VELOCITY);

			// Imperial velocities
			put("inchesperminute", conversionTypes.VELOCITY);
			put("feetperminute", conversionTypes.VELOCITY);
			put("feetpersecond", conversionTypes.VELOCITY);
			put("milesperhour", conversionTypes.VELOCITY);
			put("knot", conversionTypes.VELOCITY);

			// Temperatures
			put("celsius", conversionTypes.TEMPERATURE);
			put("kelvin", conversionTypes.TEMPERATURE);
			put("fahrenheit", conversionTypes.TEMPERATURE);

		}
	};

	private static final HashMap<String, String> typeAliases = new HashMap<String, String>() {
		/**
		 * 
		 */
		private static final long serialVersionUID = 8978542459094632598L;

		{
			// Metric lengths
			put("mm", "millimetre");
			put("millimetres", "millimetre");
			put("cm", "centimetre");
			put("centimetres", "centimetre");
			put("m", "metre");
			put("metres", "metre");
			put("km", "kilometre");
			put("kilometres", "kilometre");

			// Metric lengths - US Spelling
			put("millimeter", "millimetre");
			put("millimeters", "millimetre");
			put("centimeter", "centimetre");
			put("centimeters", "centimetre");
			put("meter", "metre");
			put("meters", "metres");
			put("kilometer", "kilometre");
			put("kilometers", "kilometre");

			// Imperial lengths
			put("in", "inch");
			put("inches", "inch");
			put("ft", "foot");
			put("feet", "foot");
			put("yd", "yard");
			put("yds", "yard");
			put("yards", "yard");
			put("fathoms", "fathom");
			put("miles", "mile");
			put("nauticalmiles", "nauticalmile");

			// Metric volumes
			put("ml", "millilitre");
			put("millilitres", "millilitre");
			put("cm3", "millilitre");
			put("cm^3", "millilitre");
			put("l", "litre");
			put("litres", "litre");
			put("dm3", "litre");
			put("dm^3", "litre");

			// / Metric volumes - US Spellings
			put("milliliter", "millilitre");
			put("milliliters", "millilitre");
			put("liter", "litre");
			put("liters", "litre");

			// Imperial volumes
			put("floz", "fluidounce");
			put("fluidounces", "fluidounce");
			put("pt", "pint");
			put("pints", "pint");
			put("gal", "gallon");
			put("gallons", "gallon");

			// US volumes
			put("usfloz", "usfluidounce");
			put("usfluidounces", "usfluidounce");
			put("uspt", "uspint");
			put("uspints", "uspint");
			put("usgal", "usgallon");
			put("usgallons", "usgallon");

			// Metric masses
			put("g", "gram");
			put("grams", "gram");
			put("gramme", "gram");
			put("grammes", "gram");
			put("kg", "kilogram");
			put("kilograms", "kilogram");
			put("tonnes", "tonne");

			// Imperial masses
			put("oz", "ounce");
			put("ounces", "ounce");
			put("lb", "pound");
			put("pounds", "pound");
			put("stones", "stone");
			put("cwt", "hundredweight");
			put("hundredweights", "hundredweight");
			put("tons", "ton");

			// Metric velocities
			put("m/s", "metrespersecond");
			put("kph", "kilometresperhour");
			put("k/h", "kilometresperhour");

			// Imperial velocities
			put("in/min", "inchesperminute");
			put("ft/min", "feetperminute");
			put("ft/sec", "feetpersecond");
			put("ft/s", "feetpersecond");
			put("fps", "feetpersecond");
			put("mph", "milesperhour");
			put("knots", "knot");

			// Temperatures
			put("c", "celsius");
			put("f", "fahrenheit");
			put("k", "kelvin");
		}
	};

	private static final HashMap<String, Double> lengthsInMetres = new HashMap<String, Double>() {
		/**
		 * 
		 */
		private static final long serialVersionUID = -2368330220527173449L;

		{
			put("millimetre", 0.001);
			put("centimetre", 0.01);
			put("metre", 1.0);
			put("kilometre", 1000.0);

			put("inch", 0.0254);
			put("foot", 0.3048);
			put("yard", 0.9144);
			put("fathom", 1.8288);
			put("mile", 1609.0);
			put("nauticalmile", 1852.0);
		}
	};

	private static final HashMap<String, Double> volumesInLitres = new HashMap<String, Double>() {
		/**
		 * 
		 */
		private static final long serialVersionUID = 2449249239342568114L;

		{
			put("millilitre", 0.001);
			put("litre", 1.0);

			put("fluidounce", 0.0284131);
			put("pint", 0.568261);
			put("gallon", 4.54609);

			put("usfluidounce", 0.0295735);
			put("uspint", 0.473176);
			put("usgallon", 3.78541);
		}
	};

	private static final HashMap<String, Double> massesInKilograms = new HashMap<String, Double>() {
		/**
		 * 
		 */
		private static final long serialVersionUID = -3137899007763138868L;

		{
			put("gram", 0.001);
			put("kilogram", 1.0);
			put("tonne", 1000.0);

			put("ounce", 0.0283459);
			put("pound", 0.45359237);
			put("stone", 6.35029318);
			put("hundredweight", 50.8023);
			put("ton", 1016.05);
		}
	};

	private static final HashMap<String, Double> velocitiesInMetresPerSecond = new HashMap<String, Double>() {
		/**
		 * 
		 */
		private static final long serialVersionUID = 206156628830835986L;

		{
			put("metrespersecond", 1.0);
			put("kilometresperhour", 0.277777778);

			put("inchesperminute", 0.00042333);
			put("feetperminute", 0.00508);
			put("feetpersecond", 0.3048);
			put("milesperhour", 0.44704);
			put("knot", 0.514444444);
		}
	};

	public String[] helpAvailableConversions = {
			"The following units are available to convert between:",
			"Length: millimetre, centimetre, metre, kilometre, inch, foot, yard, mile, nauticalmile",
			"Volume: millilitre, litre, fluidounce, pint, gallon, usfluidounce, uspint, usgallon",
			"Mass: gram, kilogram, tonne, ounce, pound, stone, hundredweight, ton",
			"Velocity: metrespersecond, kilometresperhour, inchesperminute, feetperminute, feetpersecond, milesperhour, knot",
			"Temperature: celsius, kelvin, fahrenheit" };

	public String[] helpCommandConvert = {
			"Convert between two units. (For a list of supported units see Conversions.AvailableConversions)",
			"<from> <to> [quantity]", "<from> Unit to convert from",
			"<to> Unit to convert to",
			"[quantity] Optional quantity to convert" };

	public void commandConvert(Message mes, Modules mods, IRCInterface irc) {
		// Check that we have a suitable number of arguments for the conversion
		// Two will do the conversion for a unit quantity
		String[] parameters = mods.util.getParamArray(mes);
		String inputFrom = "";
		String inputTo = "";
		Double qty = 1.0;
		switch (parameters.length) {
		case 4:
			try {
				qty = Double.valueOf(Colors
						.removeFormattingAndColors(parameters[3]));
			} catch (NumberFormatException nfe) {
				// Wasn't a number!
				irc.sendContextReply(mes,
						"The third parameter must be a number");
				return;
			}
		case 3:
			inputFrom = Colors.removeFormattingAndColors(parameters[1])
					.toLowerCase();
			inputTo = Colors.removeFormattingAndColors(parameters[2])
					.toLowerCase();
			break;
		default:
			// Doing it wrong
			irc.sendContextReply(mes,
					"Incorrect number of arguments specified.");
			return;
		}

		// Perform aliasing
		String from = doTypeAliases(inputFrom);
		String to = doTypeAliases(inputTo);

		// Parse the first argument for the type of thing being converted
		if (!typeMapping.containsKey(from)) {
			irc.sendContextReply(mes, "Cannot convert from " + from);
			return;
		}
		conversionTypes fromType = typeMapping.get(from);

		// Parse the second argument to confirm that it is of the same type
		if (!typeMapping.containsKey(to)) {
			irc.sendContextReply(mes, "Cannot convert to " + to);
			return;
		}
		if (!fromType.equals(typeMapping.get(to))) {
			// Can't convert across types
			irc.sendContextReply(mes,
					"Both parts of the conversion must be of the same type");
			return;
		}

		// Don't bother doing sums if they are the same unit
		if (from.equals(to)) {
			StringBuffer responseBuffer = new StringBuffer();
			responseBuffer.append(qty);
			responseBuffer.append(" ");
			responseBuffer.append(inputFrom);
			responseBuffer.append(" = ");
			responseBuffer.append(qty);
			responseBuffer.append(" ");
			responseBuffer.append(inputTo);
			irc.sendContextReply(mes, responseBuffer.toString());
			return;
		}

		// Do the conversion using the lookup tables
		Double result = doConvert(from, to, qty);
		if (result.isNaN()) {
			// Oh dear, the conversion broke
			irc.sendContextReply(mes, "Could not perform conversion");
			return;
		}
		if (result.isInfinite()) {
			irc.sendContextReply(mes, "Value was too large to convert.");
			return;
		}
		StringBuffer responseBuffer = new StringBuffer();
		responseBuffer.append(qty);
		responseBuffer.append(" ");
		responseBuffer.append(inputFrom);
		responseBuffer.append(" = ");
		responseBuffer.append(result);
		responseBuffer.append(" ");
		responseBuffer.append(inputTo);
		irc.sendContextReply(mes, responseBuffer.toString());

	}

	private String doTypeAliases(String aliased) {
		if (typeAliases.containsKey(aliased)) {
			return typeAliases.get(aliased);
		} else {
			return aliased;
		}
	}

	private Double doConvert(String from, String to, Double qty) {
		conversionTypes type = typeMapping.get(from);
		switch (type) {
		case MASS:
			return doMassConversion(from, to, qty);
		case VOLUME:
			return doVolumeConversion(from, to, qty);
		case LENGTH:
			return doLengthConversion(from, to, qty);
		case TEMPERATURE:
			return doTemperatureConversion(from, to, qty);
		case VELOCITY:
			return doVelocityConversion(from, to, qty);
		default:
			return Double.NaN;
		}
	}

	private Double doVelocityConversion(String from, String to, Double qty) {
		if ((!velocitiesInMetresPerSecond.containsKey(from))
				|| (!velocitiesInMetresPerSecond.containsKey(to))) {
			return Double.NaN;
		}
		return qty.doubleValue()
				* velocitiesInMetresPerSecond.get(from).doubleValue()
				/ velocitiesInMetresPerSecond.get(to).doubleValue();
	}

	private Double doTemperatureConversion(String from, String to, Double qty) {
		if ("celsius".equals(from)) {
			if ("fahrenheit".equals(to)) {
				return (qty * 1.8) + 32;
			} else if ("kelvin".equals(to)) {
				return qty + 273.15;
			}
		} else if ("fahrenheit".equals(from)) {
			if ("celsius".equals(to)) {
				return (qty - 32) / 1.8;
			}
			if ("kelvin".equals(to)) {
				return ((qty - 32) / 1.8) + 273.15;
			}
		}
		return Double.NaN;
	}

	private Double doLengthConversion(String from, String to, Double qty) {
		// Convert qty to metres
		if ((!lengthsInMetres.containsKey(from))
				|| (!lengthsInMetres.containsKey(to))) {
			return Double.NaN;
		}
		return qty.doubleValue() * lengthsInMetres.get(from).doubleValue()
				/ lengthsInMetres.get(to).doubleValue();
	}

	private Double doVolumeConversion(String from, String to, Double qty) {
		if ((!volumesInLitres.containsKey(from))
				|| (!volumesInLitres.containsKey(to))) {
			return Double.NaN;
		}
		return qty.doubleValue() * volumesInLitres.get(from).doubleValue()
				/ volumesInLitres.get(to).doubleValue();
	}

	private Double doMassConversion(String from, String to, Double qty) {
		if ((!massesInKilograms.containsKey(from))
				|| (!massesInKilograms.containsKey(to))) {
			return Double.NaN;
		}
		return qty.doubleValue() * massesInKilograms.get(from).doubleValue()
				/ massesInKilograms.get(to).doubleValue();
	}
}
