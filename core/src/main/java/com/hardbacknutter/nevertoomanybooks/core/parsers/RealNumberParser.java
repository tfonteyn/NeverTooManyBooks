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

package com.hardbacknutter.nevertoomanybooks.core.parsers;

import android.os.Build;

import androidx.annotation.Discouraged;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;

/**
 * Tested with Device running in US Locale, app in Dutch.
 * A price field with content "10.45".
 * The inputType field on the screen was set to "numberDecimal"
 * the keypad does NOT allow the use of ',' as used in Dutch for the decimal separator.
 * Using the Dutch Locale, parsing returns "1045" as the '.' is seen as the thousands' separator.
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
public class RealNumberParser {

    private static final String ERROR_NOT_A_FLOAT = "Not a float or no suitable Locale: ";
    private static final String ERROR_NOT_A_DOUBLE = "Not a double or no suitable Locale: ";
    private static final String ERROR_NOT_A_BIGDECIMAL = "Not a BigDecimal or no suitable Locale: ";
    @NonNull
    private final List<Locale> locales;
    /**
     * When set, the parser will use the monetary separaters instead of the standard number's.
     * As far as we found out this MAY make a difference for:
     * <p>
     * Decimal separator:
     * <ul>
     *     <li>Switzerland (fr_CH, de_CH): numbers {@code ,} but money {@code .}</li>
     *     <li>Estonia (et_EE): numbers {@code ,} but money {@code .}</li>
     *     <li>Sweden (sv_SE): numbers {@code ,} but money MAY use {@code ,} or {@code :}</li>
     * </ul>
     * Grouping separator:
     * <ul>
     *     <li>Switzerland (it_CH): numbers {@code space} but money {@code '}</li>
     *     <li>Norway (nb_NO): numbers {@code space} but money {@code .}</li>
     *     <li>Portugal (pt_PT): numbers {@code space} but money {@code .}</li>
     * </ul>
     * <p>
     * This is mostly theoretical, as we currently (2026-02) do not process
     * any websites from those country/currencies.
     * (The Portuguese amazon site is actually hosted on the Spanish one;
     * and Amazon being American does not give a hoot anyhow.)
     */
    private final boolean isMoney;

    /**
     * Constructor.
     *
     * @param locales to use for parsing.
     */
    public RealNumberParser(@NonNull final List<Locale> locales) {
        this.locales = locales;
        this.isMoney = false;
    }

    /**
     * Private constructor.
     *
     * @param locales to use for parsing.
     * @param isMoney flag
     */
    private RealNumberParser(@NonNull final List<Locale> locales,
                             final boolean isMoney) {
        this.locales = locales;
        this.isMoney = isMoney;
    }

    /**
     * Constructor: create a parser for numbers; NOT for Money strings.
     *
     * @param locales to use for parsing.
     *
     * @return instance
     */
    @NonNull
    public static RealNumberParser numbers(@NonNull final List<Locale> locales) {
        return new RealNumberParser(locales, false);
    }

    /**
     * Constructor: create a parser for Money; NOT for number strings.
     *
     * @param locales to use for parsing.
     *
     * @return instance
     */
    @NonNull
    public static RealNumberParser money(@NonNull final List<Locale> locales) {
        return new RealNumberParser(locales, true);
    }

    /**
     * Replace/strip a number of special character  which can appear as
     * thousands-separator and decimal-separator to make further parsing easier.
     * <p>
     * Dev. note: This is likely overkill...
     *
     * @param s to normalise
     *
     * @return normalised string
     */
    @SuppressWarnings({"UnnecessaryUnicodeEscape", "CharacterComparison"})
    @NonNull
    private static String normalizeNumericString(@NonNull final String s) {

        final String text = s.strip();
        if (text.isBlank()) {
            return text;
        }
        final int len = text.length();

        if (!needsNormalization(text, len)) {
            return text;
        }

        final StringBuilder sb = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);

            // Map Non-ASCII Minus Signs to standard ASCII '-'
            switch (c) {
                case '\u2212':
                    // MINUS SIGN (−)
                case '\u2013':
                    // EN DASH (–)
                case '\u2014':
                    // EM DASH (—)
                case '\uFE63':
                    // SMALL HYPHEN-MINUS (﹣)
                case '\uFF0D':
                    // FULLWIDTH HYPHEN-MINUS (－)
                    sb.append('-');
                    continue;

                default:
                    break;
            }

            // Map Full-width Japanese/Chinese ０-９ to 0-9
            if (c >= '\uFF10' && c <= '\uFF19') {
                sb.append((char) ('0' + (c - '\uFF10')));
                continue;
            }

            // Normalize spaces, quote variants, and Arabic decimal separator
            final char normalizedChar;
            if (c == '\u00A0' || c == '\u2009' || c == '\u202F') {
                // Special space characters
                normalizedChar = ' ';
            } else if (c == '’') {
                // fancy Swiss
                normalizedChar = '\'';
            } else if (c == '٫') {
                // Arabic
                normalizedChar = '.';
            } else {
                normalizedChar = c;
            }

            // Strip these grouping separator ONLY if strictly between two digits
            // French
            if (normalizedChar == ' '
                // Swiss
                || normalizedChar == '\''
                // Arabic
                || normalizedChar == '٬'
                // computer generated
                || normalizedChar == '_') {

                if (i > 0 && i < len - 1
                    && Character.isDigit(text.charAt(i - 1))
                    && Character.isDigit(text.charAt(i + 1))) {
                    // Skip the grouping separator
                    continue;
                }
            }

            // Keep character as-is
            sb.append(normalizedChar);
        }

        return sb.toString();
    }

    /**
     * Check for being pure asci digits and {@code .} or {@code ,}.
     *
     * @param text to check
     * @param len  length of text
     *
     * @return flag
     */
    @SuppressWarnings("CharacterComparison")
    private static boolean needsNormalization(@NonNull final CharSequence text,
                                              final int len) {
        for (int i = 0; i < len; i++) {
            final char c = text.charAt(i);
            //
            if ((c < '0' || c > '9') && c != '.' && c != '-') {
                return true;
            }
        }
        return false;
    }

    /**
     * Replacement for {@code Float.parseFloat(String)} using {@code List<Locales>}.
     *
     * @param source String to parse
     *
     * @return Resulting value ({@code null} or empty String becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public float parseFloat(@Nullable final String source)
            throws NumberFormatException {

        if (NumberParser.isZero(source)) {
            return 0f;
        }

        final String s = normalizeNumericString(source);

        // Sanity check
        if (locales.isEmpty()) {
            return Float.parseFloat(s);
        }

        // no decimal part and no thousands sep ?
        if (s.indexOf('.') == -1 && s.indexOf(',') == -1) {
            return Float.parseFloat(s);
        }

        final Number number = getNumber(s, false);
        if (number != null) {
            return number.floatValue();
        }
        throw new NumberFormatException(ERROR_NOT_A_FLOAT + s + ", locales=" + locales);
    }

    /**
     * Translate the passed Object to a {@code float} value.
     * <p>
     * This is a wrapper around {@link #parseFloat(String)} which will check
     * for the given source to be convertible to a {@code float} before parsing as a {@code String}.
     *
     * @param source Object to convert
     *
     * @return Resulting value; {@code null} or empty string becomes 0
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public float toFloat(@Nullable final Object source)
            throws NumberFormatException {

        if (source == null) {
            return 0f;

        } else if (source instanceof Number) {
            return ((Number) source).floatValue();
        }

        final String stringValue = source.toString().strip();
        try {
            return parseFloat(stringValue);
        } catch (@NonNull final NumberFormatException e) {
            // as a last resort try boolean
            // This is a safeguard for importing from CSV
            return BooleanParser.toBoolean(source) ? 1 : 0;
        }
    }

    /**
     * Replacement for {@code Double.parseDouble(String)} using a {@code List<Locales>}.
     *
     * @param source String to parse
     *
     * @return Resulting value ({@code null} or empty String becomes {@code 0})
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    @Discouraged(message = "Don't use unless absolutely needed")
    public double parseDouble(@Nullable final String source)
            throws NumberFormatException {

        if (NumberParser.isZero(source)) {
            return 0d;
        }

        final String s = normalizeNumericString(source);

        // Sanity check
        if (locales.isEmpty()) {
            return Double.parseDouble(s);
        }

        // no decimal part and no thousands sep ?
        if (s.indexOf('.') == -1 && s.indexOf(',') == -1) {
            return Double.parseDouble(s);
        }

        final Number number = getNumber(s, false);
        if (number != null) {
            return number.doubleValue();
        }
        throw new NumberFormatException(ERROR_NOT_A_DOUBLE + s + ", locales=" + locales);
    }

    /**
     * Translate the passed Object to a {@code double} value.
     * <p>
     * This is a wrapper around {@link #parseDouble(String)} which will check
     * for the given source to be convertible to a {@code double}
     * before parsing as a {@code String}.
     *
     * @param source Object to convert
     *
     * @return Resulting value; {@code null} or empty string becomes {@code 0}
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    @Discouraged(message = "Don't use unless absolutely needed")
    public double toDouble(@Nullable final Object source)
            throws NumberFormatException {

        if (source == null) {
            return 0d;

        } else if (source instanceof Number) {
            return ((Number) source).doubleValue();
        }

        final String stringValue = source.toString().strip();
        try {
            return parseDouble(stringValue);
        } catch (@NonNull final NumberFormatException e) {
            // as a last resort try boolean
            // This is a safeguard for importing from CSV
            return BooleanParser.toBoolean(source) ? 1 : 0;
        }
    }

    /**
     * Translate the passed Object to a {@code BigDecimal} value.
     * <p>
     * This is a wrapper around {@link #parseBigDecimal(String)} which will check
     * for the given source to be convertible to a {@code BigDecimal}
     * before parsing as a {@code String}.
     *
     * @param source Object to convert
     *
     * @return Resulting value; {@code null} or empty string becomes {@code BigDecimal.ZERO}
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public BigDecimal toBigDecimal(@Nullable final Object source)
            throws NumberFormatException {

        if (source == null) {
            return BigDecimal.ZERO;
        }

        if (source instanceof BigDecimal) {
            return (BigDecimal) source;
        }

        if (source instanceof Long || source instanceof Integer
            || source instanceof Short || source instanceof Byte) {
            return BigDecimal.valueOf(((Number) source).longValue());
        }

        if (source instanceof BigInteger) {
            return new BigDecimal((BigInteger) source);
        }

        if (source instanceof Float || source instanceof Double) {
            return new BigDecimal(source.toString());
        }

        final String stringValue = source.toString().strip();
        try {
            return parseBigDecimal(stringValue);
        } catch (@NonNull final NumberFormatException e) {
            // as a last resort try boolean
            // This is a safeguard for importing from CSV
            return BooleanParser.toBoolean(source) ? BigDecimal.ONE : BigDecimal.ZERO;
        }
    }

    /**
     * Parse BigDecimals without the need to use {@code double} as an interim value.
     *
     * @param source String to parse
     *
     * @return Resulting value ({@code null} or empty String becomes {@code 0})
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    @NonNull
    public BigDecimal parseBigDecimal(@Nullable final String source)
            throws NumberFormatException {

        if (NumberParser.isZero(source)) {
            return BigDecimal.ZERO;
        }

        final String s = normalizeNumericString(source);

        // Sanity check
        if (locales.isEmpty()) {
            return new BigDecimal(s);
        }

        // no decimal part and no thousands sep ?
        if (s.indexOf('.') == -1 && s.indexOf(',') == -1) {
            return new BigDecimal(s);
        }

        final Number number = getNumber(s, true);
        if (number instanceof BigDecimal) {
            return (BigDecimal) number;
        }

        if (number != null) {
            return new BigDecimal(number.toString());
        }

        throw new NumberFormatException(ERROR_NOT_A_BIGDECIMAL + s + ", locales=" + locales);
    }

    /**
     * Parse the given source into a {@code Number}.
     * <p>
     * <strong>Parses text from the beginning of the given string to produce a number.
     * The method may not use the entire text of the given string.</strong>
     * i.o.w. any invalid suffix is simply ignored.
     *
     * @param source          to parse
     * @param parseBigDecimal flag to enable BigDecimal parsing (or not)
     *
     * @return number
     */
    @Nullable
    private Number getNumber(@NonNull final String source,
                             final boolean parseBigDecimal) {
        // we check in order - first match returns.
        for (final Locale locale : locales) {
            //noinspection ProhibitedExceptionCaught
            try {
                final DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(locale);
                if (parseBigDecimal) {
                    nf.setParseBigDecimal(true);
                }

                // if the dec sep for this format is present in the source,
                // decode with this Locale; otherwise skip to the next one
                final DecimalFormatSymbols decimalFormatSymbols = nf.getDecimalFormatSymbols();
                final char decSep;
                final char grpSep;

                if (isMoney) {
                    decSep = decimalFormatSymbols.getMonetaryDecimalSeparator();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        grpSep = decimalFormatSymbols.getMonetaryGroupingSeparator();
                    } else {
                        grpSep = decimalFormatSymbols.getGroupingSeparator();
                    }
                } else {
                    decSep = decimalFormatSymbols.getDecimalSeparator();
                    grpSep = decimalFormatSymbols.getGroupingSeparator();
                }

                final String tmp;
                if (grpSep != '.' && grpSep != ',') {
                    // replace the opposite of the decimal separator with the real group separator
                    // to make the parser work
                    final char remove = decSep == '.' ? ',' : '.';
                    tmp = source.replace(remove, grpSep);
                } else {
                    tmp = source;
                }

                final int lastGrpSep = tmp.lastIndexOf(grpSep);
                final int lastDecSep = tmp.lastIndexOf(decSep);

                if (lastGrpSep == -1 && lastDecSep == -1) {
                    final Number number = nf.parse(tmp);
                    if (number != null) {
                        return number;
                    }

                } else if (lastGrpSep == -1) {
                    // no group separator, but has a decimal separator
                    // We're going to ASSUME this is a match (or else it's an int > 1000)
                    final Number number = nf.parse(tmp);
                    if (number != null) {
                        return number;
                    }

                } else if (lastDecSep == -1) {
                    // no decimal separator, but with group separator.
                    // We're going to ASSUME this is NOT a match and skip to the next Locale

                } else {
                    // both
                    if (lastDecSep > lastGrpSep) {
                        // it's a match
                        final Number number = nf.parse(tmp);
                        if (number != null) {
                            return number;
                        }
                    } else {
                        // the opposite of what we expect
                        // We're going to ASSUME this is NOT a match and skip to the next Locale
                    }
                }
            } catch (@NonNull final ParseException | IndexOutOfBoundsException ignore) {
                // ignore
            }
        }
        return null;
    }
}
