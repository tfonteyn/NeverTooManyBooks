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

/**
 * FieldFormatter for 'Number' fields with the value being a 'long'.
 *
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong></li>
 *      <li>Extract: <strong>View</strong></li>
 * </ul>
 */
public class LongNumberFormatter
        implements EditFieldFormatter<Number> {

    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final Number rawValue) {

        if (rawValue == null) {
            return "";
        }

        final long value = rawValue.longValue();
        if (value == 0) {
            return "";
        }

        return String.valueOf(value);
    }

    @NonNull
    @Override
    public Number extract(@NonNull final TextView view) {
        final String sv = view.getText().toString().trim();
        if (sv.isEmpty()) {
            return 0;
        }

        try {
            return Long.parseLong(sv);

        } catch (@NonNull final NumberFormatException e) {
            // this should never happen... flw
            return 0;
        }
    }
}
