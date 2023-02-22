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
package com.hardbacknutter.nevertoomanybooks.database;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.core.utils.AsciiNormalizer;

public final class SqlEncode {

    /** See {@link #orderByColumn}. */
    private static final Pattern NON_WORD_CHARACTER_PATTERN = Pattern.compile("\\W");
    /** See {@link #string}. */
    private static final Pattern SINGLE_QUOTE_LITERAL = Pattern.compile("'", Pattern.LITERAL);
    /** See {@link #date(LocalDateTime)}. */
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
    public static String string(@NonNull final CharSequence value) {
        return SINGLE_QUOTE_LITERAL.matcher(value).replaceAll("''");
    }

    /**
     * Prepare a string to be inserted in the 'Order By' column.
     * e.g. Author names, the Title of a book: strip spaces etc, make lowercase,...
     *
     * @param value  to encode
     * @param locale to use for case manipulation
     *
     * @return the encoded value
     */
    @NonNull
    public static String orderByColumn(@NonNull final CharSequence value,
                                       @NonNull final Locale locale) {
        final String s = AsciiNormalizer.toAscii(value);
        // remove all non-word characters. i.e. all characters not in [a-zA-Z_0-9]
        return NON_WORD_CHARACTER_PATTERN.matcher(s).replaceAll("").toLowerCase(locale);
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
    public static String date(@NonNull final LocalDateTime dateTime) {
        // We should just create a formatter which uses a ' '...
        final String date = dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return T.matcher(date).replaceFirst(" ");
    }
}
