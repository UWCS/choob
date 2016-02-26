import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.ParseException;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.lobobrowser.html.parser.DocumentBuilderImpl;
import org.lobobrowser.html.parser.InputSourceImpl;
import org.lobobrowser.html.test.SimpleUserAgentContext;
import org.w3c.dom.html2.HTMLDocument;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class Web
{
	private static final int MAXIMUM_DATA_LENGTH = 400;

	private final JSONParser jsonParser = new JSONParser();

	public String[] info()
	{
		return new String[] {
			"Plugin to fetch stuff from the web.",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"$Rev$$Date$"
		};
	}

	public String commandGetAndSelect(final String paramString)
	{
		return commandXML(paramString);
	}

	public static String commandXML(final String paramString)
	{
		try {
			final int splitAt = paramString.indexOf(" ");
			final String uri = paramString.substring(0, splitAt);
			final String xpathIn = paramString.substring(splitAt + 1, paramString.length());
			final InputStream inputStream = new URL(uri).openConnection().getInputStream();
			return process(inputStream, uri, xpathIn);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static String process(InputStream inputStream, String uri, String xpathString) throws SAXException, IOException, XPathExpressionException
	{
		final HTMLDocument doc = getDocument(inputStream, uri);

		final XPath xpath = XPathFactory.newInstance().newXPath();
		final StringBuilder result = new StringBuilder();

		for (final String xpathOr : xpathString.split("\\|")) {
			result.append(xpath.evaluate(xpathOr.trim(), doc).replaceAll("\\s+", " "));
		}

		if (result.length() > MAXIMUM_DATA_LENGTH)
			return result.substring(0, MAXIMUM_DATA_LENGTH);
		return result.toString();
	}

	public String commandJSON(final String paramString)
	{
		try {
			final int splitAt = paramString.indexOf(" ");
			final String uri = paramString.substring(0, splitAt);
			final String queryIn = paramString.substring(splitAt + 1, paramString.length());
			final Object doc = jsonParser.parse(new InputStreamReader(new URL(uri).openStream()));

			final StringBuilder result = new StringBuilder();

			for (final String query : queryIn.split("\\|")) {
				final String queryT = query.trim();
				if (queryT.startsWith("'") || queryT.startsWith("\""))
					result.append(unquoteString(queryT));
				else
					result.append(jsonqEvaluate(doc, queryT).toString().replaceAll("\\s+", " "));
			}

			if (result.length() > MAXIMUM_DATA_LENGTH)
				return result.substring(0, MAXIMUM_DATA_LENGTH);
			return result.toString();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static HTMLDocument getDocument(final InputStream input, final String uri) throws SAXException, IOException
	{
		final SimpleUserAgentContext context = new SimpleUserAgentContext();
		context.setScriptingEnabled(false);
		final DocumentBuilderImpl dbi = new DocumentBuilderImpl(context);
		final InputSource source = new InputSourceImpl(input,uri,"UTF-8");
		return (HTMLDocument) dbi.parse(source);
	}

	private Object jsonqEvaluate(final Object json, final String query) throws ParseException, PropertyException
	{
		final int start = query.startsWith(".") ? 1 : 0;
		final int ars = query.indexOf("[");
		final int are = query.indexOf("]", ars);
		final int dot = query.indexOf(".", start);
		final String property;
		final String subQuery;
		if (ars != -1 && (ars < dot || dot == -1)) {
			if (are == -1) throw new ParseException("Could not find matched ]", 0);
			if (ars == 0) {
				final String queryProperty = query.substring(1, are);
				if (queryProperty.startsWith("'") || queryProperty.startsWith("\"")) {
					property = unquoteString(queryProperty);
					subQuery = query.substring(are + 1);
				} else {
					property = queryProperty;
					subQuery = query.substring(are + 1);
				}
			} else {
				property = query.substring(start, ars);
				subQuery = query.substring(ars);
			}
		} else if (dot != -1) {
			property = query.substring(start, dot);
			subQuery = query.substring(dot + 1);
		} else if (query.length() > start) {
			property = query.substring(start);
			subQuery = null;
		} else {
			return json;
		}
		final Object jsonChild = json instanceof JSONArray ? ((JSONArray)json).get(Integer.parseInt(property)) : ((JSONObject)json).get(property);
		if (jsonChild == null) throw new PropertyException("Property '" + property + "' not found");
		if (subQuery != null)
			return jsonqEvaluate(jsonChild, subQuery);
		return jsonChild;
	}

	private String unquoteString(final String string) throws ParseException
	{
		if (string.startsWith("'") || string.startsWith("\"")) {
			if (string.startsWith("'") && !string.endsWith("'")) throw new ParseException("Could not find matching '", 0);
			if (string.startsWith("\"") && !string.endsWith("\"")) throw new ParseException("Could not find matching \"", 0);
			return string.substring(1, string.length() - 1);
		}
		return string;
	}
}

class PropertyException extends Exception
{
	public PropertyException(final String message)
	{
		super(message);
	}
}
