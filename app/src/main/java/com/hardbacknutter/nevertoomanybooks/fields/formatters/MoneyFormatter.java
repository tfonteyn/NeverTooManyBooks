/*
 * @Copyright 2020 HardBackNutter
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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.util.Currency;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

/**
 * FieldFormatter for {@link Money} fields.
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong></li>
 * </ul>
 * <p>
 * English: "[currency] [value]" with 2 decimal digits.
 * Most other languages: "[value] [currency]" with 2 decimal digits.
 */

public class MoneyFormatter
        implements FieldFormatter<Money> {

    /** Log tag. */
    private static final String TAG = "MoneyFormatter";
    @NonNull
    private final Locale mLocale;

    /**
     * Constructor.
     *
     * @param locale to use
     */
    public MoneyFormatter(@NonNull final Locale locale) {
        mLocale = locale;
    }

    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final Money rawValue) {

        if (rawValue == null || rawValue.doubleValue() == 0.0d) {
            return "";
        }

        String currency = rawValue.getCurrency();
        // no currency ? just display the source value as-is
        if (currency == null || currency.isEmpty()) {
            return String.valueOf(rawValue.doubleValue());
        }

        try {
            DecimalFormat nf = (DecimalFormat) DecimalFormat.getCurrencyInstance(mLocale);
            nf.setCurrency(Currency.getInstance(currency));

            // the result is rather dire... most currency symbols are shown as 3-char codes
            // e.g. 'EUR','US$',...
            return nf.format(rawValue.doubleValue());

        } catch (@NonNull final IllegalArgumentException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "currency=" + rawValue.getCurrency()
                           + "|value=" + rawValue.doubleValue(), e);
            }

            return context.getString(R.string.fallback_currency_format,
                                     rawValue.getCurrency(),
                                     rawValue.doubleValue());
        }
    }

    // The ICU NumberFormatter is only available from ICU level 60, but Android lags behind:
    // https://developer.android.com/guide/topics/resources/internationalization
    // #versioning-nougat
    // So you need Android 9 (API level 28) and even then, the NumberFormatter
    // is not available in android.icu so you still would need to bundle the full ICU lib
    // For now, this is to much overkill.
    //
    //   https://github.com/unicode-org/icu/blob/master/icu4j/main/classes/core/src/
    //   com/ibm/icu/number/NumberFormatter.java
    //   and UnitWidth.NARROW
}
