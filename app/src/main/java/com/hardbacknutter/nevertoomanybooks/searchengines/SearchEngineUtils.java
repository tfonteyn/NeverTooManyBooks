/*
 * @Copyright 2018-2023 HardBackNutter
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
    /** Trim extraneous punctuation and whitespace from the titles and authors. */
    private static final Pattern CLEANUP_TITLE_PATTERN =
            Pattern.compile("[,.':;`~@#$%^&*(\\-=_+]*$");

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
    public static String digits(@Nullable final String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            // allows an X anywhere instead of just at the end; doesn't really matter.
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        // ... but let empty Strings here just return.
        return sb.toString();
    }

    @NonNull
    public static String cleanText(@NonNull final String s) {
        String text = s.trim();
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
            text = CR_LITERAL.matcher(text).replaceAll(" ").trim();
        }
        return text;
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
