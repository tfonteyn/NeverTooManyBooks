/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.fields.formatters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * FieldFormatter for 'page' fields. If the value is numerical, output "x pages"
 * Otherwise outputs the original source value.
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong></li>
 * </ul>
 */
public class PagesFormatter
        implements FieldFormatter<String> {

    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final String rawValue) {

        if (rawValue == null || rawValue.isEmpty()
            || "0".equals(rawValue) || "0.0".equals(rawValue)) {
            return "";

        } else {
            try {
                return context.getString(R.string.txt_x_pages, Integer.parseInt(rawValue));

            } catch (@NonNull final NumberFormatException ignore) {
                // don't log, stored pages was alphanumeric.
                return rawValue;
            }
        }
    }
}
