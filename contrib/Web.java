import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.lobobrowser.html.UserAgentContext;
import org.lobobrowser.html.parser.DocumentBuilderImpl;
import org.lobobrowser.html.parser.InputSourceImpl;
import org.lobobrowser.html.test.SimpleUserAgentContext;
import org.w3c.dom.html2.HTMLDocument;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class Web
{
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
		try {
			final int splitAt = paramString.indexOf(" ");
			final String uri = paramString.substring(0,splitAt);
			final String xpathIn = paramString.substring(splitAt + 1, paramString.length());
			final HTMLDocument doc = getDocument(new URL(uri).openConnection().getInputStream(),uri);

			final XPath xpath = XPathFactory.newInstance().newXPath();
			final StringBuilder result = new StringBuilder();

			for (final String xpathOr : xpathIn.split("\\|")) {
				result.append(xpath.evaluate(xpathOr.trim(), doc).toString().replaceAll("\\s+", " "));
			}

			return result.toString();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private HTMLDocument getDocument(final InputStream input, final String uri) throws SAXException, IOException
	{
		final UserAgentContext context = new SimpleUserAgentContext();
		final DocumentBuilderImpl dbi = new DocumentBuilderImpl(context);
		final InputSource source = new InputSourceImpl(input,uri,"UTF-8");
		return (HTMLDocument) dbi.parse(source);
	}
}
