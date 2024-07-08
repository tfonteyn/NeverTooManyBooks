/*
 * @Copyright 2018-2024 HardBackNutter
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

    private static final String LRM_CHAR = "\u200E";
    private static final String RTL_CHAR = "\u200F";
    /** a Right-to-left is replaced with a space. */
    private static final Pattern LRM_LITERAL = Pattern.compile(LRM_CHAR, Pattern.LITERAL);
    /** a Left-to-right is replaced with a space. */
    private static final Pattern RTL_LITERAL = Pattern.compile(RTL_CHAR, Pattern.LITERAL);

    /** Trim extraneous punctuation and whitespace from the titles and authors. */
    private static final Pattern CLEANUP_TITLE_PATTERN =
            Pattern.compile("[,.':;`~@#$%^&*(\\-=_+\u200E\u200F]*$");

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
     * Clean the given text. Currently cleans up {@code &}, {@code div} and {@code \n}.
     *
     * @param s to clean
     *
     * @return cleansed string
     */
    @NonNull
    public static String cleanText(@NonNull final String s) {
        String text = s.strip();
        // add more rules when needed.
        if (text.contains("&")) {
            text = AMPERSAND_LITERAL.matcher(text).replaceAll(
                    Matcher.quoteReplacement("&"));
        }
        if (text.contains("<div>")) {
            // the div elements only create empty lines, we remove them to save screen space
            text = DIV_PATTERN.matcher(text).replaceAll("");
        }
        if (text.contains("\n")) {
            text = CR_LITERAL.matcher(text).replaceAll(" ");
        }
        if (text.contains(LRM_CHAR)) {
            text = LRM_LITERAL.matcher(text).replaceAll(" ");
        }
        if (text.contains(RTL_CHAR)) {
            text = RTL_LITERAL.matcher(text).replaceAll(" ");
        }
        return text.strip();
    }

    /**
     * Variant of {@link #cleanText(String)} which does additional cleanup
     * specific to author names and titles.
     *
     * @param s to clean
     *
     * @return cleansed string
     */
    @NonNull
    public static String cleanName(@NonNull final String s) {
        final String tmp = cleanText(s.trim());
        return CLEANUP_TITLE_PATTERN.matcher(tmp).replaceAll("").trim();
    }
}
