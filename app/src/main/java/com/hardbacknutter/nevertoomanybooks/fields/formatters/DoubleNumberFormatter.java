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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * FieldFormatter for 'Number' fields with the value being a 'double'
 * This includes the {@link Money} class.
 *
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong></li>
 *      <li>Extract: <strong>View</strong></li>
 * </ul>
 */
public class DoubleNumberFormatter
        implements EditFieldFormatter<Number> {

    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final Number rawValue) {

        if (rawValue == null) {
            return "";
        }

        final double value = rawValue.doubleValue();
        if (value == 0.0d) {
            return "";
        }

        final String formatted = String.valueOf(value);
        if (formatted.endsWith(".0")) {
            return formatted.substring(0, formatted.length() - 2);
        } else {
            return formatted;
        }
    }

    @NonNull
    @Override
    public Number extract(@NonNull final TextView view) {
        final String sv = view.getText().toString().trim();
        if (sv.isEmpty()) {
            return 0;
        }

        try {
            // getSystemLocale: the user types it in
            return ParseUtils.parseDouble(sv, LocaleUtils.getSystemLocale());
        } catch (@NonNull final NumberFormatException e) {
            // this should never happen... flw
            return 0;
        }
    }
}
