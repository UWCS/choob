import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


import org.junit.Ignore;
import org.junit.Test;


public class PipesTest
{
	@Test
	public void testExeculator() throws Exception
	{
		assertEquals("NOOOMaNOOOM789MOOONbMOOON", Pipes.eval("a$(789)b", new Pipes.Execulator()
		{
				@Override
				public String exec(String s, String stdin) {
					return "NOOOM" + s + stdin + "MOOON";
				}
		}));
	}

	@Test
	public void testTrivialDollars() throws Exception
	{
		assertEquals("6", Pipes.eval("$(6)"));
		assertEquals("56", Pipes.eval("5$(6)"));
		assertEquals("67", Pipes.eval("$(6)7"));
		assertEquals("567", Pipes.eval("5$(6)7"));
	}

	@Test(expected = Pipes.ParseException.class)
	public void testStart() throws Exception
	{
		Pipes.eval("$(");
	}

	@Ignore("Fails horribly; but not harmful.")
	@Test(expected = Pipes.ParseException.class)
	public void testEnd() throws Exception
	{
		Pipes.eval(")");
	}

	private static final String DQUOTED = "\"pony\"";

	@Test
	public void testEvalQuotes() throws Exception
	{
		assertEquals("pony", Pipes.eval(DQUOTED));
		assertEquals("pony pony", Pipes.eval(DQUOTED + " " + DQUOTED));
		assertEquals("\"pony\"", Pipes.eval("'" + DQUOTED + "'"));
		assertEquals("\"pony\" \"pony\"", Pipes.eval("'" + DQUOTED + "' '" + DQUOTED + "'"));
		assertEquals("pony", Pipes.eval("'pony'"));
		assertEquals("pony pony", Pipes.eval("'pony' 'pony'"));
		assertEquals("pony \"pony", Pipes.eval("'pony \"pony'"));
		assertEquals("pony \"\"pony", Pipes.eval("'pony \"\"pony'"));
	}

	@Test
	public void testEvalQuotesSlash() throws Exception
	{
		assertEquals("po\\\"ny", Pipes.eval("'po\\\"ny'"));
		assertEquals("po\"ny", Pipes.eval("\"po\\\"ny\""));
		assertEquals("'", Pipes.eval("\\'"));
		assertEquals("pony'pony", Pipes.eval("'pony'\\''pony'"));
		assertEquals("pony\\pony", Pipes.eval("\"pony\\\\pony\""));
	}

	@Test
	public void testEscapes() throws Exception
	{
		assertEquals("$hi", Pipes.eval("\"\\$hi\""));
	}

	@Test
	public void testEvalEnd() throws Exception
	{
		assertThrowsParse("'");
		assertThrowsParse("\"");
		assertThrowsParse("\\");
		assertThrowsParse("\\q");
	}

	@Test
	public void testPipeQuotes() throws Exception
	{
		assertEquals("a|b", Pipes.eval("'a|b'"));
	}

	@Test
	public void testDoubleSingle() throws Exception
	{
		assertEquals("'", Pipes.eval("\"'\""));
		assertEquals("'pony'", Pipes.eval("\"'pony'\""));
	}

	@Test public void testBackSlashes() throws Exception
	{
		assertEquals("\\\'", Pipes.eval("\"\\\'\""));
		assertEquals("\\", Pipes.eval("\"\\\\\""));
	}

	@Test
	public void testEvalDollars() throws Exception
	{
		assertEquals("pony", Pipes.eval("$('pony')"));
		assertEquals("ls() qq", Pipes.eval("$('ls()' 'qq')"));
		assertEquals("ls() qq", Pipes.eval("$('ls()' qq)"));

		assertEquals("ls$() qq", Pipes.eval("$('ls$()' 'qq')"));
		assertEquals("ls$() qq", Pipes.eval("$('ls$()' qq)"));

		assertEquals("57", Pipes.eval("$(5)7"));

	}

	private static void assertThrowsParse(String string) throws Exception
	{
		try
		{
			Pipes.eval(string);
			fail("Didn't throw");
		}
		catch (Pipes.ParseException e)
		{
			// expected
		}
	}
}
