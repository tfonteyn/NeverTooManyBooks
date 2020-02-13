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
package com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * FieldFormatter for 'date' fields.
 * <ul>
 * <li>Multiple fields: <strong>yes</strong></li>
 * <li>Extract: <strong>View</strong></li>
 * </ul>
 */
public class DateFieldFormatter
        implements EditFieldFormatter<String> {

    /**
     * Display as a human-friendly date, local timezone.
     */
    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        } else {
            Locale locale = LocaleUtils.getUserLocale(context);
            return DateUtils.toPrettyDate(locale, rawValue);
        }
    }

    /**
     * Extract as an SQL date, UTC timezone.
     */
    @Override
    @NonNull
    public String extract(@NonNull final TextView view) {
        String text = view.getText().toString().trim();
        Date d = DateUtils.parseDate(text);
        if (d != null) {
            return DateUtils.utcSqlDate(d);
        }
        return text;
    }
}
