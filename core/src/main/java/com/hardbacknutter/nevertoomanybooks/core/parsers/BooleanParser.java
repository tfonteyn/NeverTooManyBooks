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

public final class BooleanParser {
    /** log error string. */
    private static final String ERROR_NOT_A_BOOLEAN = "Not a boolean: ";

    private BooleanParser() {
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
        }

        return parseBoolean(source.toString(), true);
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
        }

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
