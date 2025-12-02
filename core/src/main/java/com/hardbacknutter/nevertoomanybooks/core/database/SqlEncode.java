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
package com.hardbacknutter.nevertoomanybooks.core.database;

import android.os.Build;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.core.utils.TextNormalizerApi26;
import com.hardbacknutter.nevertoomanybooks.core.utils.TextNormalizerApi29;

/**
 * Used to create {@code ORDER BY} suitable strings, quotes, dates etc.
 * <p>
 * This class (and similar UNICODE handling classes) MUST be tested with "androidTest"
 * as unit-testing will cause false positives/failures due to the lack/presence
 * of the flag {@code Pattern.UNICODE_CHARACTER_CLASS}.
 * <p>
 * See <a href="https://issuetracker.google.com/issues/181655428">Google bug 181655428</a>
 * and <a href="https://issuetracker.google.com/issues/127290684">Google bug 127290684</a>
 * <pre>
 *  1. Normally we should use the flag:
 *          {@code Pattern.UNICODE_CHARACTER_CLASS}
 *     but android does not need/support it as it always uses
 *     unicode (it says...)
 *     When using {Alnum} Android will NOT use unicode contradicting
 *     the above.
 *
 *  2. Combining explicit unicode {IsAlphabetic} with 'd' for digits
 *     Pattern.compile("[^\\p{IsAlphabetic}\\d ]");
 *     and unit testing on JDK 17 (Windows) works fine, but fails
 *     with on-device test.
 *     google bug: https://issuetracker.google.com/issues/181655428
 *
 *  3. Using as per google bug:
 *     Pattern.compile("[^\\p{Alpha}\\d ]");
 *     unit testing fails on the hosting JDK 17 for non-latin
 *     (works for latin), but works with on-device test.
 * </pre>
 * <p>
 * Passes "androidTest" on API 26,27,31,33
 *
 * @see TextNormalizerApi26
 * @see TextNormalizerApi29
 */
public final class SqlEncode {

    /** Keep only alpha/digit. KEEPS spaces. */
    public static final Pattern NORMALIZE_PATTERN = Pattern.compile("[^\\p{Alpha}\\d ]");
    /** Keep only alpha/digit. NO SPACES */
    public static final Pattern ORDERBY_PATTERN = Pattern.compile("[^\\p{Alpha}\\d]");

    /** See {@link #singleQuotes}. */
    private static final Pattern SINGLE_QUOTE_LITERAL = Pattern.compile("'", Pattern.LITERAL);
    /** See {@link #dateTime(LocalDateTime)}. */
    private static final Pattern T = Pattern.compile("T");

    private SqlEncode() {
    }

    /**
     * Escape single quotation marks by doubling them (standard SQL escape).
     *
     * @param value to encode
     *
     * @return escaped value.
     */
    @NonNull
    public static String singleQuotes(@NonNull final CharSequence value) {
        return SINGLE_QUOTE_LITERAL.matcher(value).replaceAll("''");
    }

    /**
     * Encode a LocalDateTime. Used to transform Java-ISO to SQL-ISO datetime format.
     * <p>
     * Main/only function for now: replace the 'T' character with a ' '
     * so it matches the "current_timestamp" function in SQLite.
     * We should just create a formatter which uses a ' '
     *
     * @param dateTime to encode
     *
     * @return sqlite date time as a string
     */
    @NonNull
    public static String dateTime(@NonNull final LocalDateTime dateTime) {
        // We should just create a formatter which uses a ' '...
        final String date = dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return T.matcher(date).replaceFirst(" ");
    }

    /**
     * Encode a LocalDateTime. Used to transform Java-ISO to SQL-ISO datetime format.
     *
     * @param dateTime to encode
     *
     * @return sqlite date time as a string
     */
    @NonNull
    public static String dateTime(@NonNull final CharSequence dateTime) {
        return T.matcher(dateTime).replaceFirst(" ");
    }

    /**
     * Prepare a string to be inserted in the 'Order By' column.
     * e.g. Author names, the Title of a book
     * Keep normalized basic characters and digits, strip spaces, make all lowercase.
     *
     * @param text   to normalize
     * @param locale Current Locale
     *
     * @return normalized text; always lowercase
     */
    @NonNull
    public static String orderByColumn(@NonNull final CharSequence text,
                                       @NonNull final Locale locale) {
        return normalize(text, ORDERBY_PATTERN).toLowerCase(locale);
    }

    /**
     * Normalize the given string and remove any non-alpha/digit/space characters.
     * The case is preserved.
     *
     * @param text to normalize
     *
     * @return normalized text
     */
    @NonNull
    public static String normalize(@NonNull final CharSequence text) {
        return normalize(text, NORMALIZE_PATTERN);
    }

    /**
     * Normalize the given string and apply the given pattern.
     * The case is preserved.
     *
     * @param text to normalize
     * @param keep negated pattern of characters to keep after transliteration
     *
     * @return normalized text
     */
    @NonNull
    public static String normalize(@NonNull final CharSequence text,
                                   @NonNull final Pattern keep) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return TextNormalizerApi29.normalize(text, keep);
        } else {
            return TextNormalizerApi26.normalize(text, keep);
        }
    }
}
