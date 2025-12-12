/*
 * @Copyright 2018-2025 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */

package com.hardbacknutter.nevertoomanybooks.searchengines;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchEngineUtils {

    /** Fields can contain div tags which we remove to make the text shorter. */
    private static final Pattern DIV_PATTERN = Pattern.compile("(\n*\\s*<div>\\s*|\\s*</div>)");
    /** Convert "&amp;" to '&'. */
    private static final Pattern AMPERSAND_LITERAL = Pattern.compile("&amp;", Pattern.LITERAL);
    /** a CR is replaced with a space. */
    private static final Pattern CR_LITERAL = Pattern.compile("\n", Pattern.LITERAL);

    /** All non-rendering characters to REMOVE. */
    @SuppressWarnings("UnnecessaryUnicodeEscape")
    private static final Pattern INVISIBLE_CHARS = Pattern.compile(
            "["
            // Zero-width characters
            + "\u200B\u200C\u200D\uFEFF"
            // Word joiner, invisible ops
            + "\u2060-\u2064"
            // Combining grapheme joiner
            + "\u034F"
            // BiDi controls (LRE, RLE, etc.)
            + "\u202A-\u202E"
            // LRI/RLI/FSI/PDI
            + "\u2066-\u2069"
            // Variation Selectors 1–16
            + "\uFE00-\uFE0F"
            // CJK Variation Selectors
            + "\\x{E0100}-\\x{E01EF}"
            // Tag characters (subtag system)
            + "\\x{E0000}-\\x{E007F}"
            + "]"
    );

    /** Non-standard whitespace to replace with a space. */
    private static final Pattern ODD_WHITESPACE = Pattern.compile(
            "[\u00A0\u2000-\u200A\u3000]");

    /** Replace repeated/special whitespace characters with a single space. */
    private static final Pattern WHITESPACE = Pattern.compile(
            "[\\s\u00A0\u2000-\u200A\u3000]+");

    /**
     * Trim extraneous punctuation from the end of titles, authors and other types of names.
     *
     * @see #cleanName(CharSequence)
     */
    private static final Pattern END_PUNCTUATION_PATTERN =
            Pattern.compile("[,.':;`~@#$%^&*(\\-=_+]*$");

    /** Keep only alpha/digit and space characters. */
    private static final Pattern KEEP_PATTERN = Pattern.compile("[^\\p{Alpha}\\d ]");

    private SearchEngineUtils() {
    }

    /**
     * Filter a string of all non-digits.
     *
     * @param s string to parse
     *
     * @return stripped string
     */
    @NonNull
    public static String digits(@Nullable final CharSequence s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        // ... but let empty Strings here just return.
        return sb.toString();
    }

    /**
     * Clean the given text.
     * <p>
     * Currently cleans up {@code &}, {@code div} and {@code \n}
     * and a range of unicode control characters.
     *
     * @param s to clean
     *
     * @return cleansed string
     */
    @NonNull
    public static String cleanText(@NonNull final CharSequence s) {
        // remove all unwanted invisible chars
        String text = INVISIBLE_CHARS.matcher(s).replaceAll("");
        // and replace all special whitespace with a regular single space.
        text = ODD_WHITESPACE.matcher(text).replaceAll(" ");

        if (text.contains("\n")) {
            text = CR_LITERAL.matcher(text).replaceAll(" ");
        }

        // replace any html encoded ampersands.
        if (text.contains("&")) {
            text = AMPERSAND_LITERAL.matcher(text).replaceAll(Matcher.quoteReplacement("&"));
        }

        // div elements only create empty lines, we remove them to save screen space
        if (text.contains("<div>")) {
            text = DIV_PATTERN.matcher(text).replaceAll("");
        }

        // add more rules when needed.

        return text.strip();
    }

    /**
     * Variant of {@link #cleanText(CharSequence)} which does additional cleanup
     * specific to author names and titles.
     *
     * @param s to clean
     *
     * @return cleansed string
     */
    @NonNull
    public static String cleanName(@NonNull final CharSequence s) {
        final String text = cleanText(s);
        // remove any junk characters from the end of the string
        return END_PUNCTUATION_PATTERN.matcher(text).replaceAll("").strip();
    }

    /**
     * Sanitize a parameter string for use in a url search.
     *
     * @param search to clean
     *
     * @return cleansed string
     */
    @NonNull
    public static String encodeSearchString(@Nullable final CharSequence search) {
        if (search == null || search.length() == 0) {
            return "";
        }

        final String result = WHITESPACE.matcher(search).replaceAll(" ");
        return KEEP_PATTERN.matcher(result).replaceAll("");
    }
}
