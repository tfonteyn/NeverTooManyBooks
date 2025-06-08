/*
 * @Copyright 2018-2025 HardBackNutter
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

import java.text.DecimalFormat;
import java.util.Currency;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * FieldFormatter for {@link Money} fields.
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong> but sharing the same Locale.</li>
 * </ul>
 * <p>
 * English: "[currency] [value]" with 2 decimal digits.
 * Most other languages: "[value] [currency]" with 2 decimal digits.
 * <p>
 * This class can display {@link Money} with or without a {@link Currency}.
 * <p>
 * The ICU NumberFormatter Requires Android 11 (API level 30) so we can't use it yet.
 * See <a href="https://github.com/unicode-org/icu/blob/main/icu4j/main/core/src/main/java/com/ibm/icu/number/NumberFormatter.java">icu4</a>
 * and {@code UnitWidth.NARROW}
 */

public class MoneyFormatter
        implements FieldFormatter<Money> {
    /** Log tag. */
    private static final String TAG = "MoneyFormatter";
    @NonNull
    private final Locale locale;

    /**
     * Constructor.
     *
     * @param locale to use
     */
    public MoneyFormatter(@NonNull final Locale locale) {
        this.locale = locale;
    }

    @Override
    @NonNull
    public String format(@NonNull final Context context,
                         @Nullable final Money rawValue) {

        if (rawValue == null || rawValue.isZero()) {
            return "";
        }

        final Currency currency = rawValue.getCurrency();
        if (currency == null) {
            return context.getString(R.string.unknown_currency,
                                     rawValue.getValue().doubleValue());
        }

        try {
            final DecimalFormat nf = (DecimalFormat) DecimalFormat.getCurrencyInstance(locale);
            nf.setCurrency(currency);
            return nf.format(rawValue.getValue());

        } catch (@NonNull final IllegalArgumentException e) {
            if (BuildConfig.DEBUG /* always */) {
                LoggerFactory.getLogger()
                             .e(TAG, e, "currency=" + currency.getCurrencyCode()
                                        + "|value=" + rawValue.getValue());
            }

            return context.getString(R.string.fallback_currency_format,
                                     currency.getCurrencyCode(),
                                     rawValue.getValue().doubleValue());
        }
    }
}
