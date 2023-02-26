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

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;

public class RealNumberParser
        extends NumberParser
        implements Parcelable {

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
    private static final String ERROR_NOT_A_FLOAT = "Not a float: ";
    private static final String ERROR_NOT_A_DOUBLE = "Not a double: ";
    @NonNull
    private final List<Locale> locales;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public RealNumberParser(@NonNull final Context context) {
        this.locales = LocaleListUtils.asList(context);
    }

    /**
     * Constructor.
     *
     * @param locales to use for the formatter/parser.
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

        final ArrayList<String> list = locales.stream().map(Locale::toLanguageTag)
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

        } else {
            final String stringValue = source.toString().trim();
            if (isZero(stringValue)) {
                return 0f;
            }
            try {
                return parseFloat(stringValue);
            } catch (@NonNull final NumberFormatException e) {
                // as a last resort try boolean
                return toBoolean(source) ? 1 : 0;
            }
        }
    }

    /**
     * Replacement for {@code Float.parseFloat(String)} using Locales.
     *
     * @param source String to parse
     *
     * @return Resulting value ({@code null} or empty becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public float parseFloat(@Nullable final String source)
            throws NumberFormatException {

        if (isZero(source)) {
            return 0f;
        }

        // Sanity check
        if (locales.isEmpty()) {
            return Float.parseFloat(source);
        }

        // Create a NEW list, and add Locale.US to use for
        // '.' as decimal and ',' as thousands separator.
        final List<Locale> allLocales = new ArrayList<>(locales);
        allLocales.add(Locale.US);

        // we check in order - first match returns.
        for (final Locale locale : allLocales) {
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
     * Translate the passed Object to a {@code double} value.
     *
     * @param source Object to convert
     *
     * @return Resulting value ({@code null} or empty string becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public double toDouble(@Nullable final Object source)
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
                    return parseDouble(stringValue);
                } catch (@NonNull final NumberFormatException e) {
                    // as a last resort try boolean
                    return toBoolean(source) ? 1 : 0;
                }
            }
        }
    }

    /**
     * Replacement for {@code Double.parseDouble(String)} using Locales.
     *
     * @param source String to parse
     *
     * @return Resulting value ({@code null} or empty becomes 0)
     *
     * @throws NumberFormatException if the source was not compatible.
     */
    public double parseDouble(@Nullable final String source)
            throws NumberFormatException {

        if (isZero(source)) {
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

        // Create a NEW list, and add Locale.US to use for
        // '.' as decimal and ',' as thousands separator.
        final List<Locale> allLocales = new ArrayList<>(locales);
        allLocales.add(Locale.US);

        // we check in order - first match returns.
        for (final Locale locale : allLocales) {
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
