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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.hardbacknutter.nevertoomanybooks.dialogs.date.BaseDatePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.EditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.TextAccessor;
import com.hardbacknutter.nevertoomanybooks.utils.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * FieldFormatter for 'date' fields.
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong></li>
 *      <li>Extract: <strong>View</strong></li>
 * </ul>
 * <p>
 * This class can be used in two ways:
 * <ol>
 *     <li>with a {@link TextAccessor}: the value is stored in the accessor,<br>
 *         This is meant to be used with a {@link BaseDatePickerDialogFragment}.</li>
 *     <li>with an {@link EditTextAccessor}: the value will be extracted from the View.<br>
 *         This is meant to be used as a free-entry field (i.e. the user types in the date).<br>
 *         A partial date consisting of Month+Year, will always get a day==1 added.</li>
 * </ol>
 */
public class DateFieldFormatter
        implements EditFieldFormatter<String> {

    /**
     * Display as a human-friendly date, local timezone.
     * <p>
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        } else {
            return LocaleUtils.toPrettyDate(context, rawValue);
        }
    }

    /**
     * Extract as an ISO date.
     */
    @Override
    @NonNull
    public String extract(@NonNull final TextView view) {
        final String text = view.getText().toString().trim();
        // extract a year-only string as-is. As we're using controlled input,
        // this will always be a 4-digit valid year.
        if (text.length() == 4) {
            return text;
        }

        // FIXME:a partial date consisting of Month+Year, will always get a day==1 added.
        final LocalDateTime date = DateParser.ALL.parse(text);
        if (date != null) {
            return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return text;
    }
}
