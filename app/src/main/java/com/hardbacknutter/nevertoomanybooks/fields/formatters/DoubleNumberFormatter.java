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
package com.hardbacknutter.nevertoomanybooks.fields.formatters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * FieldFormatter for 'Number' fields with the value being a 'double'
 * This includes the {@link Money} class when editing.
 *
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong></li>
 * </ul>
 */
public class DoubleNumberFormatter
        implements EditFieldFormatter<Number> {

    @Override
    @NonNull
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
        if (formatted.endsWith(".00")) {
            return formatted.substring(0, formatted.length() - 3);
        } else if (formatted.endsWith(".0")) {
            return formatted.substring(0, formatted.length() - 2);
        } else {
            return formatted;
        }
    }

    @Override
    @NonNull
    public Number extract(@NonNull final Context context,
                          @NonNull final String text) {
        if (text.isEmpty()) {
            return 0;
        }

        try {
            // System Locale: the user types it in on the SYSTEM keypad using a , or .
            return ParseUtils.parseDouble(ServiceLocator.getSystemLocaleList(), text);

        } catch (@NonNull final NumberFormatException e) {
            // this should never happen... flw
            return 0;
        }
    }
}
