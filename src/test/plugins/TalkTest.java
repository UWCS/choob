import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TalkTest {
	@Test public void testReverseSimple() {
		assertEquals("ynop", Talk.commandReverse("pony"));
	}

	@Test public void testReverseCombined() {
		assertEquals("ßéíñóø", Talk.commandReverse("øóñíéß"));
	}

	@Test public void testReverseUncombined() {
		assertEquals("a\u0300b\u0301c\u0302", Talk.commandReverse("c\u0302b\u0301a\u0300"));
	}

	@Test public void testReverseLotsOfCombined() {
		final String stackeda = "a\u0300\u0301\u0302";
		assertEquals(stackeda, Talk.commandReverse(stackeda));
		assertEquals(stackeda + "ynop", Talk.commandReverse("pony" + stackeda));
		assertEquals("ynop" + stackeda, Talk.commandReverse(stackeda + "pony"));
		assertEquals("yn" + stackeda + "op", Talk.commandReverse("po" + stackeda + "ny"));
	}

	@Test public void testReverseNonBMP() {
		final int boxLetter1 = 194586;
		final String twoOnes = stringOf(boxLetter1, boxLetter1);
		assertEquals(twoOnes, Talk.commandReverse(twoOnes));

		final int boxLetter2 = 133337;
		assertEquals(stringOf(boxLetter2, boxLetter1), Talk.commandReverse(stringOf(boxLetter1, boxLetter2)));

		assertEquals(stringOfPoints("omg", boxLetter1, "ponies"), Talk.commandReverse(stringOfPoints("seinop", boxLetter1, "gmo")));
	}

	@Test public void testTest() {
		assertEquals("cat", stringOfPoints('c', (int)'a', "t"));
		assertEquals("cat", stringOf('c', 'a', 't'));
		assertEquals("ca116", stringOfPoints('c', 'a', (long)'t'));
	}

	private static String stringOf(int... points) {
		return new String(points, 0, points.length);
	}

	private static String stringOfPoints(Object... points) {
		final StringBuilder ret = new StringBuilder();
		for (Object o : points)
			if (o instanceof Integer)
				ret.appendCodePoint((Integer) o);
			else
				ret.append(o);
		return ret.toString();
	}
}
