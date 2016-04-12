import static org.junit.Assert.assertEquals;

public class WebTest
{
	@org.junit.Test
	public void testWp() throws Exception
	{
		assertEquals("<p>A <b>birthday attack</b> is a type of" +
				" <a href=\"/wiki/Cryptography\" title=\"Cryptography\">cryptographic</a>" +
				" <a href=\"/wiki/Cryptanalysis\" title=\"Cryptanalysis\">attack</a> that" +
				" exploits the <a href=\"/wiki/Mathematics\" title=\"Mathematics\">mathematics</a>" +
				" behind the <a href=\"/wiki/Birthday_problem\" title=\"Birthday problem\">birthday problem</a>" +
				" in <a href=\"/wiki/Probability_theory\" title=\"Probability",
				Web.process(
						WebTest.class.getResourceAsStream("/wp-battack.html"),
						"https://en.wikipedia.org/wiki/Birthday_attack",
						"//div[@class='mw-content-ltr']/p[1]"));
	}

	@org.junit.Test
	public void testWtImgur() throws Exception
	{
		assertEquals("New video from Boston Dynamics - GIF on Imgur",
				Web.process(
						WebTest.class.getResourceAsStream("/imgur-item.html"),
						"https://imgur.com/gallery/efuf46W",
						"//title/text()").trim());
	}
}