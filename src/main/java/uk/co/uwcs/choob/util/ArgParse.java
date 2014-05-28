package uk.co.uwcs.choob.util;

import java.util.LinkedList;

/**
 * Provides parsing functionality for choob commands.
 * At this point in time splits strings based on spaces unless quoted.
 * Created by rayhaan on 28/05/14.
 */
public class ArgParse {

    public static final int MODE_QUOTED_SPACE_TO_UNDERSCORE = 0x01;

    private String message;
    private LinkedList<String> parts;
    private boolean quotedSpaceToUnderscore = false;

    public ArgParse(String message) {
        this.message = message;
        this.parts = new LinkedList<String>();
    }

    public void setParseMode(int parseMode) {
        if (parseMode == ArgParse.MODE_QUOTED_SPACE_TO_UNDERSCORE) {
            quotedSpaceToUnderscore = true;
        }
    }

    public boolean parse() {
        char[] msg = this.message.toCharArray();

        StringBuilder currentWord = new StringBuilder(msg.length);

        boolean modeInQuote = false;

        for (int pos = 0; pos<msg.length; pos++) {

            // If we are not in a quote
            if (msg[pos] == '"' && !modeInQuote) {
                modeInQuote = true;
                continue; // We are done at this character
            } else if (msg[pos] == '"' && modeInQuote) {
                // exit from the quote
                modeInQuote = false;
                continue;
            } else if (msg[pos] == '\\' && msg[pos+1] == '"') {
                // skip the next character as it is an escaped quote
                pos++;
                continue;
            } else if (msg[pos] == ' ' && modeInQuote && quotedSpaceToUnderscore) {
                currentWord.append('_');
                continue;
            } else if (msg[pos] == ' ' && !modeInQuote) {
                // add currentWord to parts and reset currentWord
                parts.add(currentWord.toString());
                currentWord = new StringBuilder(msg.length);
                continue;
            }

            // add the character to the current word
            currentWord.append(msg[pos]);
        }
        // flush the last word
        parts.add(currentWord.toString());
        // assert that we are not in quote mode at the end of the string
        return (!modeInQuote);
    }

    public LinkedList<String> getParts () { return parts; }

    public static void main(String... args) {
        ArgParse ap = new ArgParse("lorem ipsum \"foo's bar\" \"foo's second bar\" this is a test");
        ap.setParseMode(ArgParse.MODE_QUOTED_SPACE_TO_UNDERSCORE);
        boolean success = ap.parse();
        System.out.println((success) ? "worked" : "failed");
        for (String s : ap.getParts()) {
            System.out.println(s);
        }
    }



}
