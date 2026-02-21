/*
 * @Copyright 2018-2026 HardBackNutter
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

import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Used to encode quotes and dates etc.
 */
public final class SqlEncode {

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
}
