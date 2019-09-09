/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;

public final class ParseUtils {

    /** log error string. */
    private static final String ERROR_INVALID_BOOLEAN_S = "Invalid boolean, s=`";

    private ParseUtils() {
    }

    /**
     * Translate the passed Object to a Long value.
     *
     * @param obj Object
     *
     * @return Resulting value ({@code null} or empty becomes 0)
     */
    public static long toLong(@Nullable final Object obj) {
        if (obj == null) {
            return 0;

        } else if (obj instanceof Long) {
            return (Long) obj;

        } else if (obj instanceof Integer) {
            return ((Integer) obj).longValue();

        } else {
            String stringValue = obj.toString().trim();
            if (stringValue.isEmpty()) {
                return 0;
            } else {
                try {
                    return Long.parseLong(stringValue);
                } catch (@NonNull final NumberFormatException e1) {
                    // desperate ?
                    return toBoolean(obj) ? 1 : 0;
                }
            }
        }
    }

    /**
     * Translate the passed Object to a double value.
     *
     * @param obj Object
     *
     * @return Resulting value ({@code null} or empty becomes 0)
     */
    public static double toDouble(@Nullable final Object obj) {
        if (obj == null) {
            return 0;

        } else if (obj instanceof Double) {
            return (Double) obj;

        } else if (obj instanceof Float) {
            return ((Float) obj).doubleValue();

        } else {
            String stringValue = obj.toString().trim();
            if (stringValue.isEmpty()) {
                return 0;
            } else {
                try {
                    return parseDouble(stringValue);
                } catch (@NonNull final NumberFormatException e1) {
                    // desperate ?
                    return toBoolean(obj) ? 1 : 0;
                }
            }
        }
    }

    /**
     * Translate the passed Object to a float value.
     *
     * @param obj Object
     *
     * @return Resulting value, {@code null} or empty string become 0f.
     *
     * @throws NumberFormatException if the Object was not float compatible.
     */
    public static float toFloat(@Nullable final Object obj)
            throws NumberFormatException {
        if (obj == null) {
            return 0f;

        } else if (obj instanceof Float) {
            return (float) obj;

        } else if (obj instanceof Double) {
            return ((Double) obj).floatValue();

        } else {
            String stringValue = obj.toString().trim();
            if (stringValue.isEmpty()) {
                return 0f;
            }
            try {
                return parseFloat(stringValue);
            } catch (@NonNull final NumberFormatException e1) {
                // desperate ?
                return toBoolean(obj) ? 1 : 0;
            }
        }
    }

    /**
     * Translate the passed Object to a boolean value.
     *
     * @param obj Object
     *
     * @return Resulting value, {@code null} or empty string become {@code false}.
     *
     * @throws NumberFormatException if the Object was not boolean compatible.
     */
    public static boolean toBoolean(@Nullable final Object obj)
            throws NumberFormatException {
        if (obj == null) {
            return false;
        } else if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else if (obj instanceof Integer) {
            return (Integer) obj != 0;
        } else if (obj instanceof Long) {
            return (Long) obj != 0;
        }
        // lets see if its a String then
        return toBoolean(obj.toString(), true);
    }

    /**
     * Translate the passed String to a boolean value.
     * <p>
     * If 'emptyIsFalse' is set to {@code true};
     * we return {@code false} if the string was {@code null} or empty.
     * If set to {@code false}, we throw a NumberFormatException exception.
     *
     * @param source       String to convert
     * @param emptyIsFalse how to handle a {@code null} or empty string
     *
     * @return boolean value
     *
     * @throws NumberFormatException if the string does not contain a valid boolean.
     */
    public static boolean toBoolean(@Nullable final String source,
                                    final boolean emptyIsFalse)
            throws NumberFormatException {

        if (source == null || source.trim().isEmpty()) {
            if (emptyIsFalse) {
                return false;
            } else {
                throw new NumberFormatException(ERROR_INVALID_BOOLEAN_S + source + '`');
            }
        } else {
            String stringValue = source.trim().toLowerCase(Locale.ENGLISH);
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
                        throw new NumberFormatException(ERROR_INVALID_BOOLEAN_S + source + '`');
                    }
            }
        }
    }

    /**
     * Replacement for {@code Float.parseFloat(String)} that tries several Locales.
     * <ul>
     * <li>Locale.getDefault()</li>
     * <li>Device Locale</li>
     * <li>Locale.US</li>
     * </ul>
     *
     * @param source string to parse
     *
     * @return float
     *
     * @throws NumberFormatException on failure
     */
    public static float parseFloat(@Nullable final String source)
            throws NumberFormatException {
        if (source == null || source.trim().isEmpty()) {
            return 0f;
        }

        try {
            return parseFloat(Locale.getDefault(), source);
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }

        try {
            return parseFloat(App.getSystemLocale(), source);
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }

        return parseFloat(Locale.US, source);
    }

    static float parseFloat(@NonNull final Locale locale,
                            @NonNull final String source)
            throws NumberFormatException {
        try {
            NumberFormat nf = NumberFormat.getInstance(locale);
            return nf.parse(source).floatValue();
        } catch (@NonNull final ParseException ignore) {
            // ignore
        }

        throw new NumberFormatException("not a float: " + source);
    }

    /**
     * Replacement for {@code Double.parseDouble(String)} that tries several Locales.
     * <ul>
     * <li>Locale.getDefault()</li>
     * <li>Device Locale</li>
     * <li>Locale.US</li>
     * </ul>
     *
     * @param source string to parse
     *
     * @return double
     *
     * @throws NumberFormatException on failure
     */
    public static double parseDouble(@Nullable final String source)
            throws NumberFormatException {
        if (source == null || source.trim().isEmpty()) {
            return 0d;
        }

        try {
            return parseDouble(Locale.getDefault(), source);
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }

        try {
            return parseDouble(App.getSystemLocale(), source);
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }

        return parseDouble(Locale.US, source);
    }

    public static double parseDouble(@NonNull final Locale locale,
                                     @NonNull final String s)
            throws NumberFormatException {
        try {
            NumberFormat nf = NumberFormat.getInstance(locale);
            return nf.parse(s).doubleValue();
        } catch (@NonNull final ParseException ignore) {
        }

        throw new NumberFormatException("not a double: " + s);
    }
}
