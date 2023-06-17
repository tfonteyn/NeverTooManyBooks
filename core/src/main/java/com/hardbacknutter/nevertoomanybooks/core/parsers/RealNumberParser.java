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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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
public class RealNumberParser
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<RealNumberParser> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public RealNumberParser createFromParcel(@NonNull final Parcel in) {
            return new RealNumberParser(in);
        }

        @Override
        @NonNull
        public RealNumberParser[] newArray(final int size) {
            return new RealNumberParser[size];
        }
    };

    private static final String ERROR_NOT_A_FLOAT = "Not a float or no suitable Locale: ";
    private static final String ERROR_NOT_A_DOUBLE = "Not a double or no suitable Locale: ";
    @NonNull
    private final List<Locale> locales;

    /**
     * Constructor.
     *
     * @param locales to use for parsing.
     */
    public RealNumberParser(@NonNull final List<Locale> locales) {
        this.locales = locales;
    }

    private RealNumberParser(@NonNull final Parcel in) {
        final List<String> list = new ArrayList<>();
        in.readStringList(list);

        this.locales = list.stream().map(Locale::forLanguageTag).collect(Collectors.toList());
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {

        final ArrayList<String> list = locales.stream()
                                              .map(Locale::toLanguageTag)
                                              .collect(Collectors.toCollection(ArrayList::new));
        dest.writeStringList(list);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Translate the passed Object to a {@code float} value.
     *
     * @param source Object to convert
     *
     * @return Resulting value ({@code null} or empty string becomes 0)
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

        final String stringValue = source.toString().trim();
        try {
            return parseFloat(stringValue);
        } catch (@NonNull final NumberFormatException e) {
            // as a last resort try boolean
            // This is a safeguard for importing BC backups
            return BooleanParser.toBoolean(source) ? 1 : 0;
        }
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

        // Sanity check
        if (locales.isEmpty()) {
            return Float.parseFloat(source);
        }

        // no decimal part and no thousands sep ?
        if (source.indexOf('.') == -1 && source.indexOf(',') == -1) {
            return Float.parseFloat(source);
        }

        final Number number = getNumber(source);
        if (number != null) {
            return number.floatValue();
        }
        throw new NumberFormatException(ERROR_NOT_A_FLOAT + source + ", locales=" + locales);
    }

    /**
     * Translate the passed Object to a {@code double} value.
     *
     * @param source Object to convert
     *
     * @return Resulting value ({@code null} or empty string becomes {@code 0})
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public double toDouble(@Nullable final Object source)
            throws NumberFormatException {

        if (source == null) {
            return 0d;

        } else if (source instanceof Number) {
            return ((Number) source).doubleValue();
        }

        final String stringValue = source.toString().trim();
        try {
            return parseDouble(stringValue);
        } catch (@NonNull final NumberFormatException e) {
            // as a last resort try boolean
            // This is a safeguard for importing BC backups
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
    public double parseDouble(@Nullable final String source)
            throws NumberFormatException {

        if (NumberParser.isZero(source)) {
            return 0d;
        }

        // Sanity check
        if (locales.isEmpty()) {
            return Double.parseDouble(source);
        }

        // no decimal part and no thousands sep ?
        if (source.indexOf('.') == -1 && source.indexOf(',') == -1) {
            return Double.parseDouble(source);
        }

        final Number number = getNumber(source);
        if (number != null) {
            return number.doubleValue();
        }
        throw new NumberFormatException(ERROR_NOT_A_DOUBLE + source + ", locales=" + locales);
    }


    @Nullable
    private Number getNumber(@NonNull final String source) {
        // we check in order - first match returns.
        for (final Locale locale : locales) {
            try {
                final DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(locale);
                // if the dec sep for this format is present in the source,
                // decode with this Locale; otherwise skip to the next one
                final DecimalFormatSymbols decimalFormatSymbols = nf.getDecimalFormatSymbols();
                final char decSep = decimalFormatSymbols.getDecimalSeparator();
                final char grpSep = decimalFormatSymbols.getGroupingSeparator();

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
