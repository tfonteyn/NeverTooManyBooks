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

package com.hardbacknutter.nevertoomanybooks.core.parsers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class NumberParser {

    private NumberParser() {
    }

    /**
     * Fast-parse a string for being zero.
     *
     * @param stringValue to check
     *
     * @return {@code true} if the value was a zero in some form or another
     */
    static boolean isZero(@Nullable final String stringValue) {
        if (stringValue == null) {
            return true;
        }
        final String s = stringValue.trim();
        return s.isEmpty()
               || "0".equals(s)
               || "0.0".equals(s)
               // Comma as decimal separator.
               || "0,0".equals(s)
               // Used by Amazon for free kindle books etc...
               || "free".equalsIgnoreCase(s);
    }

    /**
     * Translate the passed Object to a {@code long} value.
     *
     * @param source Object to convert
     *
     * @return Resulting value ({@code null} or empty string becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public static long toLong(@Nullable final Object source)
            throws NumberFormatException {

        if (source == null) {
            return 0;

        } else if (source instanceof Number) {
            return ((Number) source).longValue();
        }

        final String stringValue = source.toString().trim();
        if (isZero(stringValue)) {
            return 0;
        }

        try {
            return Long.parseLong(stringValue);
        } catch (@NonNull final NumberFormatException e) {
            // as a last resort try boolean
            // This is a safeguard for importing BC backups
            return BooleanParser.toBoolean(source) ? 1 : 0;
        }
    }

    /**
     * Translate the passed Object to an {@code int} value.
     *
     * @param source Object to convert
     *
     * @return Resulting value ({@code null} or empty string becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public static int toInt(@Nullable final Object source)
            throws NumberFormatException {

        if (source == null) {
            return 0;

        } else if (source instanceof Number) {
            return ((Number) source).intValue();
        }

        final String stringValue = source.toString().trim();
        if (isZero(stringValue)) {
            return 0;
        }

        try {
            return Integer.parseInt(stringValue);
        } catch (@NonNull final NumberFormatException e) {
            // as a last resort try boolean
            // This is a safeguard for importing BC backups
            return BooleanParser.toBoolean(source) ? 1 : 0;
        }
    }
}
