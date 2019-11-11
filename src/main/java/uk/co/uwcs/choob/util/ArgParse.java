package uk.co.uwcs.choob.util;

import com.google.common.collect.Lists;
import uk.co.uwcs.choob.exceptions.UnmatchedBracketException;

import java.util.EnumSet;
import java.util.List;

/**
 * Utility class for parsing arguments to Choob commands.
 * @author rayhaan
 */
public class ArgParse {

    /**
     * Tweak how commands are parsed.
     */
    public enum ParseMode {
        // Convert any spaces between quotes to underscores.
        SPACE_IN_QUOTE_TO_UNDERSCORE;
    }

    private ArgParse() {}

    private static boolean isQuotationCharacter(char c) {
        return (c == '"') || (c == '\'') || (c == '«') || (c == '»');
    }

    public static List<String> split(String message, EnumSet<ParseMode> modes) throws UnmatchedBracketException {
        final char[] chars = message.toCharArray();

        List<String> result = Lists.newArrayList();
        StringBuilder currentWord = new StringBuilder();

        boolean containsNonEmpty = false;
        boolean modeInQuote = false;

        for (int pos = 0; pos < chars.length; pos++) {
            boolean quoteChar = isQuotationCharacter(chars[pos]);
            if (quoteChar && !modeInQuote) {
                // We are now entering a quotation, everything in here is a new string
                modeInQuote = true;
                continue;
            }
            if (quoteChar && modeInQuote) {
                // Exit the quote and move on to the next word.
                modeInQuote = false;
                result.add(currentWord.toString());
                currentWord = new StringBuilder();
                containsNonEmpty = false;
                continue;
            }
            if (chars[pos] == '\\' && isQuotationCharacter(chars[pos + 1])) {
                // skip over the next character
                pos++;
                continue;
            }
            // If we are in quote mode then convert spaces to underscores.
            if (modes.contains(ParseMode.SPACE_IN_QUOTE_TO_UNDERSCORE) && modeInQuote && chars[pos] == ' ') {
                currentWord.append('_');
                continue;
            }
            if (chars[pos] == ' ' && !modeInQuote && containsNonEmpty) {
                result.add(currentWord.toString());
                currentWord = new StringBuilder();
                containsNonEmpty = false;
                continue;
            }
            if (chars[pos] != ' ') containsNonEmpty = true;
            if (chars[pos] == ' ' && !containsNonEmpty) continue;
            currentWord.append(chars[pos]);
        }

        // If we are still in a quote at the end something has gone wrong.
        if (modeInQuote) {
            throw new UnmatchedBracketException();
        }

        // Append the last word.
        result.add(currentWord.toString());
        return result;
    }
}
