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
package com.hardbacknutter.nevertoomanybooks.fields.formatters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;

/**
 * FieldFormatter for 'Number' fields with the value being a {@code BigDecimal}.
 * This includes the {@link Money} class when editing.
 *
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong> but sharing the same Locale.</li>
 * </ul>
 */
public class BigDecimalFormatter
        implements EditFieldFormatter<Number> {

    @NonNull
    private final RealNumberParser realNumberParser;

    /**
     * Constructor.
     *
     * @param realNumberParser to use
     */
    public BigDecimalFormatter(@NonNull final RealNumberParser realNumberParser) {
        this.realNumberParser = realNumberParser;
    }

    @Override
    @NonNull
    public String format(@NonNull final Context context,
                         @Nullable final Number rawValue) {

        if (rawValue == null) {
            return "";
        }

        final BigDecimal value = realNumberParser.toBigDecimal(rawValue);
        if (BigDecimal.ZERO.compareTo(value) == 0) {
            return "";
        }

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        final DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(userLocale);
        return nf.format(value);
    }

    @Override
    @NonNull
    public Number extract(@NonNull final Context context,
                          @NonNull final String text) {
        if (text.isBlank()) {
            return 0;
        }

        try {
            return realNumberParser.toBigDecimal(text);

        } catch (@NonNull final NumberFormatException e) {
            // this should never happen... flw
            return 0;
        }
    }
}
