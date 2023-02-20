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

package com.hardbacknutter.nevertoomanybooks.core.parsers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NumberParser {
    /** log error string. */
    private static final String ERROR_NOT_A_BOOLEAN = "Not a boolean: ";
    private static final String ERROR_NOT_A_FLOAT = "Not a float: ";
    private static final String ERROR_NOT_A_DOUBLE = "Not a double: ";

    private NumberParser() {
    }

    /**
     * Fast-parse a string for being zero.
     *
     * @param stringValue to check
     *
     * @return {@code true} if the value was a zero in some form or another
     */
    public static boolean isZero(@Nullable final String stringValue) {
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
     * Translate the passed Object to a {@code float} value.
     *
     * @param localeList Locales to use for the formatter/parser.
     * @param source     Object to convert
     *
     * @return Resulting value ({@code null} or empty string becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public static float toFloat(@NonNull final List<Locale> localeList,
                                @Nullable final Object source)
            throws NumberFormatException {

        if (source == null) {
            return 0f;

        } else if (source instanceof Number) {
            return ((Number) source).floatValue();

        } else {
            final String stringValue = source.toString().trim();
            if (isZero(stringValue)) {
                return 0f;
            }
            try {
                return parseFloat(localeList, stringValue);
            } catch (@NonNull final NumberFormatException e) {
                // as a last resort try boolean
                return toBoolean(source) ? 1 : 0;
            }
        }
    }

    /**
     * Translate the passed Object to a {@code double} value.
     *
     * @param localeList Locales to use for the formatter/parser.
     * @param source     Object to convert
     *
     * @return Resulting value ({@code null} or empty string becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public static double toDouble(@NonNull final List<Locale> localeList,
                                  @Nullable final Object source)
            throws NumberFormatException {

        if (source == null) {
            return 0d;

        } else if (source instanceof Number) {
            return ((Number) source).doubleValue();

        } else {
            final String stringValue = source.toString().trim();
            if (isZero(stringValue)) {
                return 0;
            } else {
                try {
                    return parseDouble(localeList, stringValue);
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

    /**
     * Replacement for {@code Float.parseFloat(String)} using Locales.
     *
     * @param localeList Locales to use for the formatter/parser.
     * @param source     String to parse
     *
     * @return Resulting value ({@code null} or empty becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public static float parseFloat(@NonNull final List<Locale> localeList,
                                   @Nullable final String source)
            throws NumberFormatException {

        if (isZero(source)) {
            return 0f;
        }

        // Sanity check
        if (localeList.isEmpty()) {
            return Float.parseFloat(source);
        }

        // Create a NEW list, and add Locale.US to use for
        // '.' as decimal and ',' as thousands separator.
        final List<Locale> locales = new ArrayList<>(localeList);
        locales.add(Locale.US);

        // we check in order - first match returns.
        for (final Locale locale : locales) {
            try {
                final Number number = DecimalFormat.getInstance(locale).parse(source);
                if (number != null) {
                    return number.floatValue();
                }
            } catch (@NonNull final ParseException | IndexOutOfBoundsException ignore) {
                // ignore
            }
        }

        throw new NumberFormatException(ERROR_NOT_A_FLOAT + source);
    }

    /**
     * Replacement for {@code Double.parseDouble(String)} using Locales.
     *
     * @param localeList Locales to use for the formatter/parser.
     * @param source     String to parse
     *
     * @return Resulting value ({@code null} or empty becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public static double parseDouble(@NonNull final List<Locale> localeList,
                                     @Nullable final String source)
            throws NumberFormatException {

        if (isZero(source)) {
            return 0d;
        }

        // Sanity check
        if (localeList.isEmpty()) {
            return Double.parseDouble(source);
        }

        // no decimal part and no thousands sep ?
        if (source.indexOf('.') == -1 && source.indexOf(',') == -1) {
            return Double.parseDouble(source);
        }

        // Create a NEW list, and add Locale.US to use for
        // '.' as decimal and ',' as thousands separator.
        final List<Locale> locales = new ArrayList<>(localeList);
        locales.add(Locale.US);

        // we check in order - first match returns.
        for (final Locale locale : locales) {
            try {
                final DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(locale);
                final char decSep = nf.getDecimalFormatSymbols().getDecimalSeparator();

                // if the dec sep is present, decode with Locale; otherwise try next Locale
                if (source.indexOf(decSep) != -1) {
                    final Number number = nf.parse(source);
                    if (number != null) {
                        return number.doubleValue();
                    }
                }

            } catch (@NonNull final ParseException | IndexOutOfBoundsException ignore) {
                // ignore
            }
        }
        throw new NumberFormatException(ERROR_NOT_A_DOUBLE + source);
    }
}
