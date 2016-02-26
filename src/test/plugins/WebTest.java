public class WebTest
{
	@org.junit.Test
	public void testWp() throws Exception
	{
		Web.process(
				WebTest.class.getResourceAsStream("/wp-battack.html"),
				"https://en.wikipedia.org/wiki/Birthday_attack",
				"//div[@class='mw-content-ltr']/p[1]");
	}
}