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

import java.util.Locale;

/**
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
public class NumberParser {
    /** log error string. */
    private static final String ERROR_NOT_A_BOOLEAN = "Not a boolean: ";

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
               || "0,0".equals(s);
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

        } else {
            final String stringValue = source.toString().trim();
            if (isZero(stringValue)) {
                return 0;
            } else {
                try {
                    return Long.parseLong(stringValue);
                } catch (@NonNull final NumberFormatException e) {
                    // as a last resort try boolean
                    return toBoolean(source) ? 1 : 0;
                }
            }
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

        } else {
            final String stringValue = source.toString().trim();
            if (isZero(stringValue)) {
                return 0;
            } else {
                try {
                    return Integer.parseInt(stringValue);
                } catch (@NonNull final NumberFormatException e) {
                    // as a last resort try boolean
                    return toBoolean(source) ? 1 : 0;
                }
            }
        }
    }

    /**
     * Translate the passed Object to a boolean value.
     *
     * @param source Object to convert
     *
     * @return Resulting value, {@code null} or empty string become {@code false}.
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public static boolean toBoolean(@Nullable final Object source)
            throws NumberFormatException {

        if (source == null) {
            return false;

        } else if (source instanceof Boolean) {
            return (boolean) source;

        } else if (source instanceof Number) {
            return ((Number) source).longValue() != 0;

        } else {
            return parseBoolean(source.toString(), true);
        }
    }

    /**
     * Translate the passed String to a boolean value.
     * <p>
     * If 'emptyIsFalse' is set to {@code true};
     * we return {@code false} if the string was {@code null} or empty.
     * If set to {@code false}, we throw a NumberFormatException exception.
     *
     * @param source       String to parse
     * @param emptyIsFalse how to handle a {@code null} or empty string
     *
     * @return boolean value
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public static boolean parseBoolean(@Nullable final String source,
                                       final boolean emptyIsFalse)
            throws NumberFormatException {

        if (source == null || source.trim().isEmpty()) {
            if (emptyIsFalse) {
                return false;
            } else {
                throw new NumberFormatException(ERROR_NOT_A_BOOLEAN + source);
            }
        } else {
            // we only do english terms, as it's expected that these come from some
            // sort of program code during imports etc...
            final String stringValue = source.trim().toLowerCase(Locale.ENGLISH);
            switch (stringValue) {
                case "1":
                case "y":
                case "yes":
                case "t":
                case "true":
                    return true;

                case "0":
                case "n":
                case "no":
                case "f":
                case "false":
                    return false;

                default:
                    try {
                        return Integer.parseInt(stringValue) != 0;
                    } catch (@NonNull final NumberFormatException e) {
                        throw new NumberFormatException(ERROR_NOT_A_BOOLEAN + source);
                    }
            }
        }
    }
}
