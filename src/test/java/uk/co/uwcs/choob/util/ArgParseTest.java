package uk.co.uwcs.choob.util;

import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.junit.Test;
import uk.co.uwcs.choob.exceptions.UnmatchedBracketException;

import java.util.EnumSet;

/**
 * Test to make sure ArgParse works the way that we intend it to.
 */
public class ArgParseTest extends TestCase {

    @Test
    public void testArgParse() {
        try {
            assertEquals(Lists.newArrayList("Lorem", "ipsum", "dolor", "sit", "amet"),
                    ArgParse.split("Lorem ipsum dolor sit amet", EnumSet.noneOf(ArgParse.ParseMode.class)));
            assertEquals(Lists.newArrayList("Lorem", "ipsum dolor sit", "amet"),
                    ArgParse.split("Lorem \"ipsum dolor sit\"     amet", EnumSet.noneOf(ArgParse.ParseMode.class)));
            assertEquals(Lists.newArrayList("Lorem", "ipsum_dolor_sit", "amet"),
                    ArgParse.split("Lorem \"ipsum dolor sit\" amet",
                            EnumSet.of(ArgParse.ParseMode.SPACE_IN_QUOTE_TO_UNDERSCORE)));
        } catch (UnmatchedBracketException ube) {
            fail("Unexpected unmatched bracket exception");
        }
    }

    @Test
    public void testArgFail() {
        try {
            ArgParse.split("!quote badgerbot for \"being awesome", EnumSet.noneOf(ArgParse.ParseMode.class));
            fail();
        } catch (UnmatchedBracketException expected){
            /* Expected exception */
        }
    }

}
