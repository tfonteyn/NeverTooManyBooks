/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.core.parsers.NumberParser;

/**
 * {@link NumberParser#parseFloat} / {@link NumberParser#parseDouble}.
 * <p>
 * Tested with Device running in US Locale, app in Dutch.
 * A price field with content "10.45".
 * The inputType field on the screen was set to "numberDecimal"
 * the keypad does NOT allow the use of ',' as used in Dutch for the decimal separator.
 * Using the Dutch Locale, parsing returns "1045" as the '.' is seen as the thousands separator.
 * <p>
 * 2nd test with the device running in Dutch, and the app set to system Locale.
 * Again the keypad only allowed the '.' to be used.
 * <p>
 * Known issue. Stated to be fixed in Android O == 8.0
 * <a href="https://issuetracker.google.com/issues/36907764">36907764</a>
 * <a href="https://issuetracker.google.com/issues/37015783">37015783</a>
 * <p>
 * <a href="https://stackoverflow.com/questions/3821539#28466764">
 * decimal-separator-comma-with-numberdecimal-inputtype-in-edittext</a>
 * <p>
 * But I test with Android 8.0 ... Americans just can't see beyond their border...
 * To be clear: parsing works fine; it's just the user not able to input the
 * right decimal/thousand separators for their Locale.
 */
@SuppressWarnings("WeakerAccess")
public final class ParseUtils {

    /** See {@link #toAscii}. */
    private static final Pattern ASCII_PATTERN = Pattern.compile("[^\\p{ASCII}]");

    /** Fields can contain div tags which we remove to make the text shorter. */
    private static final Pattern DIV_PATTERN = Pattern.compile("(\n*\\s*<div>\\s*|\\s*</div>)");
    /** Convert "&amp;" to '&'. */
    private static final Pattern AMPERSAND_LITERAL = Pattern.compile("&amp;", Pattern.LITERAL);
    /** a CR is replaced with a space. */
    private static final Pattern CR_LITERAL = Pattern.compile("\n", Pattern.LITERAL);
    /** Trim extraneous punctuation and whitespace from the titles and authors. */
    private static final Pattern CLEANUP_TITLE_PATTERN =
            Pattern.compile("[,.':;`~@#$%^&*(\\-=_+]*$");

    private ParseUtils() {
    }

    /**
     * Encode a string by 'escaping' all instances of:
     * <ul>
     *      <li>'\\', '\r', '\n', '\t'</li>
     *      <li>any additional 'escapeChars'</li>
     * </ul>
     * The escape char is '\'.
     *
     * @param source      String to encode
     * @param escapeChars additional characters to escape. Case sensitive.
     *
     * @return encoded string
     */
    @NonNull
    public static String escape(final char elementSeparator,
                                final char objectSeparator,
                                @NonNull final CharSequence source,
                                @NonNull final char... escapeChars) {

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            final char c = source.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;

                case '\r':
                    sb.append("\\r");
                    break;

                case '\n':
                    sb.append("\\n");
                    break;

                case '\t':
                    sb.append("\\t");
                    break;

                default:
                    if (c == elementSeparator) {
                        // list of elements, just once
                        sb.append("\\");

                    } else if (c == objectSeparator) {
                        // list of objects, inside a list of elements, so TWICE
                        sb.append("\\\\");

                    } else {
                        for (final char e : escapeChars) {
                            if (c == e) {
                                sb.append('\\');
                                // break from the for (char e : escapeChars)
                                break;
                            }
                        }
                    }
                    // add the actual character
                    sb.append(c);
                    break;
            }
        }
        return sb.toString().trim();
    }

    /**
     * Decode a string by removing any escapes.
     * <strong>Does NOT recurse.</strong>
     *
     * @param source String to decode
     *
     * @return decoded string
     */
    @NonNull
    public static String unEscape(@Nullable final String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }

        boolean isEsc = false;
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (isEsc) {
                switch (c) {
                    case '\\':
                        sb.append('\\');
                        break;

                    case 'r':
                        sb.append('\r');
                        break;

                    case 't':
                        sb.append('\t');
                        break;

                    case 'n':
                        sb.append('\n');
                        break;

                    default:
                        sb.append(c);
                        break;
                }
                isEsc = false;
            } else {
                if (c == '\\') {
                    isEsc = true;
                } else {
                    // keep building the element string
                    sb.append(c);
                }
            }
        }
        return sb.toString().trim();
    }


    /**
     * Normalize a given string to contain only ASCII characters for flexible text comparison.
     *
     * @param text to normalize
     *
     * @return ascii text
     */
    @NonNull
    public static String toAscii(@NonNull final CharSequence text) {
        return ASCII_PATTERN.matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
                            .replaceAll("");
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
            text = AMPERSAND_LITERAL.matcher(text).replaceAll(Matcher.quoteReplacement("&"));
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
