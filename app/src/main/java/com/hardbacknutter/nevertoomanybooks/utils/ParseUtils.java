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

import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;

/**
 * {@link #parseFloat} / {@link #parseDouble}.
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
 * <a href="https://issuetracker.google.com/issues/36907764">
 * https://issuetracker.google.com/issues/36907764</a>
 * <a href="https://issuetracker.google.com/issues/37015783">
 * https://issuetracker.google.com/issues/37015783</a>
 *
 * <a href="https://stackoverflow.com/questions/3821539/decimal-separator-comma-with-numberdecimal-inputtype-in-edittext">
 * https://stackoverflow.com/questions/3821539/decimal-separator-comma-with-numberdecimal-inputtype-in-edittext</a>
 * <p>
 * But I test with Android 8.0 ... Americans just can't see beyond their border...
 * To be clear: parsing works fine; it's just the user not able to input the
 * right decimal/thousand separators for their Locale.
 */
public final class ParseUtils {

    /** log error string. */
    private static final String ERROR_INVALID_BOOLEAN_S = "Invalid boolean, s=`";

    private ParseUtils() {
    }

    /**
     * Encode a string by 'escaping' all instances of:
     * <ul>
     * <li>any '\', \'r', '\n', '\t'</li>
     * <li>any additional 'escapeChars'</li>
     * </ul>
     * The escape char is '\'.
     *
     * @param source      String to encode
     * @param escapeChars additional characters to escape. Case sensitive.
     *
     * @return encoded string
     */
    @SuppressWarnings("WeakerAccess")
    public static String escape(@NonNull final CharSequence source,
                                final char... escapeChars) {

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
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
                    for (char e : escapeChars) {
                        if (c == e) {
                            sb.append('\\');
                            // break from the for (char e : escapeChars)
                            break;
                        }

                    }
                    sb.append(c);
                    break;
            }
        }
        return sb.toString().trim();
    }

    /**
     * Decode a string by removing any escapes.
     *
     * @param source String to decode
     *
     * @return decoded string
     */
    public static String unEscape(@Nullable final String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }

        boolean inEsc = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (inEsc) {
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
                inEsc = false;
            } else {
                if (c == '\\') {
                    inEsc = true;
                } else {
                    // keep building the element string
                    sb.append(c);
                }
            }
        }
        return sb.toString().trim();
    }


    /**
     * Translate the passed Object to a Long value.
     *
     * @param source Object
     *
     * @return Resulting value ({@code null} or empty becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public static long toLong(@Nullable final Object source)
            throws NumberFormatException {
        if (source == null) {
            return 0;

        } else if (source instanceof Long) {
            return (long) source;

        } else if (source instanceof Integer) {
            return ((Integer) source).longValue();

        } else {
            String stringValue = source.toString().trim();
            if (stringValue.isEmpty()) {
                return 0;
            } else {
                try {
                    return Long.parseLong(stringValue);
                } catch (@NonNull final NumberFormatException e) {
                    // desperate ?
                    return toBoolean(source) ? 1 : 0;
                }
            }
        }
    }


    /**
     * Translate the passed Object to a boolean value.
     *
     * @param source Object
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
        } else if (source instanceof Integer) {
            return (int) source != 0;
        } else if (source instanceof Long) {
            return (long) source != 0;
        }
        // lets see if its a String
        return parseBoolean(source.toString(), true);
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
     * @throws NumberFormatException if the source was not compatible.
     */
    public static boolean parseBoolean(@Nullable final String source,
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
     * Translate the passed Object to a float value.
     *
     * @param source       Object
     * @param sourceLocale Locale to use for the formatter/parser.
     *                     Can be {@code null} in which case {@code Locale.getDefault()} is used.
     *
     * @return Resulting value ({@code null} or empty becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public static float toFloat(@Nullable final Object source,
                                @Nullable final Locale sourceLocale)
            throws NumberFormatException {
        if (source == null) {
            return 0f;

        } else if (source instanceof Float) {
            return (float) source;

        } else if (source instanceof Double) {
            return ((Double) source).floatValue();

        } else {
            String stringValue = source.toString().trim();
            if (stringValue.isEmpty()) {
                return 0f;
            }
            try {
                return parseFloat(stringValue, sourceLocale);
            } catch (@NonNull final NumberFormatException e) {
                // desperate ?
                return toBoolean(source) ? 1 : 0;
            }
        }
    }

    /**
     * Replacement for {@code Float.parseFloat(String)} using Locales.
     *
     * @param source       string to parse
     * @param sourceLocale (optional) Locale to use for the formatter/parser.
     *
     * @return Resulting value ({@code null} or empty becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public static float parseFloat(@Nullable final String source,
                                   @Nullable final Locale sourceLocale)
            throws NumberFormatException {

        if (source == null || source.trim().isEmpty()) {
            return 0f;
        }

        if (sourceLocale == null) {
            return Float.parseFloat(source);
        }

        // we check in order - first match returns.
        // US is used for '.' as decimal; ',' as thousands separator.
        Locale[] locales = {sourceLocale, App.getSystemLocale(), Locale.US};

        for (Locale locale : locales) {
            try {
                DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(locale);
//                char decSep = nf.getDecimalFormatSymbols().getDecimalSeparator();

                Number number = nf.parse(source);
                if (number != null) {
                    return number.floatValue();
                }
            } catch (@NonNull final ParseException | IndexOutOfBoundsException ignore) {
                // ignore
            }
        }

        throw new NumberFormatException("not a float: " + source);
    }


    /**
     * Translate the passed Object to a double value.
     *
     * @param source       Object
     * @param sourceLocale Locale to use for the formatter/parser.
     *                     Can be {@code null} in which case {@code Locale.getDefault()} is used.
     *
     * @return Resulting value ({@code null} or empty becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public static double toDouble(@Nullable final Object source,
                                  @Nullable final Locale sourceLocale)
            throws NumberFormatException {
        if (source == null) {
            return 0;

        } else if (source instanceof Double) {
            return (Double) source;

        } else if (source instanceof Float) {
            return ((Float) source).doubleValue();

        } else {
            String stringValue = source.toString().trim();
            if (stringValue.isEmpty()) {
                return 0;
            } else {
                try {
                    return parseDouble(stringValue, sourceLocale);
                } catch (@NonNull final NumberFormatException e) {
                    // desperate ?
                    return toBoolean(source) ? 1 : 0;
                }
            }
        }
    }

    /**
     * Replacement for {@code Double.parseDouble(String)} using Locales.
     *
     * @param source       string to parse
     * @param sourceLocale Locale to use for the formatter/parser.
     *                     Can be {@code null} in which case {@code Locale.getDefault()} is used.
     *
     * @return Resulting value ({@code null} or empty becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public static double parseDouble(@Nullable final String source,
                                     @Nullable final Locale sourceLocale)
            throws NumberFormatException {

        if (source == null || source.trim().isEmpty()) {
            return 0d;
        }

        if (sourceLocale == null) {
            return Double.parseDouble(source);
        }

        // no decimal part and no thousands sep ?
        if (source.indexOf('.') == -1 && source.indexOf(',') == -1) {
            return Double.parseDouble(source);
        }

        // we check in order - first match returns.
        // Locale.US is used for '.' as decimal and ',' as thousands separator.
        Locale[] locales = {sourceLocale, App.getSystemLocale(), Locale.US};

        for (Locale locale : locales) {
            try {
                Number number;
                DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(locale);
                char decSep = nf.getDecimalFormatSymbols().getDecimalSeparator();

                // if the dec sep is present, decode with Locale; otherwise try next Locale
                if (source.indexOf(decSep) != -1) {
                    number = nf.parse(source);
                    if (number != null) {
                        return number.doubleValue();
                    }
                }

            } catch (@NonNull final ParseException ignore) {
                // ignore
            }
        }
        throw new NumberFormatException("not a double: " + source);
    }
}
